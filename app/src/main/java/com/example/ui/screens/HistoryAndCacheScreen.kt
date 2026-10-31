package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CleanupHistoryEntity
import com.example.domain.model.ByteFormatter
import com.example.domain.model.ScanTelemetry
import com.example.ui.theme.ElevatedCardSurface
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.NeonCyanPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryAndCacheScreen(
    telemetry: ScanTelemetry,
    cachedSignaturesCount: Int,
    cleanupHistory: List<CleanupHistoryEntity>,
    onClearHashCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember {
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALIAN)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("history_and_cache_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Local 1KB/4KB Hash Cache Management Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonCyanPrimary.copy(alpha = 0.4f), RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = ElevatedCardSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = NeonCyanPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CACHE HASH LOCALE 1 KB → 4 KB",
                            style = MaterialTheme.typography.labelLarge,
                            color = NeonCyanPrimary
                        )
                    }

                    Text(
                        text = "$cachedSignaturesCount Firme Memorizzate",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Le firme dei blocchi da 1 KB e 4 KB vengono salvate nel database locale del telefono per evitare di rileggere i file non modificati durante le scansioni successive.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = onClearHashCache,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("clear_hash_cache_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = NeonCyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Svuota Cache Hash ($cachedSignaturesCount)", color = NeonCyanPrimary)
                    }
                }
            }
        }

        // 2. Last Scan Technical Breakdown
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "TELEMETRIA DETTAGLIATA ULTIMA SCANSIONE",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonCyanPrimary
                    )
                    TelemetryStatRow("Volume Totale File Scoperti", ByteFormatter.formatBytes(telemetry.totalBytesDiscovered))
                    TelemetryStatRow("Volume Candidati Stessa Dimensione", ByteFormatter.formatBytes(telemetry.totalCandidateBytes))
                    TelemetryStatRow("Byte Effettivamente Letti dal Disco", ByteFormatter.formatBytes(telemetry.actualBytesRead))
                    TelemetryStatRow("I/O Risparmiato grazie a 1KB → 4KB", "${ByteFormatter.formatBytes(telemetry.bytesSavedByEarlyExit)} (${telemetry.ioSavedPercent}%)")
                    TelemetryStatRow("Blocchi da 1 KB Verificati", "${telemetry.oneKbChecksPerformed} (scartati: ${telemetry.oneKbEliminatedFiles})")
                    TelemetryStatRow("Blocchi da 4 KB Verificati", "${telemetry.fourKbChunksReadCount} (scartati: ${telemetry.fourKbEliminatedFiles})")
                }
            }
        }

        // 3. Cleanup History Section
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = NeonCyanPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "STORICO PULIZIE EFFETTUATE (${cleanupHistory.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyanPrimary
                )
            }
        }

        if (cleanupHistory.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = "Nessuna sessione di pulizia registrata. Elimina i duplicati trovati per salvare il report nello storico locale.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(
                items = cleanupHistory,
                key = { it.id }
            ) { record ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = EmeraldSafe,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = record.summaryNote,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${dateFormat.format(Date(record.timestamp))} • Scansione: ${record.scanDurationMs}ms (${record.ioSavedPercent}% I/O salvato)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "+${ByteFormatter.formatBytes(record.bytesFreed)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSafe
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryStatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ElevatedCardSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = NeonCyanPrimary
        )
    }
}
