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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.ui.components.AccentBanner
import com.example.ui.components.CoralTag
import com.example.ui.components.Hairline
import com.example.ui.components.HyperCard
import com.example.ui.components.HyperSearchField
import com.example.ui.components.IndexBadge
import com.example.ui.components.InkButton
import com.example.ui.components.MonoChip
import com.example.ui.components.MonoLabel
import com.example.ui.components.OutlinePillButton
import com.example.ui.components.PaperCheck
import com.example.ui.components.PillTag
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.AlertCoralSoft
import com.example.ui.theme.HairlineStrong
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.PaperSurface
import com.example.ui.theme.PaperTint
import com.example.ui.theme.ProtectedGreen
import com.example.ui.theme.SignalLime
import com.example.ui.theme.SignalLimeSoft
import com.example.ui.theme.VioletNote
import com.example.ui.theme.VioletSoft
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
    var showRuleDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val filteredGroups = uiState.filteredGroups
    val reclaimable = uiState.totalSelectedReclaimableBytes
    val selectedCount = uiState.totalSelectedFilesCount
    val totalFiles = uiState.duplicateGroups.sumOf { it.files.size }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("duplicates_review_screen")
    ) {
        // Sticky summary: reclaimable space + the single destructive action
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonoLabel(text = "Raccolta duplicati", color = InkMuted)
                PillTag(
                    text = "${uiState.duplicateGroups.size} gruppi",
                    background = PaperTint,
                    foreground = Ink
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        val formatted = ByteFormatter.formatBytes(reclaimable)
                        Text(
                            text = formatted.substringBefore(' '),
                            style = MaterialTheme.typography.displayMedium,
                            color = Ink
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatted.substringAfter(' ', ""),
                            style = MaterialTheme.typography.headlineSmall,
                            color = InkFaint,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }
                    Text(
                        text = "$selectedCount copie da eliminare su ${totalFiles} rilevate",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted
                    )
                }

                InkButton(
                    text = if (selectedCount > 0) "Elimina" else "Nessuna",
                    icon = Icons.Default.DeleteForever,
                    containerColor = if (selectedCount > 0) AlertCoral else InkFaint,
                    contentColor = Color.White,
                    enabled = selectedCount > 0,
                    onClick = { showConfirmDialog = true },
                    modifier = Modifier.testTag("delete_selected_button")
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("duplicate_groups_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HyperSearchField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("duplicates_search_input")
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FileCategory.entries.forEach { category ->
                        MonoChip(
                            text = category.labelIt,
                            selected = uiState.selectedCategory == category,
                            onClick = { onSelectCategory(category) },
                            modifier = Modifier.testTag("category_chip_${category.name.lowercase()}")
                        )
                    }
                }
            }

            item {
                AccentBanner(
                    title = "Criterio automatico",
                    subtitle = uiState.selectionRule.titleIt,
                    icon = Icons.Default.Tune,
                    background = VioletSoft,
                    accent = VioletNote,
                    onClick = { showRuleDialog = true },
                    modifier = Modifier.testTag("selection_rule_button")
                )
            }

            if (filteredGroups.isEmpty()) {
                item {
                    EmptyDuplicatesState(
                        onAddSafFolderClick = onAddSafFolderClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            } else {
                itemsIndexed(
                    items = filteredGroups,
                    key = { _, group -> group.groupId }
                ) { index, group ->
                    DuplicateGroupCard(
                        group = group,
                        index = index,
                        onToggleFileSelection = onToggleFileSelection
                    )
                }
            }
        }
    }

    if (showRuleDialog) {
        SelectionRuleDialog(
            currentRule = uiState.selectionRule,
            onSelectRule = { rule ->
                onSelectRule(rule)
                showRuleDialog = false
            },
            onDismiss = { showRuleDialog = false }
        )
    }

    if (showConfirmDialog) {
        ConfirmDeleteDialog(
            count = selectedCount,
            bytes = reclaimable,
            onConfirm = {
                showConfirmDialog = false
                onConfirmDeleteSelected()
            },
            onDismiss = { showConfirmDialog = false }
        )
    }
}

