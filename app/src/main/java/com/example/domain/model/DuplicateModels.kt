package com.example.domain.model

import java.util.Locale

enum class FileCategory(val labelIt: String) {
    ALL("Tutti"),
    IMAGES("Foto & Immagini"),
    VIDEOS("Video"),
    AUDIO("Audio"),
    DOCUMENTS("Documenti & PDF"),
    ARCHIVES("Archivi & Altro")
}

enum class SelectionRule(val titleIt: String, val descriptionIt: String) {
    ORIGINAL_BY_DATE_AND_FOLDER(
        "Originale per Data e Cartella",
        "Protegge il file nella cartella più autorevole (es. DCIM/Documents) con data più antica e nome pulito"
    ),
    KEEP_OLDEST(
        "Mantieni il Più Vecchio",
        "Conserva sempre il file con data di creazione/modifica più antica"
    ),
    KEEP_NEWEST(
        "Mantieni il Più Recente",
        "Conserva sempre il file modificato più di recente"
    ),
    KEEP_SHORTEST_PATH(
        "Percorso Più Breve",
        "Conserva il file posizionato nella cartella principale più vicina alla radice"
    ),
    MANUAL_ONLY(
        "Solo Manuale",
        "Deseleziona tutto e lascia scegliere manualmente ogni copia"
    )
}

enum class ScanPhase(val titleIt: String) {
    IDLE("PRONTO AL LANCIO"),
    DISCOVERING("SCANSIONE RADAR FILE SYSTEM"),
    STAGE_0_SIZE("STADIO 0 • FILTRO DIMENSIONE ESATTA"),
    STAGE_1_1KB("STADIO 1 • HASH TESTATA 1 KB (1.024 BYTE)"),
    STAGE_2_4KB_LOOP("STADIO 2+ • CATENA PROGRESSIVA 4 KB (4.096 BYTE)"),
    COMPLETED("COMPLETATO • MASSIMA VELOCITÀ")
}

enum class VerboseEventType {
    INFO,
    SIZE_FILTER,
    ONE_KB_HASH,
    FOUR_KB_STEP,
    EARLY_EXIT_SAVED,
    DUPLICATE_JACKPOT,
    CACHE_TURBO
}

data class VerboseFeedLine(
    val id: Long,
    val elapsedMs: Long,
    val tag: String,
    val title: String,
    val detail: String,
    val hexDigest: String = "",
    val badgeDeltaText: String = "",
    val type: VerboseEventType = VerboseEventType.INFO
)

enum class BlockCellStatus {
    IDLE,
    SCANNING_1KB,
    MATCHED_1KB,
    SCANNING_4KB,
    MATCHED_4KB,
    DIVERGED_EARLY_EXIT
}

data class BlockCellVisual(
    val index: Int,
    val label: String,
    val status: BlockCellStatus
)

data class ScannedFileItem(
    val id: String,
    val name: String,
    val folderPath: String,
    val fullPathOrUri: String,
    val isSafUri: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val category: FileCategory,
    val originScore: Int = 0,
    val originReason: String = "",
    val isOriginalRecommended: Boolean = false,
    val isSelectedForDeletion: Boolean = false,
    val first1KbHashHex: String = "",
    val finalProgressiveHashHex: String = "",
    val fourKbStepsVerified: Int = 0
)

data class DuplicateGroup(
    val groupId: String,
    val signatureHex: String,
    val fileSizeBytes: Long,
    val category: FileCategory,
    val files: List<ScannedFileItem>,
    val fourKbBlocksChecked: Int,
    val bytesReadPerFile: Long,
    val technicalVerdict: String
) {
    val duplicateCount: Int get() = (files.size - 1).coerceAtLeast(0)
    val totalGroupBytes: Long get() = fileSizeBytes * files.size
    val reclaimableBytes: Long
        get() = files.count { it.isSelectedForDeletion } * fileSizeBytes
}

data class StageStepLog(
    val stepTitle: String,
    val detail: String,
    val candidatesIn: Int,
    val candidatesOut: Int,
    val bytesReadDelta: Long
)

data class ScanTelemetry(
    val phase: ScanPhase = ScanPhase.IDLE,
    val progressFraction: Float = 0f,
    val currentStatusText: String = "Motore 1KB → 4KB pronto",
    val currentFileName: String = "In attesa di avvio...",
    val currentFolderPath: String = "—",
    val currentOffsetBytes: Long = 0L,
    val currentBlockLabel: String = "READY [1 KB + N×4 KB]",
    val currentHashPreview: String = "0x0000000000000000",
    val comboMultiplier: Int = 1,
    val streakLabel: String = "WARMUP",
    val liveDuplicatesFoundCount: Int = 0,
    val liveReclaimableBytesFound: Long = 0L,
    val totalFilesDiscovered: Int = 0,
    val totalBytesDiscovered: Long = 0L,
    val sizeMatchedCandidates: Int = 0,
    val sizeEliminatedFiles: Int = 0,
    val oneKbChecksPerformed: Int = 0,
    val oneKbEliminatedFiles: Int = 0,
    val fourKbChunksReadCount: Int = 0,
    val fourKbEliminatedFiles: Int = 0,
    val maxFourKbDepthReached: Int = 0,
    val cacheHitsCount: Int = 0,
    val actualBytesRead: Long = 0L,
    val totalCandidateBytes: Long = 0L,
    val bytesSavedByEarlyExit: Long = 0L,
    val ioSavedPercent: Int = 0,
    val elapsedTimeMs: Long = 0L,
    val effectiveSpeedMbPerSec: Double = 0.0,
    val blockMatrix: List<BlockCellVisual> = defaultBlockMatrix(),
    val verboseFeed: List<VerboseFeedLine> = emptyList(),
    val stageLogs: List<StageStepLog> = emptyList()
)

fun defaultBlockMatrix(): List<BlockCellVisual> {
    val list = ArrayList<BlockCellVisual>(16)
    list.add(BlockCellVisual(0, "1KB", BlockCellStatus.IDLE))
    for (i in 1..15) {
        list.add(BlockCellVisual(i, "${i * 4}K", BlockCellStatus.IDLE))
    }
    return list
}

object ByteFormatter {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.ITALIAN, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.ITALIAN, "%.2f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.ITALIAN, "%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
