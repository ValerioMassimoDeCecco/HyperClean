package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.ByteFormatter
import com.example.domain.model.DuplicateGroup
import com.example.domain.model.FileCategory
import com.example.domain.model.ScannedFileItem
import com.example.domain.model.SelectionRule
import com.example.ui.DuplicateCleanerUiState
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.CoralDangerContainer
import com.example.ui.theme.ElectricIndigoSecondary
import com.example.ui.theme.ElevatedCardSurface
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.EmeraldSafeContainer
import com.example.ui.theme.NeonCyanPrimary
import com.example.ui.theme.OutlineSubtle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DuplicatesReviewScreen(
    uiState: DuplicateCleanerUiState,
    onSelectCategory: (FileCategory) -> Unit,
    onSelectRule: (SelectionRule) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleFileSelection: (groupId: String, fileId: String) -> Unit,
    onConfirmDeleteSelected: () -> Unit,
    onAddSafFolderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showRulePickerDialog by remember { mutableStateOf(false) }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }

    val filteredGroups = uiState.filteredGroups

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("duplicates_review_screen")
    ) {
        // Top Filter & Auto-Selection Rule Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("duplicates_search_input"),
                    placeholder = { Text("Cerca per nome o cartella...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = NeonCyanPrimary)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                OutlinedButton(
                    onClick = { showRulePickerDialog = true },
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("selection_rule_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Criterio Selezione",
                        tint = NeonCyanPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Regola", color = NeonCyanPrimary)
                }
            }

            // Active Auto-Selection Rule Banner
            Surface(
                color = ElectricIndigoSecondary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRulePickerDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = NeonCyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Criterio attivo: ${uiState.selectionRule.titleIt}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Cambia",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyanPrimary
                    )
                }
            }

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FileCategory.entries.forEach { category ->
                    val isSelected = uiState.selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectCategory(category) },
                        label = { Text(category.labelIt) },
                        modifier = Modifier.testTag("category_chip_${category.name.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyanPrimary.copy(alpha = 0.22f),
                            selectedLabelColor = NeonCyanPrimary
                        )
                    )
                }
            }
        }

        // Duplicate Groups List
        if (filteredGroups.isEmpty()) {
            EmptyDuplicatesState(
                onAddSafFolderClick = onAddSafFolderClick,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("duplicate_groups_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(
                    items = filteredGroups,
                    key = { it.groupId }
                ) { group ->
                    DuplicateGroupCard(
                        group = group,
                        onToggleFile = { fileId ->
                            onToggleFileSelection(group.groupId, fileId)
                        }
                    )
                }
            }
        }

        // Bottom Sticky Action Bar for 1-Tap Safe Deletion
        if (uiState.totalSelectedFilesCount > 0) {
            Surface(
                color = ElevatedCardSurface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CoralDanger.copy(alpha = 0.4f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${uiState.totalSelectedFilesCount} copie selezionate",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Liberi ${ByteFormatter.formatBytes(uiState.totalSelectedReclaimableBytes)} • Originali protetti",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldSafe
                        )
                    }

                    Button(
                        onClick = { showConfirmDeleteDialog = true },
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("delete_selected_duplicates_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CoralDanger,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Elimina Ora",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }

    if (showRulePickerDialog) {
        SelectionRuleDialog(
            currentRule = uiState.selectionRule,
            onSelectRule = { rule ->
                onSelectRule(rule)
                showRulePickerDialog = false
            },
            onDismiss = { showRulePickerDialog = false }
        )
    }

    if (showConfirmDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteDialog = false },
            title = {
                Text("Conferma Eliminazione Duplicati")
            },
            text = {
                Text(
                    "Stai per eliminare definitivamente ${uiState.totalSelectedFilesCount} copie ridondanti liberando " +
                        "${ByteFormatter.formatBytes(uiState.totalSelectedReclaimableBytes)}.\n\n" +
                        "Per ogni gruppo verrà mantenuto intatto il file originale protetto."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDeleteDialog = false
                        onConfirmDeleteSelected()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CoralDanger,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_delete_dialog_button")
                ) {
                    Text("Elimina Definitivamente")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    onToggleFile: (String) -> Unit
) {
    var showTechnicalDetails by remember { mutableStateOf(false) }

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
            // Group Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeonCyanPrimary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = categoryIcon(group.category),
                            contentDescription = null,
                            tint = NeonCyanPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${group.files.size} File Identici • ${ByteFormatter.formatBytes(group.fileSizeBytes)} cad.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Hash 1KB+${group.fourKbBlocksChecked}×4KB: #${group.signatureHex.uppercase(Locale.ROOT)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyanPrimary
                        )
                    }
                }

                Surface(
                    color = CoralDangerContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "-${ByteFormatter.formatBytes(group.reclaimableBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFFECDD3),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Individual files inside the duplicate cluster
            group.files.forEach { fileItem ->
                DuplicateFileItemRow(
                    fileItem = fileItem,
                    onToggle = { onToggleFile(fileItem.id) }
                )
            }

            // Expandable 1KB/4KB Verification & Smart Verdict Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTechnicalDetails = !showTechnicalDetails }
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dettagli verifica 1KB → 4KB & motivazione scelta originale",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (showTechnicalDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Espandi dettagli",
                    tint = NeonCyanPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = showTechnicalDetails) {
                Surface(
                    color = ElevatedCardSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = group.technicalVerdict,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Blocco 1 KB iniziale: #${group.files.firstOrNull()?.first1KbHashHex.orEmpty()} • Passi da 4 KB verificati: ${group.fourKbBlocksChecked}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyanPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateFileItemRow(
    fileItem: ScannedFileItem,
    onToggle: () -> Unit
) {
    val isSelected = fileItem.isSelectedForDeletion
    val borderColor = if (isSelected) {
        CoralDanger.copy(alpha = 0.55f)
    } else {
        EmeraldSafe.copy(alpha = 0.65f)
    }
    val rowBg = if (isSelected) {
        CoralDangerContainer.copy(alpha = 0.25f)
    } else {
        EmeraldSafeContainer.copy(alpha = 0.28f)
    }

    val dateFormatter = remember {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALIAN)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(rowBg)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = CoralDanger,
                uncheckedColor = EmeraldSafe
            )
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = if (isSelected) CoralDanger else EmeraldSafe,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.DeleteForever else Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isSelected) "ELIMINA COPIA" else "ORIGINALE PROTETTO",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = dateFormatter.format(Date(fileItem.lastModified)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = fileItem.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Cartella: ${fileItem.folderPath}",
                style = MaterialTheme.typography.bodySmall,
                color = NeonCyanPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (fileItem.originReason.isNotBlank()) {
                Text(
                    text = fileItem.originReason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyDuplicatesState(
    onAddSafFolderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = EmeraldSafe,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Nessun duplicato in questa vista",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Tutti i file analizzati sono unici oppure puoi collegare una cartella del telefono per estendere la scansione.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onAddSafFolderClick,
            modifier = Modifier.testTag("empty_state_add_folder_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonCyanPrimary,
                contentColor = Color(0xFF00201B)
            )
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Seleziona Cartella Dispositivo")
        }
    }
}

@Composable
private fun SelectionRuleDialog(
    currentRule: SelectionRule,
    onSelectRule: (SelectionRule) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Criterio Automatico Scelta Originale")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectionRule.entries.forEach { rule ->
                    val isSelected = rule == currentRule
                    Surface(
                        color = if (isSelected) NeonCyanPrimary.copy(alpha = 0.18f) else ElevatedCardSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isSelected) NeonCyanPrimary else OutlineSubtle,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectRule(rule) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = rule.titleIt,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isSelected) NeonCyanPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = rule.descriptionIt,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Chiudi")
            }
        }
    )
}

private fun categoryIcon(category: FileCategory): ImageVector = when (category) {
    FileCategory.ALL -> Icons.Default.FolderZip
    FileCategory.IMAGES -> Icons.Default.Image
    FileCategory.VIDEOS -> Icons.Default.VideoFile
    FileCategory.AUDIO -> Icons.Default.AudioFile
    FileCategory.DOCUMENTS -> Icons.Default.Description
    FileCategory.ARCHIVES -> Icons.Default.FolderZip
}
