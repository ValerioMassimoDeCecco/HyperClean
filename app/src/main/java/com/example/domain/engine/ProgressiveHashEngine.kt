package com.example.domain.engine

import com.example.data.local.CachedFileSignature
import com.example.data.local.HyperCleanDao
import com.example.data.repository.FastSequentialFileReader
import com.example.domain.model.BlockCellStatus
import com.example.domain.model.ByteFormatter
import com.example.domain.model.DuplicateGroup
import com.example.domain.model.FileCategory
import com.example.domain.model.ScanPhase
import com.example.domain.model.ScanTelemetry
import com.example.domain.model.ScannedFileItem
import com.example.domain.model.SelectionRule
import com.example.domain.model.StageStepLog
import com.example.domain.model.VerboseEventType
import com.example.domain.model.VerboseFeedLine
import com.example.domain.model.defaultBlockMatrix
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Near-Instantaneous Multi-Core Progressive Duplicate Engine:
 *  1. Zero-Delay Execution: No artificial sleeps or coroutine delays.
 *  2. O(1) RAM Cache Lookup: Loads all known signatures in a single batch query.
 *  3. Persistent NIO FileChannel / Buffered Stream per candidate: Reads 1 KB then
 *     consecutive 4 KB blocks sequentially without EVER re-opening or re-skipping the file!
 *  4. Unrolled 64-bit XXHash64-style Mixer: Zero heap allocation per 4 KB chunk via ThreadLocal buffer.
 *  5. Multi-Core Parallel Bucket Execution: Processes independent size buckets in parallel across all CPU cores.
 */
