package com.example.domain.engine

import android.system.Os
import com.example.data.local.CachedFileSignature
import com.example.data.local.HyperCleanDao
import com.example.data.repository.FastSequentialFileReader
import com.example.domain.model.BlockCellStatus
import com.example.domain.model.BlockCellVisual
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
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * HyperClean Exact Duplicate Engine — pipeline a cinque stadi.
 *
 *  0. DIMENSIONE ESATTA      raggruppa per numero di byte: zero I/O sui file unici.
 *  1. IDENTITÀ FISICA        `stat(2)` → (st_dev, st_ino): gli hardlink sono lo stesso
 *                            file fisico e non liberano spazio, quindi non sono copie.
 *  2. CACHE CONTENUTO        firme SHA-256 già verificate di file intatti: zero I/O.
 *  3. SONDA 3 × 4 KB         SHA-256 di testa + centro + coda (12 KB per file): elimina
 *                            i candidati non identici senza leggere il resto del file.
 *  4. CONFERMA ESATTA        confronto diretto byte-per-byte e SHA-256 dell'intero
 *                            contenuto: nessun falso positivo possibile.
 *
 * Il verdetto finale non è probabilistico: due file finiscono nello stesso gruppo solo
 * dopo un confronto integrale del contenuto (o dopo aver ritrovato in cache la loro
 * firma SHA-256 completa, calcolata da un confronto esatto precedente).
 */
