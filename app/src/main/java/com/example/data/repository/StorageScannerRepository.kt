package com.example.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.example.data.local.HyperCleanDao
import com.example.data.local.LinkedFolderEntity
import com.example.domain.model.FileCategory
import com.example.domain.model.ScannedFileItem
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * High-speed sequential/positional reader that keeps the underlying OS file descriptor
 * or buffered stream open across 1 KB -> 4 KB -> 4 KB steps so the file is NEVER
 * re-opened or re-skipped from byte 0.
 */
interface FastSequentialFileReader : Closeable {
    fun readNextBlock(targetBuffer: ByteArray, offsetInFile: Long, length: Int): Int
}

class StorageScannerRepository(
    private val context: Context,
    private val dao: HyperCleanDao
) {

    /**
     * Discovers all accessible real files in parallel across:
     *  1. Indexed MediaStore entries (Images, Videos, Audio, Downloads, Documents)
     *  2. App external media/files directories
     *  3. All user-linked SAF folders (OpenDocumentTree)
     */
    suspend fun discoverAllFiles(): List<ScannedFileItem> = withContext(Dispatchers.IO) {
        val discovered = java.util.concurrent.ConcurrentHashMap<String, ScannedFileItem>(1024)

        coroutineScope {
            val extJob = async {
                context.getExternalFilesDirs(null)?.filterNotNull()?.forEach { extDir ->
                    scanJavaDirectoryRecursive(
                        rootDir = extDir,
                        baseLabelPrefix = "External/",
                        sink = discovered
                    )
                }
            }

            val mediaJob = async {
                scanMediaStoreCollection(discovered)
            }

            val safJob = async {
                val linkedFolders = dao.getLinkedFoldersList()
                linkedFolders.map { folder ->
                    async {
                        try {
                            val treeUri = Uri.parse(folder.uriString)
                            scanSafTreeFast(treeUri, folder.displayName, discovered)
                        } catch (_: Exception) {
                        }
                    }
                }.awaitAll()
            }

            extJob.await()
            mediaJob.await()
            safJob.await()
        }

        discovered.values.toList()
    }

    private fun scanJavaDirectoryRecursive(
        rootDir: File,
        baseLabelPrefix: String,
        sink: MutableMap<String, ScannedFileItem>
    ) {
        val rootPath = rootDir.absolutePath
        rootDir.walkTopDown().forEach { file ->
            if (file.isFile && !file.name.startsWith(".") && file.length() > 0L) {
                val relParent = file.parentFile?.absolutePath
                    ?.removePrefix(rootPath)
                    ?.trimStart('/')
                    .orEmpty()
                val folderLabel = (baseLabelPrefix + relParent).ifBlank { "Storage" }
                val absPath = file.absolutePath
                sink[absPath] = ScannedFileItem(
                    id = absPath,
                    name = file.name,
                    folderPath = folderLabel,
                    fullPathOrUri = absPath,
                    isSafUri = false,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    category = categorizeFileByName(file.name)
                )
            }
        }
    }

    private fun scanMediaStoreCollection(sink: MutableMap<String, ScannedFileItem>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = context.contentResolver
        val collectionUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.RELATIVE_PATH
        )
        val selection = "${MediaStore.Files.FileColumns.SIZE} > 0"

        try {
            resolver.query(collectionUri, projection, selection, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val pathCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    if (size <= 0L) continue
                    val dateSec = cursor.getLong(dateCol)
                    val relPath = if (pathCol >= 0) {
                        cursor.getString(pathCol)?.trimEnd('/') ?: "Memoria Dispositivo"
                    } else {
                        "Memoria Dispositivo"
                    }
                    val contentUri = Uri.withAppendedPath(collectionUri, id.toString()).toString()

                    sink[contentUri] = ScannedFileItem(
                        id = contentUri,
                        name = name,
                        folderPath = relPath,
                        fullPathOrUri = contentUri,
                        isSafUri = true,
                        sizeBytes = size,
                        lastModified = dateSec * 1000L,
                        category = categorizeFileByName(name)
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun scanSafTreeFast(
        treeUri: Uri,
        rootDisplayName: String,
        sink: MutableMap<String, ScannedFileItem>
    ) {
        val resolver = context.contentResolver
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add(rootDocId to rootDisplayName)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        while (queue.isNotEmpty()) {
            val (parentDocId, currentFolderPath) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            var cursor: Cursor? = null
            try {
                cursor = resolver.query(childrenUri, projection, null, null, null)
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(0) ?: continue
                        val displayName = cursor.getString(1) ?: continue
                        val mimeType = cursor.getString(2) ?: ""
                        val size = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                        val lastMod = if (cursor.isNull(4)) System.currentTimeMillis() else cursor.getLong(4)

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (!displayName.startsWith(".")) {
                                queue.add(docId to "$currentFolderPath/$displayName")
                            }
                        } else if (size > 0L && !displayName.startsWith(".")) {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()
                            sink[docUri] = ScannedFileItem(
                                id = docUri,
                                name = displayName,
                                folderPath = currentFolderPath,
                                fullPathOrUri = docUri,
                                isSafUri = true,
                                sizeBytes = size,
                                lastModified = lastMod,
                                category = categorizeFileByName(displayName)
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                cursor?.close()
            }
        }
    }

    /**
     * Opens a zero-overhead positional FileChannel (or persistent BufferedInputStream fallback)
     * so reading 1 KB then consecutive 4 KB chunks never re-opens or re-skips the file!
     */
    fun openFastReader(item: ScannedFileItem): FastSequentialFileReader? {
        return try {
            if (!item.isSafUri) {
                val raf = RandomAccessFile(File(item.fullPathOrUri), "r")
                val channel = raf.channel
                FileChannelFastReader(channel, raf)
            } else {
                val uri = Uri.parse(item.fullPathOrUri)
                val pfd = try {
                    context.contentResolver.openFileDescriptor(uri, "r")
                } catch (_: Exception) {
                    null
                }
                if (pfd != null) {
                    val fis = FileInputStream(pfd.fileDescriptor)
                    val channel = fis.channel
                    FileChannelFastReader(channel, Closeable {
                        try {
                            fis.close()
                        } finally {
                            pfd.close()
                        }
                    })
                } else {
                    val stream = context.contentResolver.openInputStream(uri) ?: return null
                    StreamFastReader(BufferedInputStream(stream, 16 * 1024))
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun openFileInputStream(item: ScannedFileItem): InputStream? {
        return try {
            if (item.isSafUri) {
                context.contentResolver.openInputStream(Uri.parse(item.fullPathOrUri))
            } else {
                FileInputStream(File(item.fullPathOrUri))
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deleteSelectedFiles(itemsToDelete: List<ScannedFileItem>): Pair<Int, Long> =
        withContext(Dispatchers.IO) {
            var deletedCount = 0
            var freedBytes = 0L
            val resolver: ContentResolver = context.contentResolver

            for (item in itemsToDelete) {
                var success = false
                try {
                    if (item.isSafUri) {
                        val uri = Uri.parse(item.fullPathOrUri)
                        success = try {
                            DocumentsContract.deleteDocument(resolver, uri)
                        } catch (_: Exception) {
                            resolver.delete(uri, null, null) > 0
                        }
                    } else {
                        val f = File(item.fullPathOrUri)
                        if (f.exists() && f.delete()) {
                            success = true
                        }
                    }
                } catch (_: Exception) {
                    success = false
                }

                if (success) {
                    deletedCount++
                    freedBytes += item.sizeBytes
                }
            }

            deletedCount to freedBytes
        }

    suspend fun addLinkedSafFolder(treeUri: Uri, displayName: String) {
        dao.insertLinkedFolder(
            LinkedFolderEntity(
                uriString = treeUri.toString(),
                displayName = displayName
            )
        )
    }

    suspend fun removeLinkedSafFolder(uriString: String) {
        dao.removeLinkedFolder(uriString)
    }

    private class FileChannelFastReader(
        private val channel: FileChannel,
        private val owner: Closeable
    ) : FastSequentialFileReader {
        override fun readNextBlock(targetBuffer: ByteArray, offsetInFile: Long, length: Int): Int {
            val byteBuffer = ByteBuffer.wrap(targetBuffer, 0, length)
            var totalRead = 0
            while (totalRead < length) {
                val n = channel.read(byteBuffer, offsetInFile + totalRead)
                if (n <= 0) break
                totalRead += n
            }
            return totalRead
        }

        override fun close() {
            try {
                channel.close()
            } catch (_: Exception) {
            }
            try {
                owner.close()
            } catch (_: Exception) {
            }
        }
    }

    private class StreamFastReader(
        private val stream: InputStream
    ) : FastSequentialFileReader {
        private var currentPos = 0L

        override fun readNextBlock(targetBuffer: ByteArray, offsetInFile: Long, length: Int): Int {
            while (currentPos < offsetInFile) {
                val toSkip = offsetInFile - currentPos
                val skipped = stream.skip(toSkip)
                if (skipped <= 0L) {
                    if (stream.read() == -1) return -1
                    currentPos += 1L
                } else {
                    currentPos += skipped
                }
            }
            var totalRead = 0
            while (totalRead < length) {
                val n = stream.read(targetBuffer, totalRead, length - totalRead)
                if (n == -1) break
                totalRead += n
                currentPos += n
            }
            return totalRead
        }

        override fun close() {
            try {
                stream.close()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        fun categorizeFileByName(fileName: String): FileCategory {
            val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return when (ext) {
                "jpg", "jpeg", "png", "webp", "heic", "gif", "bmp", "raw", "dng" -> FileCategory.IMAGES
                "mp4", "mkv", "mov", "avi", "webm", "3gp" -> FileCategory.VIDEOS
                "mp3", "flac", "wav", "aac", "ogg", "m4a", "opus" -> FileCategory.AUDIO
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv" -> FileCategory.DOCUMENTS
                else -> FileCategory.ARCHIVES
            }
        }
    }
}