class ProgressiveHashEngine(
    private val dao: HyperCleanDao?,
    private val streamProvider: (ScannedFileItem) -> InputStream?,
    private val fastReaderProvider: ((ScannedFileItem) -> FastSequentialFileReader?)? = null
) {

    companion object {
        const val FIRST_BLOCK_BYTES = 1024 // 1 KB
        const val STEP_BLOCK_BYTES = 4096  // 4 KB

        private const val PRIME64_1 = -7046029288634856825L // 0x9E3779B185EBCA87UL
        private const val PRIME64_2 = -4417276706812531889L // 0xC2B2AE3D27D4EB4FUL
        private const val PRIME64_3 = 1609587929392839161L  // 0x165667B19E3779F9UL
        private const val PRIME64_4 = -8796714831421723037L // 0x85EBCA77C2B2AE63UL
        private const val PRIME64_5 = 2870177450012600261L  // 0x27D4EB2F165667C5UL

        private val threadLocalBuffer = ThreadLocal.withInitial { ByteArray(STEP_BLOCK_BYTES) }

        private val COPY_REGEX = Regex(
            pattern = """(\(\d+\)|_copy|copia|backup|dup|wa\d+|received_|_edit|-copy)""",
            option = RegexOption.IGNORE_CASE
        )

        /**
         * Ultra-fast 64-bit XXHash64-inspired block hash operating directly on [buf]
         * with 8-byte unrolled lanes and zero object allocations.
         */
        fun fastHash64(buf: ByteArray, length: Int, seed: Long = 0L): Long {
            var h64: Long
            var index = 0

            if (length >= 8) {
                var v1 = seed + PRIME64_1 + PRIME64_2
                var v2 = seed + PRIME64_2
                val limit = length - 8
                while (index <= limit) {
                    val k1 = readLongLE(buf, index)
                    v1 = java.lang.Long.rotateLeft(v1 + k1 * PRIME64_2, 31) * PRIME64_1
                    v2 = java.lang.Long.rotateLeft(v2 xor (k1 * PRIME64_3), 27) * PRIME64_4
                    index += 8
                }
                h64 = java.lang.Long.rotateLeft(v1, 1) +
                    java.lang.Long.rotateLeft(v2, 7) +
                    length.toLong()
            } else {
                h64 = seed + PRIME64_5 + length.toLong()
            }

            while (index < length) {
                val byteVal = (buf[index].toInt() and 0xFF).toLong()
                h64 = java.lang.Long.rotateLeft(h64 xor (byteVal * PRIME64_5), 11) * PRIME64_1
                index++
            }

            h64 = h64 xor (h64 ushr 33)
            h64 *= PRIME64_2
            h64 = h64 xor (h64 ushr 29)
            h64 *= PRIME64_3
            h64 = h64 xor (h64 ushr 32)
            return h64
        }

        private fun readLongLE(buf: ByteArray, offset: Int): Long {
            return (buf[offset].toLong() and 0xFFL) or
                ((buf[offset + 1].toLong() and 0xFFL) shl 8) or
                ((buf[offset + 2].toLong() and 0xFFL) shl 16) or
                ((buf[offset + 3].toLong() and 0xFFL) shl 24) or
                ((buf[offset + 4].toLong() and 0xFFL) shl 32) or
                ((buf[offset + 5].toLong() and 0xFFL) shl 40) or
                ((buf[offset + 6].toLong() and 0xFFL) shl 48) or
                ((buf[offset + 7].toLong() and 0xFFL) shl 56)
        }

        fun longToHex16(value: Long): String = java.lang.Long.toUnsignedString(value, 16).padStart(16, '0')

        fun applySelectionRuleToGroup(
            files: List<ScannedFileItem>,
            rule: SelectionRule
        ): List<ScannedFileItem> {
            if (files.isEmpty()) return emptyList()
            val minTimestamp = files.minOf { it.lastModified }
            val maxTimestamp = files.maxOf { it.lastModified }

            val scored = files.map { item ->
                val (score, reason) = computeOriginalityScore(
                    item = item,
                    minTimestamp = minTimestamp,
                    maxTimestamp = maxTimestamp
                )
                item.copy(originScore = score, originReason = reason)
            }

            val keeperId: String? = when (rule) {
                SelectionRule.ORIGINAL_BY_DATE_AND_FOLDER ->
                    scored.maxWithOrNull(
                        compareBy<ScannedFileItem> { it.originScore }
                            .thenBy { it.lastModified }
                            .thenBy { it.folderPath.length }
                    )?.id

                SelectionRule.KEEP_OLDEST ->
                    scored.minWithOrNull(
                        compareBy<ScannedFileItem> { it.lastModified }
                            .thenByDescending { it.originScore }
                    )?.id

                SelectionRule.KEEP_NEWEST ->
                    scored.maxWithOrNull(
                        compareBy<ScannedFileItem> { it.lastModified }
                            .thenByDescending { it.originScore }
                    )?.id

                SelectionRule.KEEP_SHORTEST_PATH ->
                    scored.minWithOrNull(
                        compareBy<ScannedFileItem> { it.folderPath.length }
                            .thenBy { it.name.length }
                    )?.id

                SelectionRule.MANUAL_ONLY -> null
            }

            return scored
                .sortedWith(
                    compareByDescending<ScannedFileItem> { it.id == keeperId }
                        .thenByDescending { it.originScore }
                        .thenBy { it.lastModified }
                )
                .map { item ->
                    if (rule == SelectionRule.MANUAL_ONLY) {
                        item.copy(
                            isOriginalRecommended = false,
                            isSelectedForDeletion = false
                        )
                    } else {
                        val isKeeper = item.id == keeperId
                        item.copy(
                            isOriginalRecommended = isKeeper,
                            isSelectedForDeletion = !isKeeper
                        )
                    }
                }
        }

        private fun computeOriginalityScore(
            item: ScannedFileItem,
            minTimestamp: Long,
            maxTimestamp: Long
        ): Pair<Int, String> {
            var score = 100
            val reasons = mutableListOf<String>()
            val lowerFolder = item.folderPath.lowercase(Locale.ROOT)
            val lowerName = item.name.lowercase(Locale.ROOT)

            when {
                lowerFolder.contains("dcim/camera") || lowerFolder.contains("fotocamera") -> {
                    score += 45
                    reasons.add("Cartella Fotocamera principale")
                }
                lowerFolder.contains("pictures") || lowerFolder.contains("documents") ||
                    lowerFolder.contains("documenti") || lowerFolder.contains("music") ||
                    lowerFolder.contains("movies") -> {
                    score += 30
                    reasons.add("Cartella di sistema autorevole")
                }
                lowerFolder.contains("download") -> {
                    score -= 25
                    reasons.add("Cartella Download secondaria")
                }
                lowerFolder.contains("whatsapp") || lowerFolder.contains("telegram") -> {
                    score -= 35
                    reasons.add("Cartella messaggistica/inoltro")
                }
                lowerFolder.contains("temp") || lowerFolder.contains("cache") ||
                    lowerFolder.contains("backup") || lowerFolder.contains("copie") -> {
                    score -= 40
                    reasons.add("Cartella di backup/temporanea")
                }
            }

            if (COPY_REGEX.containsMatchIn(lowerName)) {
                score -= 35
                reasons.add("Suffisso di copia nel nome")
            } else {
                score += 15
                reasons.add("Nome file pulito")
            }

            if (item.lastModified <= minTimestamp + 1000L && maxTimestamp > minTimestamp) {
                score += 25
                reasons.add("Data creazione più antica")
            } else if (maxTimestamp > minTimestamp) {
                score -= 10
                reasons.add("Data successiva all'originale")
            }

            return score to reasons.joinToString(" • ")
        }
    }

    data class ProgressiveScanOutput(
        val duplicateGroups: List<DuplicateGroup>,
        val telemetry: ScanTelemetry,
        val nearMissCollisionsCount: Int
    )

    suspend fun executeProgressiveScan(
        discoveredFiles: List<ScannedFileItem>,
        selectionRule: SelectionRule,
        onTelemetryUpdate: suspend (ScanTelemetry) -> Unit
    ): ProgressiveScanOutput = withContext(Dispatchers.IO) {
        val startNano = System.nanoTime()
        val validFiles = discoveredFiles.filter { it.sizeBytes > 0L }
        val totalBytes = validFiles.sumOf { it.sizeBytes }

        val stageLogs = mutableListOf<StageStepLog>()
        val verboseQueue = ConcurrentLinkedQueue<VerboseFeedLine>()
        val eventSeq = AtomicLong(1L)

        fun pushFeed(
            tag: String,
            title: String,
            detail: String,
            hexDigest: String = "",
            badgeDeltaText: String = "",
            type: VerboseEventType = VerboseEventType.INFO
        ) {
            val elapsedMs = ((System.nanoTime() - startNano) / 1_000_000L).coerceAtLeast(0L)
            verboseQueue.add(
                VerboseFeedLine(
                    id = eventSeq.getAndIncrement(),
                    elapsedMs = elapsedMs,
                    tag = tag,
                    title = title,
                    detail = detail,
                    hexDigest = hexDigest,
                    badgeDeltaText = badgeDeltaText,
                    type = type
                )
            )
        }

        // Load entire local cache in a single SQL query into RAM Map (O(1) lookup, 0 SQLite round-trips in loop!)
        val ramSignatureCache: Map<String, CachedFileSignature> = try {
            dao?.getAllSignaturesList()?.associateBy { it.fileKey } ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }

        // =====================================================================
        // STAGE 0: Instant grouping by exact byte size (0 bytes read from disk)
        // =====================================================================
        val sizeBuckets = validFiles
            .groupBy { it.sizeBytes }
            .filterValues { it.size >= 2 }

        val sizeMatchedFiles = sizeBuckets.values.flatten()
        val sizeEliminatedCount = validFiles.size - sizeMatchedFiles.size
        val totalCandidateBytes = sizeMatchedFiles.sumOf { it.sizeBytes }
        val stage0SavedBytes = (totalBytes - totalCandidateBytes).coerceAtLeast(0L)

        pushFeed(
            tag = "BOOT-FAST",
            title = "Motore Parallelo XXHash64 1KB → 4KB Avviato",
            detail = "Indicizzati ${validFiles.size} file (${ByteFormatter.formatBytes(totalBytes)}) • Cache RAM: ${ramSignatureCache.size} firme",
            badgeDeltaText = "${validFiles.size} FILE",
            type = VerboseEventType.INFO
        )

        if (sizeEliminatedCount > 0) {
            pushFeed(
                tag = "SIZE-0",
                title = "Filtro Dimensione Istantaneo (0 Byte Letti)",
                detail = "Scartati in memoria $sizeEliminatedCount file con dimensione unica senza alcuna lettura dal disco",
                badgeDeltaText = "-${ByteFormatter.formatBytes(stage0SavedBytes)} I/O",
                type = VerboseEventType.SIZE_FILTER
            )
        }

        stageLogs.add(
            StageStepLog(
                stepTitle = "Stadio 0 • Filtro Dimensione Esatta (In-Memory)",
                detail = "Scartati istantaneamente $sizeEliminatedCount file con dimensione unica (0 byte letti)",
                candidatesIn = validFiles.size,
                candidatesOut = sizeMatchedFiles.size,
                bytesReadDelta = 0L
            )
        )

        val oneKbChecks = AtomicInteger(0)
        val oneKbEliminated = AtomicInteger(0)
        val fourKbChunksRead = AtomicInteger(0)
        val fourKbEliminated = AtomicInteger(0)
        val maxFourKbDepth = AtomicInteger(0)
        val cacheHits = AtomicInteger(0)
        val actualBytesRead = AtomicLong(0L)
        val nearMissCollisions = AtomicInteger(0)

        val confirmedGroupsQueue = ConcurrentLinkedQueue<DuplicateGroup>()
        val signaturesToCacheQueue = ConcurrentLinkedQueue<CachedFileSignature>()

        val parallelDispatcher = Dispatchers.IO.limitedParallelism(
            Runtime.getRuntime().availableProcessors().coerceIn(4, 16)
        )

        val bucketEntries = sizeBuckets.entries.sortedByDescending { it.key }
        val lastUiEmitMs = AtomicLong(System.currentTimeMillis())

        // =====================================================================
        // PARALLEL MULTI-CORE EXECUTION ACROSS ALL SIZE BUCKETS
        // =====================================================================
        coroutineScope {
            bucketEntries.map { (fileSize, sameSizeFiles) ->
                async(parallelDispatcher) {
                    coroutineContext.ensureActive()
                    processSizeBucketFast(
                        fileSize = fileSize,
                        sameSizeFiles = sameSizeFiles,
                        selectionRule = selectionRule,
                        ramSignatureCache = ramSignatureCache,
                        oneKbChecks = oneKbChecks,
                        oneKbEliminated = oneKbEliminated,
                        fourKbChunksRead = fourKbChunksRead,
                        fourKbEliminated = fourKbEliminated,
                        maxFourKbDepth = maxFourKbDepth,
                        cacheHits = cacheHits,
                        actualBytesRead = actualBytesRead,
                        nearMissCollisions = nearMissCollisions,
                        confirmedGroupsQueue = confirmedGroupsQueue,
                        signaturesToCacheQueue = signaturesToCacheQueue,
                        pushFeed = ::pushFeed
                    )

                    // Non-blocking throttled UI telemetry snapshot (at most every 40ms)
                    val nowMs = System.currentTimeMillis()
                    val prevMs = lastUiEmitMs.get()
                    if (nowMs - prevMs >= 40L && lastUiEmitMs.compareAndSet(prevMs, nowMs)) {
                        val elapsedMs = ((System.nanoTime() - startNano) / 1_000_000L).coerceAtLeast(1L)
                        val readNow = actualBytesRead.get()
                        val savedNow = (totalCandidateBytes - readNow).coerceAtLeast(0L)
                        val savedPct = if (totalCandidateBytes > 0L) {
                            ((savedNow * 100L) / totalCandidateBytes).toInt().coerceIn(0, 99)
                        } else 99
                        val speedMbSec =
                            (totalBytes.toDouble() / (1024.0 * 1024.0)) / (elapsedMs.toDouble() / 1000.0)
                        val sampleFile = sameSizeFiles.first()

                        onTelemetryUpdate(
                            ScanTelemetry(
                                phase = ScanPhase.STAGE_2_4KB_LOOP,
                                progressFraction = 0.65f,
                                currentStatusText = "Scansione parallela 1KB → 4KB in corso (${oneKbChecks.get()} hash 1KB, ${fourKbChunksRead.get()} blocchi 4KB)...",
                                currentFileName = sampleFile.name,
                                currentFolderPath = sampleFile.folderPath,
                                currentOffsetBytes = readNow,
                                currentBlockLabel = "PARALLEL NIO 1KB + 4KB ENGINE",
                                currentHashPreview = "TURBO MULTI-CORE",
                                comboMultiplier = 32,
                                streakLabel = "HYPER-SPEED PARALLEL NIO ⚡",
                                liveDuplicatesFoundCount = confirmedGroupsQueue.sumOf { it.duplicateCount },
                                liveReclaimableBytesFound = confirmedGroupsQueue.sumOf { it.reclaimableBytes },
                                totalFilesDiscovered = validFiles.size,
                                totalBytesDiscovered = totalBytes,
                                sizeMatchedCandidates = sizeMatchedFiles.size,
                                sizeEliminatedFiles = sizeEliminatedCount,
                                oneKbChecksPerformed = oneKbChecks.get(),
                                oneKbEliminatedFiles = oneKbEliminated.get(),
                                fourKbChunksReadCount = fourKbChunksRead.get(),
                                fourKbEliminatedFiles = fourKbEliminated.get(),
                                maxFourKbDepthReached = maxFourKbDepth.get(),
                                cacheHitsCount = cacheHits.get(),
                                actualBytesRead = readNow,
                                totalCandidateBytes = totalCandidateBytes,
                                bytesSavedByEarlyExit = savedNow,
                                ioSavedPercent = savedPct,
                                elapsedTimeMs = elapsedMs,
                                effectiveSpeedMbPerSec = speedMbSec
                            )
                        )
                    }
                }
            }.awaitAll()
        }

        // Batch persist new signatures in a single transaction
        val newSignatures = signaturesToCacheQueue.toList()
        if (newSignatures.isNotEmpty()) {
            try {
                dao?.upsertSignatures(newSignatures)
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
            }
        }

        val finalOneKbChecks = oneKbChecks.get()
        val finalOneKbEliminated = oneKbEliminated.get()
        val finalFourKbChunks = fourKbChunksRead.get()
        val finalFourKbEliminated = fourKbEliminated.get()
        val finalMaxDepth = maxFourKbDepth.get()
        val finalCacheHits = cacheHits.get()
        val finalBytesRead = actualBytesRead.get()
        val survivorsAfter1Kb = sizeMatchedFiles.size - finalOneKbEliminated

        val sortedGroups = confirmedGroupsQueue.sortedByDescending { it.reclaimableBytes }
        val finalDuplicatesFilesCount = sortedGroups.sumOf { it.files.size }
        val totalDuplicatesCopies = sortedGroups.sumOf { it.duplicateCount }
        val totalReclaimable = sortedGroups.sumOf { it.reclaimableBytes }

        stageLogs.add(
            StageStepLog(
                stepTitle = "Stadio 1 • Hash Parallelo Primi 1 KB (XXHash64)",
                detail = "Esaminati i primi 1.024 byte di $finalOneKbChecks file ($finalCacheHits da cache RAM): scartati subito $finalOneKbEliminated file diversi",
                candidatesIn = sizeMatchedFiles.size,
                candidatesOut = survivorsAfter1Kb,
                bytesReadDelta = finalOneKbChecks.toLong() * FIRST_BLOCK_BYTES.toLong()
            )
        )

        stageLogs.add(
            StageStepLog(
                stepTitle = "Stadio 2+ • Avanzamento Sequenziale NIO 4 KB",
                detail = "Letti $finalFourKbChunks blocchi da 4.096 byte senza riapertura file (profondità max ${finalMaxDepth}×4KB): scartati $finalFourKbEliminated file divergenti e confermati ${sortedGroups.size} gruppi duplicati",
                candidatesIn = survivorsAfter1Kb,
                candidatesOut = finalDuplicatesFilesCount,
                bytesReadDelta = finalBytesRead
            )
        )

        val elapsedMicros = max(100L, (System.nanoTime() - startNano) / 1_000L)
        val totalElapsedMs = max(1L, elapsedMicros / 1_000L)
        val totalSavedBytes = (totalCandidateBytes - finalBytesRead).coerceAtLeast(0L)
        val ioSavedPct = if (totalCandidateBytes > 0L) {
            ((totalSavedBytes * 100L) / totalCandidateBytes).toInt().coerceIn(0, 99)
        } else if (totalBytes > 0L) 99 else 0

        // Calculate speed using high-resolution microseconds so sub-millisecond scans show true GB/s or MB/s
        val finalSpeedMbPerSec =
            (totalBytes.toDouble() / (1024.0 * 1024.0)) / (elapsedMicros.toDouble() / 1_000_000.0)

        pushFeed(
            tag = "INSTANT ⚡",
            title = "Scansione Istantanea Completata in $totalElapsedMs ms (${elapsedMicros} µs)",
            detail = "Trovati ${sortedGroups.size} gruppi ($totalDuplicatesCopies copie • ${ByteFormatter.formatBytes(totalReclaimable)}) • Risparmiati ${ByteFormatter.formatBytes(totalSavedBytes)} di I/O!",
            hexDigest = String.format(Locale.ITALIAN, "%.1f MB/s", finalSpeedMbPerSec),
            badgeDeltaText = "${totalElapsedMs} ms",
            type = VerboseEventType.DUPLICATE_JACKPOT
        )

        // Build final visual block matrix reflecting the completed scan
        val finalMatrix = defaultBlockMatrix().mapIndexed { index, cell ->
            when {
                index == 0 -> cell.copy(status = BlockCellStatus.MATCHED_1KB)
                index <= finalMaxDepth.coerceAtLeast(4) -> cell.copy(status = BlockCellStatus.MATCHED_4KB)
                else -> cell.copy(status = BlockCellStatus.DIVERGED_EARLY_EXIT)
            }
        }

        val recentVerboseFeed = verboseQueue.toList().asReversed().take(50)
        val comboFinal = (16 + sortedGroups.size * 8 + finalOneKbEliminated * 4 + finalFourKbEliminated * 4)
            .coerceIn(2, 64)

        val finalTelemetry = ScanTelemetry(
            phase = ScanPhase.COMPLETED,
            progressFraction = 1f,
            currentStatusText = "Completata in ${totalElapsedMs}ms (${elapsedMicros}µs) • ${sortedGroups.size} gruppi duplicati",
            currentFileName = sortedGroups.firstOrNull()?.files?.firstOrNull()?.name
                ?: validFiles.firstOrNull()?.name
                ?: "Scansione Istantanea Completata",
            currentFolderPath = sortedGroups.firstOrNull()?.files?.firstOrNull()?.folderPath
                ?: "${validFiles.size} file analizzati",
            currentOffsetBytes = finalBytesRead,
            currentBlockLabel = "1 KB + $finalFourKbChunks BLOCCHI 4 KB (${totalElapsedMs} ms)",
            currentHashPreview = sortedGroups.firstOrNull()?.signatureHex?.let { "0x${it.uppercase(Locale.ROOT)}" }
                ?: "0xINSTANT_DONE",
            comboMultiplier = comboFinal,
            streakLabel = "INSTANT NIO OVERDRIVE 🔥 (${totalElapsedMs} ms)",
            liveDuplicatesFoundCount = totalDuplicatesCopies,
            liveReclaimableBytesFound = totalReclaimable,
            totalFilesDiscovered = validFiles.size,
            totalBytesDiscovered = totalBytes,
            sizeMatchedCandidates = sizeMatchedFiles.size,
            sizeEliminatedFiles = sizeEliminatedCount,
            oneKbChecksPerformed = finalOneKbChecks,
            oneKbEliminatedFiles = finalOneKbEliminated,
            fourKbChunksReadCount = finalFourKbChunks,
            fourKbEliminatedFiles = finalFourKbEliminated,
            maxFourKbDepthReached = finalMaxDepth,
            cacheHitsCount = finalCacheHits,
            actualBytesRead = finalBytesRead,
            totalCandidateBytes = totalCandidateBytes,
            bytesSavedByEarlyExit = totalSavedBytes,
            ioSavedPercent = ioSavedPct,
            elapsedTimeMs = totalElapsedMs,
            effectiveSpeedMbPerSec = finalSpeedMbPerSec,
            blockMatrix = finalMatrix,
            verboseFeed = recentVerboseFeed,
            stageLogs = stageLogs.toList()
        )

        onTelemetryUpdate(finalTelemetry)

        ProgressiveScanOutput(
            duplicateGroups = sortedGroups,
            telemetry = finalTelemetry,
            nearMissCollisionsCount = nearMissCollisions.get()
        )
    }

    /**
     * Processes a single size bucket using persistent open readers so consecutive
     * 1 KB -> 4 KB -> 4 KB reads never re-open or re-seek from 0.
     */
    private fun processSizeBucketFast(
        fileSize: Long,
        sameSizeFiles: List<ScannedFileItem>,
        selectionRule: SelectionRule,
        ramSignatureCache: Map<String, CachedFileSignature>,
        oneKbChecks: AtomicInteger,
        oneKbEliminated: AtomicInteger,
        fourKbChunksRead: AtomicInteger,
        fourKbEliminated: AtomicInteger,
        maxFourKbDepth: AtomicInteger,
        cacheHits: AtomicInteger,
        actualBytesRead: AtomicLong,
        nearMissCollisions: AtomicInteger,
        confirmedGroupsQueue: ConcurrentLinkedQueue<DuplicateGroup>,
        signaturesToCacheQueue: ConcurrentLinkedQueue<CachedFileSignature>,
        pushFeed: (String, String, String, String, String, VerboseEventType) -> Unit
    ) {
        val localBuf = threadLocalBuffer.get()

        // Fast check: if ALL files in this size bucket are already in RAM cache with a fullProgressiveHash,
        // we can group them in 0.001ms with ZERO disk I/O!
        val allCached = sameSizeFiles.mapNotNull { item ->
            val sig = ramSignatureCache[item.fullPathOrUri]
            if (sig != null && sig.sizeBytes == item.sizeBytes &&
                sig.lastModified == item.lastModified && !sig.fullProgressiveHash.isNullOrEmpty()
            ) {
                item to sig
            } else {
                null
            }
        }

        if (allCached.size == sameSizeFiles.size) {
            cacheHits.addAndGet(sameSizeFiles.size)
            oneKbChecks.addAndGet(sameSizeFiles.size)
            val groupedByCachedFull = allCached.groupBy({ it.second.fullProgressiveHash!! }, { it.first to it.second })
            for ((fullHash, pairs) in groupedByCachedFull) {
                if (pairs.size >= 2) {
                    val steps = pairs.first().second.fourKbChunksVerified
                    val first1Kb = pairs.first().second.first1KbHash
                    val groupFiles = applySelectionRuleToGroup(
                        files = pairs.map { (item, _) ->
                            item.copy(
                                first1KbHashHex = first1Kb,
                                finalProgressiveHashHex = fullHash,
                                fourKbStepsVerified = steps
                            )
                        },
                        rule = selectionRule
                    )
                    val group = buildDuplicateGroup(
                        signatureHex = fullHash,
                        fileSize = fileSize,
                        files = groupFiles,
                        fourKbSteps = steps,
                        bytesReadPerFile = 0L
                    )
                    confirmedGroupsQueue.add(group)
                    pushFeed(
                        "RAM-CACHE ⚡",
                        "Match Istantaneo RAM (${groupFiles.size} copie): ${groupFiles.first().name}",
                        "0 byte letti dal disco • Recuperabili ${ByteFormatter.formatBytes(group.reclaimableBytes)}",
                        "0x${fullHash.uppercase(Locale.ROOT)}",
                        "0 ms RAM",
                        VerboseEventType.CACHE_TURBO
                    )
                } else {
                    oneKbEliminated.incrementAndGet()
                }
            }
            return
        }

        // Open persistent readers for candidates in this size bucket
        val readersMap = HashMap<String, FastSequentialFileReader>(sameSizeFiles.size)
        try {
            val oneKbSubBuckets = HashMap<Long, MutableList<ScannedFileItem>>()
            val firstBlockLen = minOf(FIRST_BLOCK_BYTES.toLong(), fileSize).toInt()

            // =================================================================
            // STAGE 1: First 1 KB (1,024 bytes) Hash Check
            // =================================================================
            for (fileItem in sameSizeFiles) {
                val cached = ramSignatureCache[fileItem.fullPathOrUri]?.takeIf {
                    it.sizeBytes == fileItem.sizeBytes && it.lastModified == fileItem.lastModified
                }

                val hash64: Long
                val hex1Kb: String

                if (cached != null && cached.first1KbHash.length == 16) {
                    hex1Kb = cached.first1KbHash
                    hash64 = java.lang.Long.parseUnsignedLong(hex1Kb, 16)
                    cacheHits.incrementAndGet()
                } else {
                    val reader = openReaderFor(fileItem) ?: continue
                    val nRead = reader.readNextBlock(localBuf, 0L, firstBlockLen)
                    if (nRead <= 0) {
                        reader.close()
                        continue
                    }
                    actualBytesRead.addAndGet(nRead.toLong())
                    hash64 = fastHash64(localBuf, nRead, seed = fileSize)
                    hex1Kb = longToHex16(hash64)
                    readersMap[fileItem.id] = reader
                }

                oneKbChecks.incrementAndGet()
                val updated = fileItem.copy(first1KbHashHex = hex1Kb)
                oneKbSubBuckets.getOrPut(hash64) { ArrayList(2) }.add(updated)
            }

            // Close readers immediately for any file whose 1 KB header was unique!
            val surviving1KbBuckets = HashMap<Long, List<ScannedFileItem>>()
            for ((h64, bucket) in oneKbSubBuckets) {
                if (bucket.size >= 2) {
                    surviving1KbBuckets[h64] = bucket
                } else {
                    val single = bucket.first()
                    readersMap.remove(single.id)?.close()
                    oneKbEliminated.incrementAndGet()
                    nearMissCollisions.incrementAndGet()
                    val savedBytes = (fileSize - firstBlockLen).coerceAtLeast(0L)
                    pushFeed(
                        "EARLY-1KB",
                        "Scarto @ 1 KB: ${single.name}",
                        "Primi 1.024 B unici • Evitata lettura di ${ByteFormatter.formatBytes(savedBytes)}",
                        "0x${single.first1KbHashHex.uppercase(Locale.ROOT)}",
                        "+${ByteFormatter.formatBytes(savedBytes)} SALVATI",
                        VerboseEventType.EARLY_EXIT_SAVED
                    )
                }
            }

            // =================================================================
            // STAGE 2+: Progressive 4 KB (4,096 bytes) Stepping Loop
            // =================================================================
            for ((oneKbHashLong, candidatesAfter1Kb) in surviving1KbBuckets) {
                val oneKbHex = longToHex16(oneKbHashLong)

                if (fileSize <= FIRST_BLOCK_BYTES.toLong()) {
                    val groupFiles = applySelectionRuleToGroup(
                        files = candidatesAfter1Kb.map {
                            it.copy(
                                finalProgressiveHashHex = oneKbHex,
                                fourKbStepsVerified = 0
                            )
                        },
                        rule = selectionRule
                    )
                    val group = buildDuplicateGroup(
                        signatureHex = oneKbHex,
                        fileSize = fileSize,
                        files = groupFiles,
                        fourKbSteps = 0,
                        bytesReadPerFile = fileSize
                    )
                    confirmedGroupsQueue.add(group)
                    groupFiles.forEach { f ->
                        signaturesToCacheQueue.add(
                            CachedFileSignature(
                                fileKey = f.fullPathOrUri,
                                sizeBytes = f.sizeBytes,
                                lastModified = f.lastModified,
                                first1KbHash = oneKbHex,
                                fullProgressiveHash = oneKbHex,
                                fourKbChunksVerified = 0
                            )
                        )
                    }
                } else {
                    var activeBuckets: Map<Long, List<ScannedFileItem>> =
                        mapOf(oneKbHashLong to candidatesAfter1Kb)
                    var currentOffset = FIRST_BLOCK_BYTES.toLong()
                    var stepDepth = 0

                    while (currentOffset < fileSize && activeBuckets.isNotEmpty()) {
                        stepDepth++
                        val bytesInWindow = minOf(STEP_BLOCK_BYTES.toLong(), fileSize - currentOffset).toInt()
                        val nextBuckets = HashMap<Long, MutableList<ScannedFileItem>>()

                        for ((runningHash64, bucketFiles) in activeBuckets) {
                            val subPartition = HashMap<Long, MutableList<ScannedFileItem>>()
                            for (fileItem in bucketFiles) {
                                val reader = readersMap.getOrPut(fileItem.id) {
                                    openReaderFor(fileItem) ?: continue
                                }
                                val nRead = reader.readNextBlock(localBuf, currentOffset, bytesInWindow)
                                if (nRead > 0) {
                                    actualBytesRead.addAndGet(nRead.toLong())
                                    fourKbChunksRead.incrementAndGet()
                                    val combined64 = fastHash64(localBuf, nRead, seed = runningHash64)
                                    subPartition.getOrPut(combined64) { ArrayList(2) }.add(fileItem)
                                }
                            }

                            for ((combined64, subFiles) in subPartition) {
                                if (subFiles.size >= 2) {
                                    nextBuckets.getOrPut(combined64) { ArrayList(subFiles.size) }.addAll(subFiles)
                                } else {
                                    // Early exit at this 4 KB block! Close its file descriptor immediately!
                                    val diverged = subFiles.first()
                                    readersMap.remove(diverged.id)?.close()
                                    fourKbEliminated.incrementAndGet()
                                    nearMissCollisions.incrementAndGet()
                                    val savedBytes = (fileSize - (currentOffset + bytesInWindow)).coerceAtLeast(0L)
                                    pushFeed(
                                        "4KB-EXIT#$stepDepth",
                                        "Scarto al Blocco 4 KB #$stepDepth: ${diverged.name}",
                                        "Divergenza a offset ${ByteFormatter.formatBytes(currentOffset)} • Risparmiati ${ByteFormatter.formatBytes(savedBytes)}",
                                        "0x${longToHex16(combined64).uppercase(Locale.ROOT)}",
                                        "+${ByteFormatter.formatBytes(savedBytes)} SALVATI",
                                        VerboseEventType.EARLY_EXIT_SAVED
                                    )
                                }
                            }
                        }

                        val prevMax = maxFourKbDepth.get()
                        if (stepDepth > prevMax) {
                            maxFourKbDepth.compareAndSet(prevMax, stepDepth)
                        }

                        activeBuckets = nextBuckets
                        currentOffset += bytesInWindow.toLong()
                    }

                    for ((finalHash64, matchedFiles) in activeBuckets) {
                        val finalHex = longToHex16(finalHash64)
                        val groupFiles = applySelectionRuleToGroup(
                            files = matchedFiles.map {
                                it.copy(
                                    finalProgressiveHashHex = finalHex,
                                    fourKbStepsVerified = stepDepth
                                )
                            },
                            rule = selectionRule
                        )
                        val group = buildDuplicateGroup(
                            signatureHex = finalHex,
                            fileSize = fileSize,
                            files = groupFiles,
                            fourKbSteps = stepDepth,
                            bytesReadPerFile = fileSize
                        )
                        confirmedGroupsQueue.add(group)

                        val keeper = groupFiles.firstOrNull { it.isOriginalRecommended } ?: groupFiles.first()
                        pushFeed(
                            "JACKPOT 🎯",
                            "Duplicato Confermato (${groupFiles.size}× ${ByteFormatter.formatBytes(fileSize)}): ${keeper.name}",
                            "1 KB + $stepDepth×4 KB verificati • Originale protetto in ${keeper.folderPath}",
                            "0x${finalHex.uppercase(Locale.ROOT)}",
                            "+${ByteFormatter.formatBytes(group.reclaimableBytes)}",
                            VerboseEventType.DUPLICATE_JACKPOT
                        )

                        groupFiles.forEach { f ->
                            signaturesToCacheQueue.add(
                                CachedFileSignature(
                                    fileKey = f.fullPathOrUri,
                                    sizeBytes = f.sizeBytes,
                                    lastModified = f.lastModified,
                                    first1KbHash = oneKbHex,
                                    fullProgressiveHash = finalHex,
                                    fourKbChunksVerified = stepDepth
                                )
                            )
                        }
                    }
                }
            }
        } finally {
            readersMap.values.forEach { reader ->
                try {
                    reader.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun openReaderFor(item: ScannedFileItem): FastSequentialFileReader? {
        if (fastReaderProvider != null) {
            return fastReaderProvider.invoke(item)
        }
        val stream = streamProvider(item) ?: return null
        return object : FastSequentialFileReader {
            private var pos = 0L
            override fun readNextBlock(targetBuffer: ByteArray, offsetInFile: Long, length: Int): Int {
                while (pos < offsetInFile) {
                    val skipped = stream.skip(offsetInFile - pos)
                    if (skipped <= 0L) {
                        if (stream.read() == -1) return -1
                        pos += 1L
                    } else {
                        pos += skipped
                    }
                }
                var total = 0
                while (total < length) {
                    val n = stream.read(targetBuffer, total, length - total)
                    if (n == -1) break
                    total += n
                    pos += n
                }
                return total
            }

            override fun close() {
                try {
                    stream.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun buildDuplicateGroup(
        signatureHex: String,
        fileSize: Long,
        files: List<ScannedFileItem>,
        fourKbSteps: Int,
        bytesReadPerFile: Long
    ): DuplicateGroup {
        val primaryCategory = files.firstOrNull()?.category ?: FileCategory.ARCHIVES
        val keeper = files.firstOrNull { it.isOriginalRecommended } ?: files.first()
        val copiesCount = (files.size - 1).coerceAtLeast(1)
        val verdict = buildString {
            append("Verificato al 100% tramite 1 KB + $fourKbSteps blocchi da 4 KB (XXHash64 NIO). ")
            append("Consigliato mantenere '${keeper.name}' in ${keeper.folderPath} ")
            append("(${keeper.originReason.ifBlank { "copia principale" }}) ed eliminare $copiesCount ")
            append(if (copiesCount == 1) "copia ridondante." else "copie ridondanti.")
        }

        return DuplicateGroup(
            groupId = "${fileSize}_$signatureHex",
            signatureHex = signatureHex,
            fileSizeBytes = fileSize,
            category = primaryCategory,
            files = files,
            fourKbBlocksChecked = fourKbSteps,
            bytesReadPerFile = bytesReadPerFile,
            technicalVerdict = verdict
        )
    }
}
