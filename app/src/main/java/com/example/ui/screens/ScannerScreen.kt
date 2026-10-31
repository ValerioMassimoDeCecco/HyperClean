package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.LinkedFolderEntity
import com.example.domain.model.BlockCellStatus
import com.example.domain.model.BlockCellVisual
import com.example.domain.model.ByteFormatter
import com.example.domain.model.ScanPhase
import com.example.domain.model.ScanTelemetry
import com.example.domain.model.StageStepLog
import com.example.domain.model.VerboseEventType
import com.example.domain.model.VerboseFeedLine
import com.example.ui.DuplicateCleanerUiState
import com.example.ui.components.BarMeter
import com.example.ui.components.Hairline
import com.example.ui.components.HyperCard
import com.example.ui.components.IconSquare
import com.example.ui.components.IndexBadge
import com.example.ui.components.InkButton
import com.example.ui.components.MonoLabel
import com.example.ui.components.OutlinePillButton
import com.example.ui.components.PillTag
import com.example.ui.components.SectionHeading
import com.example.ui.components.StatusDot
import com.example.ui.components.VerticalRule
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.ConsoleInk
import com.example.ui.theme.ConsoleInkRaised
import com.example.ui.theme.ConsoleMuted
import com.example.ui.theme.ConsoleText
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.PaperSunken
import com.example.ui.theme.PaperTint
import com.example.ui.theme.ProtectedGreen
import com.example.ui.theme.ProtectedSoft
import com.example.ui.theme.SignalLime
import com.example.ui.theme.SignalLimeSoft
import com.example.ui.theme.VioletNote
import com.example.ui.theme.VioletSoft
import kotlin.math.roundToInt

