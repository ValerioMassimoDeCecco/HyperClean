package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.LinkedFolderEntity
import com.example.domain.model.BlockCellStatus
import com.example.domain.model.BlockCellVisual
import com.example.domain.model.ByteFormatter
import com.example.domain.model.ScanTelemetry
import com.example.domain.model.StageStepLog
import com.example.domain.model.VerboseEventType
import com.example.domain.model.VerboseFeedLine
import com.example.ui.DuplicateCleanerUiState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.ElectricIndigoSecondary
import com.example.ui.theme.ElevatedCardSurface
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.NeonCyanPrimary
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.OutlineSubtle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RadarScannerScreen(
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Dopamine Overdrive Hero Cockpit with Live Sonar Ring, Combo Streak & Rolling Counters
        item {
            DopamineHeroCockpitCard(
                uiState = uiState,
                telemetry = telemetry,
                onStartScan = onStartScan,
                onCancelScan = onCancelScan,
                onNavigateToDuplicates = onNavigateToDuplicates
            )
        }

        // 2. Live 1KB -> 4KB Memory Block Matrix & File/Offset Laser Inspector
        item {
            LiveBlockMatrixAndInspectorCard(
                isScanning = uiState.isScanning,
                telemetry = telemetry
            )
        }

        // 3. Verbose Real-Time Cyber-Terminal Stream (Every 1KB hash, 4KB step, Early-Exit & Jackpot!)
        item {
            LiveVerboseCyberTerminalCard(
                verboseFeed = telemetry.verboseFeed,
                isScanning = uiState.isScanning
            )
        }

        // 4. Multi-Stage Filtering Funnel (Stage 0 -> Stage 1 -> Stage 2+)
        item {
            ProgressiveAlgorithmPipelineCard(telemetry = telemetry)
        }

        // 5. Key Speed & Disk I/O Saved Metrics Grid
        item {
            TelemetryMetricsGrid(
                telemetry = telemetry,
                cachedSignaturesCount = cachedSignaturesCount,
                totalBytesFreedAllTime = totalBytesFreedAllTime
            )
        }

        // 6. Storage Scope & Linked Folders Card
        item {
            StorageScopeCard(
                linkedFolders = linkedFolders,
                onAddSafFolderClick = onAddSafFolderClick,
                onRemoveSafFolder = onRemoveSafFolder
            )
        }

        // 7. Stage Summary Logs
        if (telemetry.stageLogs.isNotEmpty()) {
            item {
                Text(
                    text = "RIEPILOGO STADI DI SCANSIONE (1 KB → 4 KB)",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyanPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(telemetry.stageLogs) { stepLog ->
                StageStepLogCard(stepLog = stepLog)
            }
        }
    }
}

@Composable
private fun DopamineHeroCockpitCard(
    uiState: DuplicateCleanerUiState,
    telemetry: ScanTelemetry,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onNavigateToDuplicates: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_pulse")
    val radarSweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = telemetry.progressFraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "animated_progress"
    )

    val comboScale by animateFloatAsState(
        targetValue = if (telemetry.comboMultiplier >= 16) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "combo_scale"
    )

    val borderAccent = if (uiState.isScanning) {
        NeonCyanPrimary.copy(alpha = glowPulse)
    } else {
        NeonCyanPrimary.copy(alpha = 0.45f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderAccent, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedCardSurface)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.ic_radar_hero_banner),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.25f
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Row: Phase Badge + Dopamine Combo Multiplier Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = NeonCyanPrimary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyanPrimary)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = NeonCyanPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = telemetry.phase.titleIt,
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyanPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Combo Multiplier Badge
                    Surface(
                        color = if (telemetry.comboMultiplier >= 16) {
                            CoralDanger.copy(alpha = 0.24f)
                        } else {
                            ElectricIndigoSecondary.copy(alpha = 0.24f)
                        },
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (telemetry.comboMultiplier >= 16) CoralDanger else ElectricIndigoSecondary
                        ),
                        modifier = Modifier.scale(comboScale)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = if (telemetry.comboMultiplier >= 16) AmberWarning else NeonCyanPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "COMBO x${telemetry.comboMultiplier}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Center Row: Animated Sonar Tachometer Ring + Giant Reclaimable & Speed Metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Sonar / Speedometer Ring Canvas
                    Box(
                        modifier = Modifier.size(96.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeW = 8.dp.toPx()
                            val radius = (size.minDimension - strokeW) / 2f
                            val centerOffset = Offset(size.width / 2f, size.height / 2f)

                            // Background ring
                            drawCircle(
                                color = OutlineSubtle,
                                radius = radius,
                                center = centerOffset,
                                style = Stroke(width = strokeW)
                            )

                            // Progress arc
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(ElectricIndigoSecondary, NeonCyanPrimary, EmeraldSafe, NeonCyanPrimary)
                                ),
                                startAngle = -90f,
                                sweepAngle = 360f * animatedProgress,
                                useCenter = false,
                                topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                                size = Size(radius * 2f, radius * 2f),
                                style = Stroke(width = strokeW, cap = StrokeCap.Round)
                            )

                            // Rotating radar sweep line when scanning
                            if (uiState.isScanning) {
                                val rad = Math.toRadians(radarSweepAngle.toDouble())
                                val endX = centerOffset.x + (radius * 0.82f * cos(rad)).toFloat()
                                val endY = centerOffset.y + (radius * 0.82f * sin(rad)).toFloat()
                                drawLine(
                                    color = NeonCyanPrimary,
                                    start = centerOffset,
                                    end = Offset(endX, endY),
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(animatedProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.titleLarge,
                                color = NeonCyanPrimary
                            )
                            Text(
                                text = "${telemetry.ioSavedPercent}% I/O",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSafe
                            )
                        }
                    }

                    // Live Rolling Numbers
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = telemetry.streakLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberWarning
                        )
                        val displayBytes = if (uiState.isScanning) {
                            telemetry.liveReclaimableBytesFound
                        } else {
                            uiState.totalSelectedReclaimableBytes
                        }
                        Text(
                            text = ByteFormatter.formatBytes(displayBytes),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format(Locale.ITALIAN, "⚡ %.1f MB/s", telemetry.effectiveSpeedMbPerSec),
                                style = MaterialTheme.typography.labelMedium,
                                color = NeonCyanPrimary
                            )
                            Text(
                                text = "•",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Salvati ${ByteFormatter.formatBytes(telemetry.bytesSavedByEarlyExit)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldSafe
                            )
                        }
                    }
                }

                // Segmented Progress Bar + Live Status Ticker
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = if (uiState.isScanning) NeonCyanPrimary else EmeraldSafe,
                        trackColor = ObsidianBackground
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = telemetry.currentStatusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyanPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${telemetry.elapsedTimeMs} ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.isScanning) {
                        OutlinedButton(
                            onClick = onCancelScan,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("cancel_scan_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralDanger)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Interrompi Scansione")
                        }
                    } else {
                        Button(
                            onClick = onStartScan,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("start_scan_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyanPrimary,
                                contentColor = Color(0xFF00201B)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Lancia Scansione 1KB/4KB",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    if (uiState.duplicateGroups.isNotEmpty() && !uiState.isScanning) {
                        Button(
                            onClick = onNavigateToDuplicates,
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("review_duplicates_cta"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CoralDanger,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Elimina (${uiState.totalSelectedFilesCount})",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveBlockMatrixAndInspectorCard(
    isScanning: Boolean,
    telemetry: ScanTelemetry
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OutlineSubtle, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MATRICE BLOCCHI LIVE (1 KB → 4 KB × N)",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyanPrimary
                )
                Text(
                    text = telemetry.currentBlockLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldSafe
                )
            }

            // 16-Cell Visual Block Grid (Cell 0 = 1KB Header, Cells 1..15 = Progressive 4KB Steps)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    telemetry.blockMatrix.take(8).forEach { cell ->
                        BlockMatrixCellView(
                            cell = cell,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    telemetry.blockMatrix.drop(8).take(8).forEach { cell ->
                        BlockMatrixCellView(
                            cell = cell,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Live Byte-Level File & Hash Inspector Box
            Surface(
                color = ObsidianBackground,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonCyanPrimary.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "FILE SOTTO IL LASER:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "OFFSET: ${ByteFormatter.formatBytes(telemetry.currentOffsetBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyanPrimary
                        )
                    }
                    Text(
                        text = telemetry.currentFileName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dir: ${telemetry.currentFolderPath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DIGEST: ${telemetry.currentHashPreview}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isScanning) NeonCyanPrimary else EmeraldSafe
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockMatrixCellView(
    cell: BlockCellVisual,
    modifier: Modifier = Modifier
) {
    val (bgColor, borderColor, textColor) = when (cell.status) {
        BlockCellStatus.IDLE -> Triple(
            ObsidianBackground,
            OutlineSubtle,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        BlockCellStatus.SCANNING_1KB -> Triple(
            NeonCyanPrimary.copy(alpha = 0.28f),
            NeonCyanPrimary,
            NeonCyanPrimary
        )
        BlockCellStatus.MATCHED_1KB -> Triple(
            EmeraldSafe.copy(alpha = 0.25f),
            EmeraldSafe,
            EmeraldSafe
        )
        BlockCellStatus.SCANNING_4KB -> Triple(
            ElectricIndigoSecondary.copy(alpha = 0.32f),
            ElectricIndigoSecondary,
            Color.White
        )
        BlockCellStatus.MATCHED_4KB -> Triple(
            EmeraldSafe.copy(alpha = 0.22f),
            EmeraldSafe,
            EmeraldSafe
        )
        BlockCellStatus.DIVERGED_EARLY_EXIT -> Triple(
            CoralDanger.copy(alpha = 0.28f),
            CoralDanger,
            CoralDanger
        )
    }

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = cell.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun LiveVerboseCyberTerminalCard(
    verboseFeed: List<VerboseFeedLine>,
    isScanning: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeonCyanPrimary.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = NeonCyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TERMINALE VERBOSO IN TEMPO REALE (1KB/4KB STREAM)",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonCyanPrimary
                    )
                }

                Surface(
                    color = if (isScanning) NeonCyanPrimary.copy(alpha = 0.2f) else EmeraldSafe.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isScanning) "● LIVE STREAM" else "● COMPLETATO",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isScanning) NeonCyanPrimary else EmeraldSafe,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (verboseFeed.isEmpty()) {
                Text(
                    text = "Avvia la scansione per visualizzare il flusso verboso di ogni singolo blocco da 1 KB e 4 KB...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    verboseFeed.take(12).forEach { line ->
                        VerboseConsoleLineItem(line = line)
                    }
                }
            }
        }
    }
}

@Composable
private fun VerboseConsoleLineItem(line: VerboseFeedLine) {
    val accent = when (line.type) {
        VerboseEventType.INFO -> NeonCyanPrimary
        VerboseEventType.SIZE_FILTER -> ElectricIndigoSecondary
        VerboseEventType.ONE_KB_HASH -> NeonCyanPrimary
        VerboseEventType.FOUR_KB_STEP -> ElectricIndigoSecondary
        VerboseEventType.EARLY_EXIT_SAVED -> EmeraldSafe
        VerboseEventType.DUPLICATE_JACKPOT -> CoralDanger
        VerboseEventType.CACHE_TURBO -> AmberWarning
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ElevatedCardSurface.copy(alpha = 0.75f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = String.format(Locale.ITALIAN, "[+%04dms]", line.elapsedMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = accent.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = line.tag,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                Text(
                    text = line.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (line.badgeDeltaText.isNotBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = line.badgeDeltaText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = line.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (line.hexDigest.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = line.hexDigest,
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyanPrimary
                )
            }
        }
    }
}

@Composable
private fun ProgressiveAlgorithmPipelineCard(telemetry: ScanTelemetry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "IMBUTO DI ELIMINAZIONE MULTI-STADIO",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyanPrimary
                )
                Text(
                    text = "${telemetry.ioSavedPercent}% I/O Disco Risparmiato",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldSafe
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PipelineStagePill(
                    modifier = Modifier.weight(1f),
                    stageLabel = "0. DIMENSIONE",
                    valueText = "${telemetry.totalFilesDiscovered} → ${telemetry.sizeMatchedCandidates}",
                    subText = "-${telemetry.sizeEliminatedFiles} scartati (0 B)",
                    accentColor = ElectricIndigoSecondary
                )
                PipelineStagePill(
                    modifier = Modifier.weight(1f),
                    stageLabel = "1. PRIMI 1 KB",
                    valueText = "${telemetry.oneKbChecksPerformed} hash",
                    subText = "-${telemetry.oneKbEliminatedFiles} scartati @1KB",
                    accentColor = NeonCyanPrimary
                )
                PipelineStagePill(
                    modifier = Modifier.weight(1f),
                    stageLabel = "2+. PASSI 4 KB",
                    valueText = "${telemetry.fourKbChunksReadCount} blocchi",
                    subText = "-${telemetry.fourKbEliminatedFiles} scartati @4KB",
                    accentColor = EmeraldSafe
                )
            }
        }
    }
}

@Composable
private fun PipelineStagePill(
    stageLabel: String,
    valueText: String,
    subText: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(accentColor.copy(alpha = 0.16f), accentColor.copy(alpha = 0.04f))
                )
            )
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stageLabel,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TelemetryMetricsGrid(
    telemetry: ScanTelemetry,
    cachedSignaturesCount: Int,
    totalBytesFreedAllTime: Long
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Speed,
                label = "Tempo Scansione",
                value = "${telemetry.elapsedTimeMs} ms",
                detail = "${telemetry.totalFilesDiscovered} file analizzati",
                accent = NeonCyanPrimary
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Memory,
                label = "I/O Evitato (Early Exit)",
                value = ByteFormatter.formatBytes(telemetry.bytesSavedByEarlyExit),
                detail = "Letti solo ${ByteFormatter.formatBytes(telemetry.actualBytesRead)}",
                accent = EmeraldSafe
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Bolt,
                label = "Cache Hash 1KB/4KB",
                value = "$cachedSignaturesCount firme",
                detail = "${telemetry.cacheHitsCount} hit istantanei",
                accent = ElectricIndigoSecondary
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.CleaningServices,
                label = "Spazio Totale Liberato",
                value = ByteFormatter.formatBytes(totalBytesFreedAllTime),
                detail = "Storico pulizie dispositivo",
                accent = NeonCyanPrimary
            )
        }
    }
}

@Composable
private fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }
    }
}

@Composable
private fun StorageScopeCard(
    linkedFolders: List<LinkedFolderEntity>,
    onAddSafFolderClick: () -> Unit,
    onRemoveSafFolder: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AREE DI MEMORIA SCANSIONATE",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonCyanPrimary
                    )
                    Text(
                        text = "MediaStore (Foto, Video, Audio, Download) + Cartelle SAF collegate",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onAddSafFolderClick,
                    modifier = Modifier.testTag("add_saf_folder_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "Aggiungi Cartella",
                        tint = NeonCyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cartella", color = NeonCyanPrimary)
                }
            }

            if (linkedFolders.isNotEmpty()) {
                linkedFolders.forEach { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ElevatedCardSurface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = NeonCyanPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = folder.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { onRemoveSafFolder(folder.uriString) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Rimuovi cartella",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StageStepLogCard(stepLog: StageStepLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedCardSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = EmeraldSafe,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stepLog.stepTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${stepLog.candidatesIn} → ${stepLog.candidatesOut}",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonCyanPrimary
                    )
                }
                Text(
                    text = stepLog.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