class ProgressiveHashEngine(
    private val dao: HyperCleanDao?,
    private val streamProvider: (ScannedFileItem) -> InputStream?,
    private val fastReaderProvider: ((ScannedFileItem) -> FastSequentialFileReader?)? = null
) {

    companion object {
        /** Prima sonda: 4 KB di testa. */
        const val PROBE_BLOCK_BYTES = 4096
        /** Buffer del confronto diretto: 128 KB per lettura. */
        const val CONFIRM_BUFFER_BYTES = 128 * 1024
        /** Letture simultanee: oltre questo valore lo storage va in thrashing. */
        const val MAX_IO_PARALLELISM = 4
        /** Prefisso delle firme cache: identifica gli hash SHA-256 dell'intero contenuto. */
        const val CACHE_VERSION_TAG = "v2c:"

        private const val ALGORITHM = "SHA-256"
        private const val MAX_FEED_LINES = 240

        private val COPY_REGEX = Regex(
            pattern = """(\(\d+\)|_copy|copia|backup|dup|wa\d+|received_|_edit|-copy)""",
            option = RegexOption.IGNORE_CASE
        )

        /** Sceglie l'originale da proteggere e seleziona le copie da eliminare. */
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

        /** Chiave d'identità fisica del file: -1 quando non è determinabile. */
        fun physicalIdentityKey(item: ScannedFileItem): Long {
            if (item.isSafUri) return -1L
            return try {
                val stat = Os.stat(item.fullPathOrUri)
                val inode = stat.st_ino
                if (inode <= 0L) -1L else {
                    // Combina device e inode in una chiave stabile a 64 bit.
                    (stat.st_dev * 1_000_003L) xor inode
                }
            } catch (_: Throwable) {
                -1L
            }
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

        val verboseQueue = ConcurrentLinkedQueue<VerboseFeedLine>()
        val eventSeq = AtomicLong(1L)
        val stageLogs = mutableListOf<StageStepLog>()

        val probeChecks = AtomicInteger()
        val probeEliminated = AtomicInteger()
        val probeMatches = AtomicInteger()
        val cacheHits = AtomicInteger()
        val bytesRead = AtomicLong()
        val verifiedFiles = AtomicInteger()
        val rejectedCollisions = AtomicInteger()
        val hardlinkSets = AtomicInteger()

        var processedBytes = 0L
        var lastHashPreview = "0x0000000000000000"
        var lastOffset = 0L
        var lastFileName = "In attesa di avvio…"
        var lastFolderPath = "—"

        fun elapsedMs(): Long = (System.nanoTime() - startNano) / 1_000_000L

        fun pushFeed(
            tag: String,
            title: String,
            detail: String,
            hexDigest: String = "",
            type: VerboseEventType = VerboseEventType.INFO
        ) {
            while (verboseQueue.size > MAX_FEED_LINES) {
                verboseQueue.poll()
            }
            verboseQueue.add(
                VerboseFeedLine(
                    id = eventSeq.getAndIncrement(),
                    elapsedMs = elapsedMs(),
                    tag = tag,
                    title = title,
                    detail = detail,
                    hexDigest = hexDigest,
                    type = type
                )
            )
        }

        // ================================================================
        // STADIO 0 — dimensione esatta (zero I/O)
        // ================================================================
        val sizeBuckets = validFiles
            .groupBy { it.sizeBytes }
            .filterValues { it.size >= 2 }

        val candidateFiles = sizeBuckets.values.sumOf { it.size }
        val candidateBytes = sizeBuckets.values.sumOf { bucket -> bucket.sumOf { it.sizeBytes } }

        fun makeTelemetry(
            phase: ScanPhase,
            progress: Float,
            status: String,
            blockLabel: String,
            matrix: List<BlockCellVisual>
        ): ScanTelemetry {
            val read = bytesRead.get()
            val saved = (totalBytes - read).coerceAtLeast(0L)
            val ioPercent = if (totalBytes > 0L) ((saved * 100L) / totalBytes).toInt() else 0
            val elapsed = elapsedMs().coerceAtLeast(1L)
            val found = verifiedFiles.get()
            return ScanTelemetry(
                phase = phase,
                progressFraction = progress.coerceIn(0f, 1f),
                currentStatusText = status,
                currentFileName = lastFileName,
                currentFolderPath = lastFolderPath,
                currentOffsetBytes = lastOffset,
                currentBlockLabel = blockLabel,
                currentHashPreview = lastHashPreview,
                comboMultiplier = (1 + probeEliminated.get() / 40).coerceAtMost(99),
                streakLabel = "MOTORE ESATTO · SHA-256",
                liveDuplicatesFoundCount = verifiedFiles.get() / 2,
                liveReclaimableBytesFound = 0L,
                totalFilesDiscovered = validFiles.size,
                totalBytesDiscovered = totalBytes,
                sizeMatchedCandidates = candidateFiles,
                sizeEliminatedFiles = validFiles.size - candidateFiles,
                oneKbChecksPerformed = probeChecks.get(),
                oneKbEliminatedFiles = probeEliminated.get(),
                fourKbChunksReadCount = probeChecks.get() * 3,
                fourKbEliminatedFiles = probeEliminated.get(),
                maxFourKbDepthReached = 3,
                cacheHitsCount = cacheHits.get(),
                actualBytesRead = read,
                totalCandidateBytes = candidateBytes,
                bytesSavedByEarlyExit = saved,
                ioSavedPercent = ioPercent,
                elapsedTimeMs = elapsed,
                effectiveSpeedMbPerSec = (read / 1_048_576.0) / (elapsed / 1000.0),
                blockMatrix = matrix,
                verboseFeed = verboseQueue.toList(),
                stageLogs = stageLogs.toList(),
                hardlinkSetsExcluded = hardlinkSets.get(),
                probeSignaturesChecked = probeChecks.get(),
                probeMatchesCount = probeMatches.get(),
                byteCompareVerifiedFiles = found,
                rejectedProbeCollisions = rejectedCollisions.get()
            )
        }

        stageLogs += StageStepLog(
            stepTitle = "Stadio 0 · dimensione esatta",
            detail = "${sizeBuckets.size} gruppi con lo stesso numero di byte, nessun byte letto",
            candidatesIn = validFiles.size,
            candidatesOut = candidateFiles,
            bytesReadDelta = 0L
        )
        pushFeed(
            tag = "SIZE",
            title = "Filtro dimensione completato",
            detail = "$candidateFiles candidati su ${validFiles.size} file · ${sizeBuckets.size} gruppi",
            type = VerboseEventType.SIZE_FILTER
        )

        onTelemetryUpdate(
            makeTelemetry(
                phase = ScanPhase.STAGE_0_SIZE,
                progress = 0.12f,
                status = "Raggruppamento per dimensione esatta",
                blockLabel = "STADIO 0 · DIMENSIONE",
                matrix = emptyMatrix()
            )
        )

        val ramCache: Map<String, CachedFileSignature> = try {
            dao?.getAllSignaturesList()?.associateBy { it.fileKey } ?: emptyMap()
        } catch (_: Throwable) {
            emptyMap()
        }

        val ioGate = Semaphore(MAX_IO_PARALLELISM)
        val signaturesToPersist = ConcurrentLinkedQueue<CachedFileSignature>()
        val confirmedGroups = mutableListOf<DuplicateGroup>()

        // ================================================================
        // STADIO 1 — identità fisica: hardlink esclusi dalle copie
        // ================================================================
        val physicalBuckets = ArrayList<Pair<Long, List<ScannedFileItem>>>(sizeBuckets.size)
        for ((size, members) in sizeBuckets) {
            val byIdentity = LinkedHashMap<Long, MutableList<ScannedFileItem>>()
            members.forEach { item ->
                val key = physicalIdentityKey(item)
                val mapKey = if (key == -1L) -1_000_000L - byIdentity.size.toLong() else key
                byIdentity.getOrPut(mapKey) { mutableListOf() }.add(item)
            }
            val representatives = ArrayList<ScannedFileItem>(members.size)
            for ((identity, group) in byIdentity) {
                if (identity >= 0L && group.size > 1) {
                    hardlinkSets.incrementAndGet()
                    representatives.add(group.first())
                    pushFeed(
                        tag = "LINK",
                        title = "${group.size} hardlink dello stesso file",
                        detail = "${group.first().name} · occupa spazio una sola volta, escluso dalle copie",
                        type = VerboseEventType.LINK
                    )
                } else {
                    representatives.addAll(group)
                }
            }
            if (representatives.size >= 2) physicalBuckets.add(size to representatives)
        }

        stageLogs += StageStepLog(
            stepTitle = "Stadio 1 · identità fisica",
            detail = "${hardlinkSets.get()} set di hardlink riconosciuti e sottratti ai duplicati",
            candidatesIn = candidateFiles,
            candidatesOut = physicalBuckets.sumOf { it.second.size },
            bytesReadDelta = 0L
        )
        if (hardlinkSets.get() > 0) {
            pushFeed(
                tag = "INODE",
                title = "Identità fisica risolta",
                detail = "${hardlinkSets.get()} file collegati via hardlink non sono duplicati",
                type = VerboseEventType.LINK
            )
        }

        // ================================================================
        // STADI 2-4 — cache, sonda, conferma esatta
        // ================================================================
        val bucketCount = physicalBuckets.size.coerceAtLeast(1)
        for ((bucketIndex, bucket) in physicalBuckets.withIndex()) {
            currentCoroutineContext().ensureActive()
            val (fileSize, members) = bucket

            lastFileName = members.first().name
            lastFolderPath = members.first().folderPath

            val progressBase = 0.15f + 0.8f * (bucketIndex.toFloat() / bucketCount)
            onTelemetryUpdate(
                makeTelemetry(
                    phase = ScanPhase.STAGE_1_1KB,
                    progress = progressBase,
                    status = "Sonda SHA-256 su ${members.size} file da ${ByteFormatter.formatBytes(fileSize)}",
                    blockLabel = "STADIO 3 · SONDA 3 × 4 KB",
                    matrix = probeMatrix(0, 0)
                )
            )

            // --- Stadio 2: file già verificati e immutati → zero I/O ---
            val bucketGroups = LinkedHashMap<String, MutableList<ScannedFileItem>>()
            val probeIndex = HashMap<String, String>()
            val fresh = ArrayList<ScannedFileItem>(members.size)

            for (item in members) {
                val cached = ramCache[item.fullPathOrUri]
                val contentHex = cached?.fullProgressiveHash
                if (cached != null &&
                    cached.sizeBytes == item.sizeBytes &&
                    cached.lastModified == item.lastModified &&
                    contentHex != null &&
                    contentHex.startsWith(CACHE_VERSION_TAG)
                ) {
                    cacheHits.incrementAndGet()
                    val probeHex = cached.first1KbHash
                    bucketGroups.getOrPut(contentHex) { mutableListOf() }.add(
                        item.copy(
                            first1KbHashHex = probeHex,
                            finalProgressiveHashHex = contentHex,
                            fourKbStepsVerified = 3
                        )
                    )
                    if (probeHex.isNotEmpty()) probeIndex[probeHex] = contentHex
                } else {
                    fresh.add(item)
                }
            }

            if (bucketGroups.isNotEmpty() && fresh.isEmpty()) {
                pushFeed(
                    tag = "CACHE",
                    title = "${cacheHits.get()} firme riusate dalla cache",
                    detail = "Nessuna lettura disco per ${members.size} file già verificati",
                    type = VerboseEventType.CACHE_TURBO
                )
            }

            // --- Stadio 3: sonda 3 × 4 KB in parallelo (max 4 letture) ---
            val probed = coroutineScope {
                fresh.map { item ->
                    async(Dispatchers.IO) {
                        ioGate.withPermit { item to probeSignature(item) }
                    }
                }.awaitAll()
            }

            for ((item, probeHex) in probed) {
                if (probeHex == null) continue
                probeChecks.incrementAndGet()
                bytesRead.addAndGet(minOf(item.sizeBytes, PROBE_BLOCK_BYTES * 3L))
                lastHashPreview = "0x${probeHex.take(16)}"
                lastOffset = probeOffsetFor(item.sizeBytes)
            }

            val probedOk = probed.filter { it.second != null }
            val eliminatedHere = probedOk
                .groupBy { it.second!! }
                .filterValues { it.size == 1 }
                .values
                .flatten()
                .filter { item -> item.second?.let { probeIndex[it] } == null }

            if (eliminatedHere.isNotEmpty()) {
                probeEliminated.addAndGet(eliminatedHere.size)
                pushFeed(
                    tag = "SONDA",
                    title = "${eliminatedHere.size} file scartati in 12 KB",
                    detail = "Sonda diversa: nessun confronto necessario, resto del file mai letto",
                    type = VerboseEventType.EARLY_EXIT_SAVED
                )
            }

            val probeGroups = probedOk.groupBy({ it.second!! }, { it.first })

            for ((probeHex, items) in probeGroups) {
                currentCoroutineContext().ensureActive()
                val knownContentHex = probeIndex[probeHex]

                // --- Ponte cache ↔ file nuovi: verifica diretta contro il rappresentante ---
                if (knownContentHex != null) {
                    val knownGroup = bucketGroups[knownContentHex] ?: mutableListOf()
                    val reference = knownGroup.firstOrNull()
                    for (item in items) {
                        if (reference != null && filesAreIdentical(reference, item)) {
                            verifiedFiles.incrementAndGet()
                            bytesRead.addAndGet(item.sizeBytes)
                            knownGroup.add(
                                item.copy(
                                    first1KbHashHex = probeHex,
                                    finalProgressiveHashHex = knownContentHex,
                                    fourKbStepsVerified = 3
                                )
                            )
                            persistSignature(signaturesToPersist, item, probeHex, knownContentHex)
                        } else {
                            rejectedCollisions.incrementAndGet()
                        }
                    }
                    bucketGroups[knownContentHex] = knownGroup
                    continue
                }

                if (items.size == 1) continue
                probeMatches.addAndGet(items.size)

                // --- Stadio 4: confronto diretto byte-per-byte + firma SHA-256 completa ---
                val clusters = mutableListOf<MutableList<ScannedFileItem>>()
                for (item in items) {
                    var host: MutableList<ScannedFileItem>? = null
                    for (cluster in clusters) {
                        if (filesAreIdentical(cluster.first(), item)) {
                            host = cluster
                            break
                        }
                    }
                    if (host != null) host.add(item) else clusters.add(mutableListOf(item))
                }

                for (cluster in clusters) {
                    if (cluster.size < 2) {
                        rejectedCollisions.addAndGet(cluster.size)
                        continue
                    }
                    val reference = cluster.first()
                    val contentHex = computeContentHash(reference, bytesRead)
                        ?: (CACHE_VERSION_TAG + probeHex + ":" + fileSize)
                    bytesRead.addAndGet(reference.sizeBytes * (cluster.size - 1).toLong())
                    verifiedFiles.addAndGet(cluster.size)

                    val group = bucketGroups.getOrPut(contentHex) { mutableListOf() }
                    for (item in cluster) {
                        group.add(
                            item.copy(
                                first1KbHashHex = probeHex,
                                finalProgressiveHashHex = contentHex,
                                fourKbStepsVerified = 3
                            )
                        )
                        persistSignature(signaturesToPersist, item, probeHex, contentHex)
                    }

                    pushFeed(
                        tag = "CONFIRM",
                        title = "Duplicato confermato byte-per-byte",
                        detail = "${cluster.size} copie · ${ByteFormatter.formatBytes(fileSize * (cluster.size - 1))} recuperabili",
                        hexDigest = probeHex.take(16),
                        type = VerboseEventType.CONFIRM
                    )
                }

                if (clusters.count { it.size < 2 } > 0) {
                    pushFeed(
                        tag = "COLISIONE",
                        title = "Sonda identica ma contenuto diverso",
                        detail = "Falso positivo respinto dal confronto diretto: nessun file proposto per l'eliminazione",
                        type = VerboseEventType.CONFIRM
                    )
                }
            }

            // --- Gruppi definitivi del bucket ---
            for ((contentHex, fileList) in bucketGroups) {
                if (fileList.size < 2) continue
                val selected = applySelectionRuleToGroup(files = fileList, rule = selectionRule)
                if (selected.size < 2) continue
                confirmedGroups.add(buildVerifiedGroup(contentHex, fileSize, selected))
                pushFeed(
                    tag = "MATCH",
                    title = "Gruppo verificato · ${selected.size} copie",
                    detail = "${ByteFormatter.formatBytes(fileSize * (selected.size - 1))} recuperabili",
                    hexDigest = contentHex.removePrefix(CACHE_VERSION_TAG).take(16),
                    type = VerboseEventType.DUPLICATE_JACKPOT
                )
            }

            val bucketProgress = 0.15f + 0.8f * ((bucketIndex + 1).toFloat() / bucketCount)
            onTelemetryUpdate(
                makeTelemetry(
                    phase = ScanPhase.STAGE_2_4KB_LOOP,
                    progress = bucketProgress,
                    status = "Verifica esatta in corso · ${ByteFormatter.formatBytes(processedBytes)} analizzati",
                    blockLabel = "STADIO 4 · CONFRONTO ESATTO",
                    matrix = compareMatrix(verifiedFiles.get(), confirmedGroups.size)
                )
            )
            processedBytes += members.sumOf { it.sizeBytes }
        }

        if (signaturesToPersist.isNotEmpty()) {
            try {
                dao?.upsertSignatures(signaturesToPersist.distinctBy { it.fileKey })
            } catch (_: Throwable) {
                // la cache è un'ottimizzazione: il fallimento non invalida la scansione
            }
        }

        stageLogs += StageStepLog(
            stepTitle = "Stadio 3 · sonda SHA-256 a 3 punti",
            detail = "${probeChecks.get()} sonde da 12 KB · ${probeEliminated.get()} file scartati senza leggere il resto",
            candidatesIn = physicalBuckets.sumOf { it.second.size },
            candidatesOut = probeChecks.get() - probeEliminated.get(),
            bytesReadDelta = probeChecks.get() * PROBE_BLOCK_BYTES * 3L
        )
        stageLogs += StageStepLog(
            stepTitle = "Stadio 4 · conferma byte-per-byte",
            detail = "${verifiedFiles.get()} file confrontati integralmente · ${rejectedCollisions.get()} collisioni respinte",
            candidatesIn = probeChecks.get() - probeEliminated.get(),
            candidatesOut = verifiedFiles.get(),
            bytesReadDelta = 0L
        )

        val finalGroups = confirmedGroups
            .sortedWith(
                compareByDescending<DuplicateGroup> { it.reclaimableBytes }
                    .thenByDescending { it.fileSizeBytes }
            )

        val totalReclaimable = finalGroups.sumOf { it.reclaimableBytes }

        val completedTelemetry = makeTelemetry(
            phase = ScanPhase.COMPLETED,
            progress = 1f,
            status = if (finalGroups.isEmpty()) {
                "Nessun duplicato: archivio già ottimale"
            } else {
                "${finalGroups.size} gruppi verificati byte-per-byte"
            },
            blockLabel = "COMPLETATO · ESATTO",
            matrix = compareMatrix(verifiedFiles.get(), finalGroups.size)
        ).copy(
            currentFileName = "Scansione completata",
            currentFolderPath = "—",
            liveDuplicatesFoundCount = finalGroups.size,
            liveReclaimableBytesFound = totalReclaimable
        )

        onTelemetryUpdate(completedTelemetry)

        ProgressiveScanOutput(
            duplicateGroups = finalGroups,
            telemetry = completedTelemetry,
            nearMissCollisionsCount = rejectedCollisions.get()
        )
    }

    /* ---------------------------------------------------------------- */
    /*  Lettura posizionale e firme                                     */
    /* ---------------------------------------------------------------- */

    /** SHA-256 di testa, centro e coda (12 KB per file) + dimensione. */
    private fun probeSignature(item: ScannedFileItem): String? {
        val size = item.sizeBytes
        if (size <= 0L) return null
        val reader = openReaderFor(item) ?: return null
        val block = ByteArray(PROBE_BLOCK_BYTES)
        val scratch = ByteArray(PROBE_BLOCK_BYTES)
        return try {
            val digest = MessageDigest.getInstance(ALGORITHM)
            digest.update(longToBytes(size))
            for (offset in probeOffsets(size)) {
                val length = minOf(PROBE_BLOCK_BYTES.toLong(), size - offset).toInt()
                if (length <= 0) continue
                val read = readChunk(reader, block, offset, length, scratch)
                if (read <= 0) return null
                digest.update(block, 0, minOf(read, length))
            }
            toHex(digest.digest())
        } catch (_: Throwable) {
            null
        } finally {
            closeQuietly(reader)
        }
    }

    /** SHA-256 dell'intero contenuto, usato come firma finale persistita. */
    private fun computeContentHash(
        item: ScannedFileItem,
        bytesCounter: AtomicLong
    ): String? {
        val reader = openReaderFor(item) ?: return null
        val buffer = ByteArray(CONFIRM_BUFFER_BYTES)
        val scratch = ByteArray(CONFIRM_BUFFER_BYTES)
        return try {
            val digest = MessageDigest.getInstance(ALGORITHM)
            var offset = 0L
            while (offset < item.sizeBytes) {
                val length = minOf(CONFIRM_BUFFER_BYTES.toLong(), item.sizeBytes - offset).toInt()
                val read = readChunk(reader, buffer, offset, length, scratch)
                if (read <= 0) return null
                digest.update(buffer, 0, read)
                bytesCounter.addAndGet(read.toLong())
                offset += read
            }
            CACHE_VERSION_TAG + toHex(digest.digest())
        } catch (_: Throwable) {
            null
        } finally {
            closeQuietly(reader)
        }
    }

    /** Confronto integrale: due file sono duplicati solo se ogni byte coincide. */
    private fun filesAreIdentical(a: ScannedFileItem, b: ScannedFileItem): Boolean {
        if (a.sizeBytes != b.sizeBytes) return false
        if (a.id == b.id) return true
        val readerA = openReaderFor(a) ?: return false
        val readerB = openReaderFor(b) ?: return false
        val bufferA = ByteArray(CONFIRM_BUFFER_BYTES)
        val bufferB = ByteArray(CONFIRM_BUFFER_BYTES)
        val scratchA = ByteArray(CONFIRM_BUFFER_BYTES)
        val scratchB = ByteArray(CONFIRM_BUFFER_BYTES)
        return try {
            var offset = 0L
            var identical = true
            while (offset < a.sizeBytes) {
                val length = minOf(CONFIRM_BUFFER_BYTES.toLong(), a.sizeBytes - offset).toInt()
                val readA = readChunk(readerA, bufferA, offset, length, scratchA)
                val readB = readChunk(readerB, bufferB, offset, length, scratchB)
                if (readA != readB || readA <= 0) {
                    identical = false
                    break
                }
                var index = 0
                while (index < readA) {
                    if (bufferA[index] != bufferB[index]) {
                        identical = false
                        break
                    }
                    index++
                }
                if (!identical) break
                offset += readA
            }
            identical
        } catch (_: Throwable) {
            false
        } finally {
            closeQuietly(readerA)
            closeQuietly(readerB)
        }
    }

    /** Offset crescenti: necessari perché i lettori su stream SAF sono solo in avanti. */
    private fun probeOffsets(size: Long): LongArray {
        val tail = size - PROBE_BLOCK_BYTES
        if (size <= PROBE_BLOCK_BYTES * 3L) return longArrayOf(0L)
        val mid = ((size / 2) / PROBE_BLOCK_BYTES) * PROBE_BLOCK_BYTES
        return if (mid <= 0L || mid >= tail) {
            longArrayOf(0L, tail)
        } else {
            longArrayOf(0L, mid, tail)
        }
    }

    private fun probeOffsetFor(size: Long): Long = probeOffsets(size).lastOrNull() ?: 0L

    /** L'API del reader riempie sempre dall'indice 0 del buffer: gestiamo le letture parziali. */
    private fun readChunk(
        reader: FastSequentialFileReader,
        target: ByteArray,
        offset: Long,
        length: Int,
        scratch: ByteArray
    ): Int {
        var total = reader.readNextBlock(target, offset, length)
        if (total <= 0 || total >= length) return total
        while (total < length) {
            val next = reader.readNextBlock(scratch, offset + total, length - total)
            if (next <= 0) break
            System.arraycopy(scratch, 0, target, total, next)
            total += next
        }
        return total
    }

    private fun openReaderFor(item: ScannedFileItem): FastSequentialFileReader? {
        if (fastReaderProvider != null) {
            return try {
                fastReaderProvider.invoke(item)
            } catch (_: Throwable) {
                null
            }
        }
        val stream = try {
            streamProvider(item)
        } catch (_: Throwable) {
            null
        } ?: return null

        return object : FastSequentialFileReader {
            private var pos = 0L

            override fun readNextBlock(targetBuffer: ByteArray, offsetInFile: Long, length: Int): Int {
                if (offsetInFile < pos) return -1
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
                    val read = stream.read(targetBuffer, total, length - total)
                    if (read <= 0) break
                    total += read
                    pos += read
                }
                return total
            }

            override fun close() {
                closeQuietly(stream)
            }
        }
    }

    /* ---------------------------------------------------------------- */
    /*  Utilità                                                         */
    /* ---------------------------------------------------------------- */

    private fun persistSignature(
        queue: ConcurrentLinkedQueue<CachedFileSignature>,
        item: ScannedFileItem,
        probeHex: String,
        contentHex: String
    ) {
        queue.add(
            CachedFileSignature(
                fileKey = item.fullPathOrUri,
                sizeBytes = item.sizeBytes,
                lastModified = item.lastModified,
                first1KbHash = probeHex,
                fullProgressiveHash = contentHex,
                fourKbChunksVerified = 3
            )
        )
    }

    private fun buildVerifiedGroup(
        contentHex: String,
        fileSize: Long,
        files: List<ScannedFileItem>
    ): DuplicateGroup {
        val keeper = files.firstOrNull { it.isOriginalRecommended } ?: files.first()
        val copies = (files.size - 1).coerceAtLeast(1)
        val verdict = buildString {
            append("Contenuto identico verificato byte-per-byte su ${files.size} file ")
            append("(SHA-256 ${contentHex.removePrefix(CACHE_VERSION_TAG).take(16)}). ")
            append("Mantenere '${keeper.name}' in ${keeper.folderPath} ")
            append("(${keeper.originReason.ifBlank { "copia principale" }}) ed eliminare $copies ")
            append(if (copies == 1) "copia ridondante." else "copie ridondanti.")
        }
        return DuplicateGroup(
            groupId = "${fileSize}_$contentHex",
            signatureHex = contentHex.removePrefix(CACHE_VERSION_TAG),
            fileSizeBytes = fileSize,
            category = files.firstOrNull()?.category ?: FileCategory.ARCHIVES,
            files = files,
            fourKbBlocksChecked = 3,
            bytesReadPerFile = minOf(fileSize, PROBE_BLOCK_BYTES * 3L),
            technicalVerdict = verdict
        )
    }

    private fun emptyMatrix(): List<BlockCellVisual> =
        (0 until 16).map { index ->
            BlockCellVisual(index, matrixLabel(index), BlockCellStatus.IDLE)
        }

    private fun probeMatrix(matched: Int, total: Int): List<BlockCellVisual> =
        (0 until 16).map { index ->
            val status = when {
                index <= 2 && index < matched -> BlockCellStatus.MATCHED_1KB
                index <= 2 -> BlockCellStatus.SCANNING_1KB
                total > 0 && index >= 3 -> BlockCellStatus.IDLE
                else -> BlockCellStatus.IDLE
            }
            BlockCellVisual(index, matrixLabel(index), status)
        }

    private fun compareMatrix(verified: Int, groups: Int): List<BlockCellVisual> =
        (0 until 16).map { index ->
            val status = when {
                groups == 0 && index == 3 -> BlockCellStatus.DIVERGED_EARLY_EXIT
                verified > 0 && index < 3 -> BlockCellStatus.MATCHED_1KB
                verified > 0 -> BlockCellStatus.MATCHED_4KB
                index < 3 -> BlockCellStatus.SCANNING_1KB
                else -> BlockCellStatus.SCANNING_4KB
            }
            BlockCellVisual(index, matrixLabel(index), status)
        }

    private fun matrixLabel(index: Int): String = when (index) {
        0 -> "H"
        1 -> "M"
        2 -> "T"
        3 -> "CMP"
        else -> "${index - 3}C"
    }

    private fun longToBytes(value: Long): ByteArray =
        ByteArray(8) { index -> (value shr (56 - 8 * index)).toByte() }

    private fun toHex(bytes: ByteArray): String {
        val chars = CharArray(bytes.size * 2)
        val digits = "0123456789abcdef"
        bytes.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            chars[index * 2] = digits[value ushr 4]
            chars[index * 2 + 1] = digits[value and 0x0F]
        }
        return String(chars)
    }

    private fun closeQuietly(closeable: java.io.Closeable?) {
        try {
            closeable?.close()
        } catch (_: Throwable) {
        }
    }
}