/* ------------------------------------------------------------------ */
/*  Group card                                                         */
/* ------------------------------------------------------------------ */

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    index: Int,
    onToggleFileSelection: (String, String) -> Unit
) {
    var expanded by remember(group.groupId) { mutableStateOf(false) }

    HyperCard(
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(16.dp),
        elevation = 5
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IndexBadge(text = String.format(Locale.ITALIAN, "%02d", index + 1))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${group.files.size} copie identiche",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = categoryIcon(group.category),
                        contentDescription = null,
                        tint = InkMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    MonoLabel(
                        text = "${ByteFormatter.formatBytes(group.fileSizeBytes)} · ${group.category.labelIt}",
                        color = InkMuted
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "+${ByteFormatter.formatBytes(group.reclaimableBytes)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = ProtectedGreen
                )
                MonoLabel(text = "recuperabili", color = InkFaint)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Hairline()
        Spacer(modifier = Modifier.height(4.dp))

        group.files.forEachIndexed { fileIndex, file ->
            DuplicateFileRow(
                fileItem = file,
                onToggle = { onToggleFileSelection(group.groupId, file.id) }
            )
            if (fileIndex != group.files.lastIndex) {
                Hairline(color = PaperTint)
            }
        }

        Hairline()
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonoLabel(
                text = "Verifica 1 KB → 4 KB · ${group.fourKbBlocksChecked} blocchi",
                color = InkFaint
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(16.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(PaperTint)
                    .padding(12.dp)
            ) {
                Text(
                    text = group.technicalVerdict,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "testata 1 KB  ${group.files.firstOrNull()?.first1KbHashHex.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "firma finale  ${group.signatureHex}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DuplicateFileRow(
    fileItem: ScannedFileItem,
    onToggle: () -> Unit
) {
    val selected = fileItem.isSelectedForDeletion
    val accent = if (selected) AlertCoral else ProtectedGreen
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yy · HH:mm", Locale.ITALIAN) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(vertical = 11.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        PaperCheck(checked = selected, accent = accent)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fileItem.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (selected) {
                    CoralTag(text = "elimina")
                } else {
                    PillTag(
                        text = "originale",
                        background = SignalLime,
                        foreground = Ink,
                        icon = Icons.Default.Shield
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = fileItem.folderPath,
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(dateFormatter.format(Date(fileItem.lastModified)))
                    if (fileItem.originReason.isNotBlank()) {
                        append("  ·  ")
                        append(fileItem.originReason)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = InkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Empty state                                                        */
/* ------------------------------------------------------------------ */

@Composable
private fun EmptyDuplicatesState(
    onAddSafFolderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        HyperCard(
            shape = RoundedCornerShape(26.dp),
            contentPadding = PaddingValues(22.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(SignalLimeSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Nessun duplicato in questa vista",
                style = MaterialTheme.typography.headlineMedium,
                color = Ink
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tutti i file confrontati hanno una firma diversa, oppure il filtro attivo sta nascondendo i gruppi trovati. Puoi estendere la ricerca collegando un'altra cartella.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted
            )
            Spacer(modifier = Modifier.height(18.dp))
            OutlinePillButton(
                text = "Collega una cartella",
                icon = Icons.Default.FolderZip,
                onClick = onAddSafFolderClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("empty_state_add_folder_button")
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Dialogs                                                            */
/* ------------------------------------------------------------------ */

@Composable
private fun ConfirmDeleteDialog(
    count: Int,
    bytes: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PaperSurface,
        shape = RoundedCornerShape(26.dp),
        title = {
            Column {
                MonoLabel(text = "Conferma eliminazione", color = AlertCoral)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Eliminare $count copie?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Verranno rimossi definitivamente ${ByteFormatter.formatBytes(bytes)} di file ridondanti. Gli originali protetti restano al loro posto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AlertCoralSoft)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = AlertCoral,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Operazione non reversibile",
                        style = MaterialTheme.typography.titleSmall,
                        color = AlertCoral,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            InkButton(
                text = "Elimina",
                containerColor = AlertCoral,
                contentColor = Color.White,
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_delete_button")
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Annulla",
                    style = MaterialTheme.typography.labelLarge,
                    color = InkMuted
                )
            }
        }
    )
}

@Composable
private fun SelectionRuleDialog(
    currentRule: SelectionRule,
    onSelectRule: (SelectionRule) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PaperSurface,
        shape = RoundedCornerShape(26.dp),
        title = {
            Column {
                MonoLabel(text = "Criterio automatico", color = VioletNote)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Quale copia proteggere?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectionRule.entries.forEach { rule ->
                    val isSelected = rule == currentRule
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) VioletSoft else PaperTint)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) VioletNote.copy(alpha = 0.4f) else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onSelectRule(rule) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) VioletNote else PaperSurface)
                                .border(1.dp, HairlineStrong, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = rule.titleIt,
                                style = MaterialTheme.typography.titleSmall,
                                color = Ink
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = rule.descriptionIt,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Chiudi",
                    style = MaterialTheme.typography.labelLarge,
                    color = Ink
                )
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