@Composable
fun ScannerScreen(
    uiState: DuplicateCleanerUiState,
    cachedSignaturesCount: Int,
    totalBytesFreedAllTime: Long,
    linkedFolders: List<LinkedFolderEntity>,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onAddSafFolderClick: () -> Unit,
    onRemoveSafFolder: (String) -> Unit,
    onNavigateToDuplicates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry = uiState.telemetry

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("radar_scanner_list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScanHero(
                uiState = uiState,
                telemetry = telemetry,
                cachedSignaturesCount = cachedSignaturesCount,
                totalBytesFreedAllTime = totalBytesFreedAllTime,
                onStartScan = onStartScan,
                onCancelScan = onCancelScan,
                onNavigateToDuplicates = onNavigateToDuplicates
            )
        }

        item { QuickMetricsCard(telemetry = telemetry) }

        item { BlockConsoleCard(isScanning = uiState.isScanning, telemetry = telemetry) }

        item { AlgorithmFunnelCard(telemetry = telemetry) }

        item {
            LiveLedgerCard(
                feed = telemetry.verboseFeed,
                isScanning = uiState.isScanning
            )
        }

        item {
            ScanScopeCard(
                linkedFolders = linkedFolders,
                onAddSafFolderClick = onAddSafFolderClick,
                onRemoveSafFolder = onRemoveSafFolder
            )
        }

        if (telemetry.stageLogs.isNotEmpty()) {
            item {
                SectionHeading(
                    title = "Rapporto degli stadi",
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            items(telemetry.stageLogs) { log ->
                StageReportRow(log = log)
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Hero — the editorial cover of the dashboard                        */
/* ------------------------------------------------------------------ */

@Composable
private fun ScanHero(
    uiState: DuplicateCleanerUiState,
    telemetry: ScanTelemetry,
    cachedSignaturesCount: Int,
    totalBytesFreedAllTime: Long,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onNavigateToDuplicates: () -> Unit
) {
    val reclaimable = if (uiState.isScanning) {
        telemetry.liveReclaimableBytesFound
    } else {
        uiState.totalSelectedReclaimableBytes
    }

    val animatedProgress by animateFloatAsState(
        targetValue = telemetry.progressFraction,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "hero_progress"
    )

    HyperCard(
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(20.dp),
        elevation = 10
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(
                    color = if (uiState.isScanning) SignalLime else InkFaint,
                    size = 8
                )
                Spacer(modifier = Modifier.width(8.dp))
                MonoLabel(
                    text = phaseShortLabel(telemetry.phase),
                    color = InkMuted
                )
            }
            if (telemetry.comboMultiplier > 1) {
                PillTag(
                    text = "x${telemetry.comboMultiplier}",
                    background = SignalLime,
                    foreground = Ink
                )
            } else {
                PillTag(
                    text = if (uiState.isScanning) "live" else "pronto",
                    background = if (uiState.isScanning) SignalLime else PaperSunken,
                    foreground = Ink
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        MonoLabel(text = "Spazio recuperabile", color = InkMuted)

        Spacer(modifier = Modifier.height(2.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            val formatted = ByteFormatter.formatBytes(reclaimable)
            val number = formatted.substringBefore(' ')
            val unit = formatted.substringAfter(' ', "")
            Text(
                text = number,
                style = MaterialTheme.typography.displayLarge,
                color = Ink
            )
            if (unit.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.headlineMedium,
                    color = InkFaint,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (uiState.isScanning) {
                "${telemetry.liveDuplicatesFoundCount} gruppi individuati finora · ${telemetry.currentStatusText}"
            } else if (uiState.duplicateGroups.isEmpty()) {
                "Nessuna copia ridondante rilevata: l'archivio è già in ordine."
            } else {
                "${uiState.duplicateGroups.size} gruppi · ${uiState.totalSelectedFilesCount} copie selezionate per l'eliminazione"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(20.dp))

        SegmentRuler(progress = animatedProgress)

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonoLabel(
                text = "${(animatedProgress * 100).roundToInt()}% completato",
                color = Ink
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = InkMuted,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                MonoLabel(
                    text = String.format(
                        java.util.Locale.ITALIAN,
                        "%.1f MB/s",
                        telemetry.effectiveSpeedMbPerSec
                    ),
                    color = InkMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (uiState.isScanning) {
                OutlinePillButton(
                    text = "Interrompi",
                    icon = Icons.Default.Pause,
                    color = AlertCoral,
                    borderColor = AlertCoral.copy(alpha = 0.45f),
                    onClick = onCancelScan,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cancel_scan_button")
                )
            } else {
                InkButton(
                    text = "Avvia scansione",
                    icon = if (telemetry.phase == ScanPhase.IDLE) {
                        Icons.Default.PlayArrow
                    } else {
                        Icons.Default.RestartAlt
                    },
                    onClick = onStartScan,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("start_scan_button")
                )
            }

            if (uiState.duplicateGroups.isNotEmpty() && !uiState.isScanning) {
                InkButton(
                    text = "Rivedi ${uiState.totalSelectedFilesCount}",
                    icon = Icons.Default.DeleteSweep,
                    containerColor = AlertCoral,
                    contentColor = Color.White,
                    onClick = onNavigateToDuplicates,
                    modifier = Modifier.testTag("review_duplicates_cta")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Hairline()
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MonoLabel(
                text = "$cachedSignaturesCount firme in cache",
                color = InkFaint
            )
            MonoLabel(
                text = "${ByteFormatter.formatBytes(totalBytesFreedAllTime)} liberati in totale",
                color = InkFaint
            )
        }
    }
}

@Composable
private fun SegmentRuler(
    progress: Float,
    segments: Int = 34
) {
    val filled = (progress.coerceIn(0f, 1f) * segments).roundToInt()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(segments) { index ->
            val isFilled = index < filled
            val isHead = index == filled && progress < 0.999f
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            isFilled -> Ink
                            isHead -> SignalLime
                            else -> PaperSunken
                        }
                    )
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Quick metrics                                                      */
/* ------------------------------------------------------------------ */

@Composable
private fun QuickMetricsCard(telemetry: ScanTelemetry) {
    HyperCard(contentPadding = PaddingValues(vertical = 14.dp, horizontal = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickMetric(
                label = "I/O evitato",
                value = ByteFormatter.formatBytes(telemetry.bytesSavedByEarlyExit),
                hint = "${telemetry.ioSavedPercent}% del totale",
                color = ProtectedGreen,
                modifier = Modifier.weight(1f)
            )
            VerticalRule(height = 38)
            QuickMetric(
                label = "Byte letti",
                value = ByteFormatter.formatBytes(telemetry.actualBytesRead),
                hint = "${telemetry.fourKbChunksReadCount} blocchi",
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            VerticalRule(height = 38)
            QuickMetric(
                label = "Tempo",
                value = "${telemetry.elapsedTimeMs} ms",
                hint = "${telemetry.totalFilesDiscovered} file",
                color = VioletNote,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickMetric(
    label: String,
    value: String,
    hint: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        MonoLabel(text = label, color = InkMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = InkFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* ------------------------------------------------------------------ */
/*  Dark console — file under inspection + block rail                  */
/* ------------------------------------------------------------------ */

@Composable
private fun BlockConsoleCard(
    isScanning: Boolean,
    telemetry: ScanTelemetry
) {
    HyperCard(
        color = ConsoleInk,
        borderColor = ConsoleInkRaised,
        shape = RoundedCornerShape(26.dp),
        contentPadding = PaddingValues(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(color = if (isScanning) SignalLime else ConsoleMuted)
                Spacer(modifier = Modifier.width(8.dp))
                MonoLabel(text = "Sotto esame", color = ConsoleMuted)
            }
            MonoLabel(text = telemetry.currentBlockLabel, color = SignalLime)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = telemetry.currentFileName,
            style = MaterialTheme.typography.titleMedium,
            color = ConsoleText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = telemetry.currentFolderPath,
            style = MaterialTheme.typography.bodySmall,
            color = ConsoleMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(16.dp))

        BlockRail(cells = telemetry.blockMatrix.take(8))
        Spacer(modifier = Modifier.height(6.dp))
        BlockRail(cells = telemetry.blockMatrix.drop(8).take(8))

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                MonoLabel(text = "Offset", color = ConsoleMuted)
                Text(
                    text = "+${ByteFormatter.formatBytes(telemetry.currentOffsetBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ConsoleText
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                MonoLabel(text = "Digest", color = ConsoleMuted)
                Text(
                    text = telemetry.currentHashPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = SignalLime,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BlockRail(cells: List<BlockCellVisual>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        cells.forEach { cell ->
            val background = when (cell.status) {
                BlockCellStatus.IDLE -> ConsoleInkRaised
                BlockCellStatus.SCANNING_1KB -> SignalLime
                BlockCellStatus.SCANNING_4KB -> SignalLime
                BlockCellStatus.MATCHED_1KB -> SignalLime.copy(alpha = 0.22f)
                BlockCellStatus.MATCHED_4KB -> SignalLime.copy(alpha = 0.22f)
                BlockCellStatus.DIVERGED_EARLY_EXIT -> AlertCoral
            }
            val foreground = when (cell.status) {
                BlockCellStatus.IDLE -> ConsoleMuted
                BlockCellStatus.SCANNING_1KB -> ConsoleInk
                BlockCellStatus.SCANNING_4KB -> ConsoleInk
                BlockCellStatus.MATCHED_1KB -> SignalLime
                BlockCellStatus.MATCHED_4KB -> SignalLime
                BlockCellStatus.DIVERGED_EARLY_EXIT -> Color.White
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(background),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (cell.status == BlockCellStatus.DIVERGED_EARLY_EXIT) "×" else cell.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = foreground
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Algorithm funnel                                                   */
/* ------------------------------------------------------------------ */

@Composable
private fun AlgorithmFunnelCard(telemetry: ScanTelemetry) {
    val stage0Ratio = if (telemetry.totalFilesDiscovered > 0) {
        telemetry.sizeMatchedCandidates.toFloat() / telemetry.totalFilesDiscovered
    } else {
        0f
    }
    val stage1Ratio = if (telemetry.oneKbChecksPerformed > 0) {
        1f - telemetry.oneKbEliminatedFiles.toFloat() / telemetry.oneKbChecksPerformed
    } else {
        1f
    }
    val stage2Ratio = if (telemetry.probeMatchesCount > 0) {
        telemetry.byteCompareVerifiedFiles.toFloat() / telemetry.probeMatchesCount
    } else if (telemetry.byteCompareVerifiedFiles > 0) 1f else 0f

    HyperCard(contentPadding = PaddingValues(18.dp)) {
        SectionHeading(
            title = "Catena di verifica",
            trailing = {
                PillTag(
                    text = "${telemetry.ioSavedPercent}% I/O risparmiato",
                    background = ProtectedSoft,
                    foreground = ProtectedGreen
                )
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        FunnelStage(
            index = "00",
            title = "Dimensione esatta",
            detail = "${telemetry.sizeEliminatedFiles} file scartati · zero byte letti",
            value = "${telemetry.sizeMatchedCandidates}",
            ratio = stage0Ratio,
            accent = VioletNote
        )
        Spacer(modifier = Modifier.height(14.dp))
        FunnelStage(
            index = "01",
            title = "Identità fisica",
            detail = "${telemetry.hardlinkSetsExcluded} set di hardlink esclusi · zero byte letti",
            value = "${telemetry.hardlinkSetsExcluded}",
            ratio = if (telemetry.sizeMatchedCandidates > 0) {
                1f - telemetry.hardlinkSetsExcluded.toFloat() / telemetry.sizeMatchedCandidates
            } else 1f,
            accent = Ink
        )
        Spacer(modifier = Modifier.height(14.dp))
        FunnelStage(
            index = "02",
            title = "Sonda SHA-256 3 × 4 KB",
            detail = "${telemetry.oneKbEliminatedFiles} scartati in 12 KB per file",
            value = "${telemetry.probeSignaturesChecked}",
            ratio = stage1Ratio,
            accent = Ink
        )
        Spacer(modifier = Modifier.height(14.dp))
        FunnelStage(
            index = "03",
            title = "Conferma byte-per-byte",
            detail = "${telemetry.rejectedProbeCollisions} collisioni di sonda respinte",
            value = "${telemetry.byteCompareVerifiedFiles}",
            ratio = stage2Ratio,
            accent = ProtectedGreen
        )

        Spacer(modifier = Modifier.height(16.dp))
        Hairline()
        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = SignalLime.copy(alpha = 0.9f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = "Nessun file è dichiarato duplicato sulla base di un hash: il verdetto arriva solo dopo il confronto integrale del contenuto.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted
            )
        }
    }
}

@Composable
private fun FunnelStage(
    index: String,
    title: String,
    detail: String,
    value: String,
    ratio: Float,
    accent: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IndexBadge(text = index)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    color = accent
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            BarMeter(fraction = ratio, color = accent, height = 4)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = InkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Live ledger                                                        */
/* ------------------------------------------------------------------ */

@Composable
private fun LiveLedgerCard(
    feed: List<VerboseFeedLine>,
    isScanning: Boolean
) {
    val visible = feed.takeLast(7).reversed()

    HyperCard(contentPadding = PaddingValues(18.dp)) {
        SectionHeading(
            title = "Registro live",
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(
                        color = if (isScanning && visible.isNotEmpty()) SignalLime else InkFaint,
                        size = 6
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    MonoLabel(
                        text = if (isScanning) "stream" else "fermo",
                        color = InkMuted
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (visible.isEmpty()) {
            Text(
                text = "Nessun evento registrato. Avvia una scansione per vedere ogni test da 1 KB e ogni passo da 4 KB.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkFaint,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        } else {
            visible.forEachIndexed { index, line ->
                FeedRow(line = line)
                if (index != visible.lastIndex) {
                    Hairline(color = PaperSunken)
                }
            }
        }
    }
}

@Composable
private fun FeedRow(line: VerboseFeedLine) {
    val (tagBackground, tagForeground) = when (line.type) {
        VerboseEventType.SIZE_FILTER -> VioletSoft to VioletNote
        VerboseEventType.ONE_KB_HASH -> SignalLimeSoft to Ink
        VerboseEventType.FOUR_KB_STEP -> PaperTint to InkMuted
        VerboseEventType.PROBE -> SignalLimeSoft to Ink
        VerboseEventType.EARLY_EXIT_SAVED -> ProtectedSoft to ProtectedGreen
        VerboseEventType.DUPLICATE_JACKPOT -> SignalLime to Ink
        VerboseEventType.CACHE_TURBO -> VioletSoft to VioletNote
        VerboseEventType.LINK -> PaperTint to VioletNote
        VerboseEventType.CONFIRM -> SignalLime to Ink
        VerboseEventType.INFO -> PaperTint to InkMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.width(52.dp)) {
            Text(
                text = "${line.elapsedMs}",
                style = MaterialTheme.typography.bodySmall,
                color = InkFaint
            )
            Text(
                text = "ms",
                style = MaterialTheme.typography.labelSmall,
                color = InkFaint
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            PillTag(
                text = line.tag,
                background = tagBackground,
                foreground = tagForeground
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = line.title,
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (line.detail.isNotBlank()) {
                Text(
                    text = line.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (line.hexDigest.isNotBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = line.hexDigest.take(10),
                style = MaterialTheme.typography.bodySmall,
                color = InkFaint,
                maxLines = 1
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Storage scope                                                      */
/* ------------------------------------------------------------------ */

@Composable
private fun ScanScopeCard(
    linkedFolders: List<LinkedFolderEntity>,
    onAddSafFolderClick: () -> Unit,
    onRemoveSafFolder: (String) -> Unit
) {
    HyperCard(contentPadding = PaddingValues(18.dp)) {
        SectionHeading(
            title = "Aree scansionate",
            trailing = {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SignalLimeSoft)
                        .clickable { onAddSafFolderClick() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("add_saf_folder_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Ink,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    MonoLabel(text = "Aggiungi", color = Ink)
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        ScopeRow(
            icon = Icons.Default.FolderOpen,
            title = "Catalogo multimediale",
            subtitle = "Foto, video, audio, download e documenti indicizzati dal sistema",
            accent = PaperTint
        )

        linkedFolders.forEach { folder ->
            Spacer(modifier = Modifier.height(9.dp))
            ScopeRow(
                icon = Icons.Default.FolderOpen,
                title = folder.displayName,
                subtitle = "Cartella collegata via Storage Access Framework",
                accent = SignalLimeSoft,
                onRemove = { onRemoveSafFolder(folder.uriString) }
            )
        }

        if (linkedFolders.isEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Collega una cartella qualsiasi del telefono per estendere la ricerca oltre il catalogo multimediale.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkFaint
            )
        }
    }
}

@Composable
private fun ScopeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onRemove: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PaperTint)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconSquare(icon = icon, background = accent, size = 34)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = InkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Rimuovi cartella",
                    tint = InkMuted,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Stage report                                                       */
/* ------------------------------------------------------------------ */

@Composable
private fun StageReportRow(log: StageStepLog) {
    HyperCard(
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(14.dp),
        elevation = 3
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(ProtectedSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = ProtectedGreen,
                    modifier = Modifier.size(13.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = log.stepTitle,
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            MonoLabel(
                text = "${log.candidatesIn} → ${log.candidatesOut}",
                color = Ink
            )
        }
        if (log.detail.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = log.detail,
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

private fun phaseShortLabel(phase: ScanPhase): String = when (phase) {
    ScanPhase.IDLE -> "In attesa"
    ScanPhase.DISCOVERING -> "Indicizzazione file"
    ScanPhase.STAGE_0_SIZE -> "Stadio 0 · dimensione"
    ScanPhase.STAGE_1_1KB -> "Stadio 1 · testata 1 KB"
    ScanPhase.STAGE_2_4KB_LOOP -> "Stadio 2 · catena 4 KB"
    ScanPhase.COMPLETED -> "Scansione completata"
}
