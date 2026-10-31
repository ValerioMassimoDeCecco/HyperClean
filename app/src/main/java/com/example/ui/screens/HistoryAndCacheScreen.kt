package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.CleanupHistoryEntity
import com.example.domain.model.ByteFormatter
import com.example.domain.model.ScanTelemetry
import com.example.ui.components.Hairline
import com.example.ui.components.HyperCard
import com.example.ui.components.IndexBadge
import com.example.ui.components.LedgerRow
import com.example.ui.components.MonoLabel
import com.example.ui.components.OutlinePillButton
import com.example.ui.components.PillTag
import com.example.ui.components.SectionHeading
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.PaperSunken
import com.example.ui.theme.PaperTint
import com.example.ui.theme.ProtectedGreen
import com.example.ui.theme.ProtectedSoft
import com.example.ui.theme.VioletNote
import com.example.ui.theme.VioletSoft
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
    val dateFormat = remember { SimpleDateFormat("dd MMM · HH:mm", Locale.ITALIAN) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("history_and_cache_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HyperCard(
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(20.dp),
                elevation = 10
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MonoLabel(text = "Archivio interventi", color = InkMuted)
                    PillTag(
                        text = "${cleanupHistory.size} sessioni",
                        background = PaperTint,
                        foreground = Ink
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                MonoLabel(text = "Spazio liberato in totale", color = InkMuted)
                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    val formatted = ByteFormatter.formatBytes(
                        cleanupHistory.sumOf { it.bytesFreed }
                    )
                    Text(
                        text = formatted.substringBefore(' '),
                        style = MaterialTheme.typography.displayLarge,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatted.substringAfter(' ', ""),
                        style = MaterialTheme.typography.headlineMedium,
                        color = InkFaint,
                        modifier = Modifier.padding(bottom = 7.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (cleanupHistory.isEmpty()) {
                        "Ogni pulizia completata viene annotata qui con byte liberati, durata e I/O risparmiato."
                    } else {
                        "${cleanupHistory.sumOf { it.filesDeletedCount }} copie rimosse in ${cleanupHistory.size} interventi registrati sul dispositivo."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted
                )

                Spacer(modifier = Modifier.height(18.dp))
                Hairline()
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MiniStat(
                        label = "Ultima durata",
                        value = "${telemetry.elapsedTimeMs} ms",
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(PaperSunken)
                    )
                    MiniStat(
                        label = "I/O risparmiato",
                        value = "${telemetry.ioSavedPercent}%",
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(PaperSunken)
                    )
                    MiniStat(
                        label = "File analizzati",
                        value = "${telemetry.totalFilesDiscovered}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item { SectionHeading(title = "Cache firme") }

        item {
            HyperCard(contentPadding = PaddingValues(18.dp), elevation = 4) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VioletSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = VioletNote,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Firme 1 KB / 4 KB",
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink
                        )
                        Text(
                            text = "Salvate localmente, mai inviate altrove",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkFaint
                        )
                    }
                    Text(
                        text = "$cachedSignaturesCount",
                        style = MaterialTheme.typography.displayMedium,
                        color = Ink
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Gli hash già calcolati permettono alle scansioni successive di saltare la rilettura dei file non modificati.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinePillButton(
                    text = "Svuota cache",
                    icon = Icons.Default.Memory,
                    onClick = onClearHashCache,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clear_hash_cache_button")
                )
            }
        }

        item { SectionHeading(title = "Ultima scansione") }

        item {
            HyperCard(contentPadding = PaddingValues(18.dp), elevation = 4) {
                LedgerRow(
                    label = "Volume totale dei file scoperti",
                    value = ByteFormatter.formatBytes(telemetry.totalBytesDiscovered)
                )
                Hairline(color = PaperTint)
                LedgerRow(
                    label = "Candidati con la stessa dimensione",
                    value = ByteFormatter.formatBytes(telemetry.totalCandidateBytes)
                )
                Hairline(color = PaperTint)
                LedgerRow(
                    label = "Byte realmente letti dal disco",
                    value = ByteFormatter.formatBytes(telemetry.actualBytesRead)
                )
                Hairline(color = PaperTint)
                LedgerRow(
                    label = "I/O evitato dall'uscita anticipata",
                    value = ByteFormatter.formatBytes(telemetry.bytesSavedByEarlyExit),
                    valueColor = ProtectedGreen,
                    emphasized = true
                )
                Hairline(color = PaperTint)
                LedgerRow(
                    label = "Sonde SHA-256 da 3 × 4 KB",
                    value = "${telemetry.probeSignaturesChecked}",
                    valueColor = Ink
                )
                Hairline(color = PaperTint)
                LedgerRow(
                    label = "File confrontati byte-per-byte",
                    value = "${telemetry.byteCompareVerifiedFiles}",
                    valueColor = Ink
                )
                Hairline(color = PaperTint)
                LedgerRow(
                    label = "Hardlink esclusi dalle copie",
                    value = "${telemetry.hardlinkSetsExcluded}",
                    valueColor = Ink
                )
                Hairline(color = PaperTint)
                LedgerRow(
                    label = "Collisioni di sonda respinte",
                    value = "${telemetry.rejectedProbeCollisions}",
                    valueColor = ProtectedGreen
                )
            }
        }

        item { SectionHeading(title = "Interventi registrati") }

        if (cleanupHistory.isEmpty()) {
            item {
                HyperCard(contentPadding = PaddingValues(18.dp), elevation = 3) {
                    Text(
                        text = "Nessuna sessione di pulizia. Elimina le copie ridondanti dalla raccolta duplicati per popolare l'archivio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkFaint
                    )
                }
            }
        } else {
            itemsIndexed(
                items = cleanupHistory,
                key = { _, record -> record.id }
            ) { index, record ->
                CleanupRecordRow(
                    record = record,
                    index = index,
                    dateText = dateFormat.format(Date(record.timestamp))
                )
            }
        }
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        MonoLabel(text = label, color = InkMuted)
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = Ink
        )
    }
}

@Composable
private fun CleanupRecordRow(
    record: CleanupHistoryEntity,
    index: Int,
    dateText: String
) {
    HyperCard(
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(14.dp),
        elevation = 4
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IndexBadge(
                text = String.format(Locale.ITALIAN, "%02d", index + 1),
                background = ProtectedSoft,
                foreground = ProtectedGreen
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.summaryNote,
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "$dateText · ${record.filesDeletedCount} file · ${record.ioSavedPercent}% I/O",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkFaint,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = ProtectedGreen,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = ByteFormatter.formatBytes(record.bytesFreed),
                        style = MaterialTheme.typography.titleMedium,
                        color = ProtectedGreen
                    )
                }
                MonoLabel(text = "liberati", color = InkFaint)
            }
        }
    }
}
