package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.DuplicateCleanerViewModel
import com.example.ui.components.MonoLabel
import com.example.ui.components.StatusDot
import com.example.ui.screens.DuplicatesReviewScreen
import com.example.ui.screens.HistoryAndCacheScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.ConsoleMuted
import com.example.ui.theme.Hairline
import com.example.ui.theme.HyperTypography
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PaperCanvas
import com.example.ui.theme.PaperSurface
import com.example.ui.theme.SignalLime
import com.example.ui.theme.VioletNote

enum class HyperCleanTab(
    val routeId: String,
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    SCANNER("scanner", R.string.nav_scanner, Icons.Filled.Radar, Icons.Outlined.Radar),
    DUPLICATES("duplicates", R.string.nav_duplicates, Icons.Filled.CopyAll, Icons.Outlined.CopyAll),
    HISTORY("history", R.string.nav_history, Icons.Filled.History, Icons.Outlined.History)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            MyApplicationTheme {
                HyperCleanApp()
            }
        }
    }
}

@Composable
fun HyperCleanApp(
    viewModel: DuplicateCleanerViewModel = viewModel(factory = DuplicateCleanerViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cachedCount by viewModel.cachedSignaturesCount.collectAsStateWithLifecycle()
    val cleanupHistory by viewModel.cleanupHistory.collectAsStateWithLifecycle()
    val totalBytesFreedAllTime by viewModel.totalBytesFreedAllTime.collectAsStateWithLifecycle()
    val linkedFolders by viewModel.linkedFolders.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(HyperCleanTab.SCANNER) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    BackHandler(enabled = currentTab != HyperCleanTab.SCANNER) {
        currentTab = HyperCleanTab.SCANNER
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            val folderLabel = treeUri.lastPathSegment?.substringAfterLast(':') ?: "Cartella dispositivo"
            viewModel.addSafFolder(treeUri, folderLabel)
        }
    }

    LaunchedEffect(uiState.statusBannerMessage) {
        val msg = uiState.statusBannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    Scaffold(
        containerColor = PaperCanvas,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PaperCanvas)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                BrandHeader(
                    isScanning = uiState.isScanning,
                    phasesLabel = if (uiState.isScanning) "live" else "idle"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    ) {
                        when (currentTab) {
                            HyperCleanTab.SCANNER -> ScannerScreen(
                                uiState = uiState,
                                cachedSignaturesCount = cachedCount,
                                totalBytesFreedAllTime = totalBytesFreedAllTime,
                                linkedFolders = linkedFolders,
                                onStartScan = { viewModel.startUltraFastScan() },
                                onCancelScan = { viewModel.cancelScan() },
                                onAddSafFolderClick = { folderPickerLauncher.launch(null) },
                                onRemoveSafFolder = { uri -> viewModel.removeSafFolder(uri) },
                                onNavigateToDuplicates = {
                                    currentTab = HyperCleanTab.DUPLICATES
                                }
                            )

                            HyperCleanTab.DUPLICATES -> DuplicatesReviewScreen(
                                uiState = uiState,
                                onSelectCategory = { viewModel.setCategoryFilter(it) },
                                onSelectRule = { viewModel.applySelectionRule(it) },
                                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                onToggleFileSelection = { groupId, fileId ->
                                    viewModel.toggleFileSelection(groupId, fileId)
                                },
                                onConfirmDeleteSelected = { viewModel.deleteSelectedDuplicates() },
                                onAddSafFolderClick = { folderPickerLauncher.launch(null) }
                            )

                            HyperCleanTab.HISTORY -> HistoryAndCacheScreen(
                                telemetry = uiState.telemetry,
                                cachedSignaturesCount = cachedCount,
                                cleanupHistory = cleanupHistory,
                                onClearHashCache = { viewModel.clearHashCache() }
                            )
                        }
                    }
                }
            }

            DockNavigation(
                currentTab = currentTab,
                selectedCount = uiState.totalSelectedFilesCount,
                onSelect = { currentTab = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp)
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 104.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Ink,
                    contentColor = PaperCanvas,
                    actionContentColor = SignalLime,
                    dismissActionContentColor = ConsoleMuted
                )
            }
        }
    }
}

@Composable
private fun BrandHeader(
    isScanning: Boolean,
    phasesLabel: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Ink),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "HC",
                style = HyperTypography.titleLarge,
                color = SignalLime
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = Ink
            )
            MonoLabel(
                text = "Deduplicatore · 1 KB → 4 KB",
                color = InkMuted
            )
        }

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .border(1.dp, Hairline, CircleShape)
                .background(PaperSurface)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(color = if (isScanning) SignalLime else InkMuted, size = 6)
            Spacer(modifier = Modifier.width(6.dp))
            MonoLabel(
                text = phasesLabel,
                color = if (isScanning) VioletNote else InkMuted
            )
        }
    }
}

@Composable
private fun DockNavigation(
    currentTab: HyperCleanTab,
    selectedCount: Int,
    onSelect: (HyperCleanTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .shadow(
                elevation = 20.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = Ink.copy(alpha = 0.16f),
                spotColor = Ink.copy(alpha = 0.16f)
            ),
        shape = CircleShape,
        color = PaperSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HyperCleanTab.entries.forEach { tab ->
                val selected = currentTab == tab
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) Ink else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(horizontal = if (selected) 16.dp else 14.dp, vertical = 11.dp)
                        .testTag("nav_tab_${tab.routeId}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = stringResource(tab.labelResId),
                        tint = if (selected) SignalLime else InkMuted,
                        modifier = Modifier.size(19.dp)
                    )
                    if (selected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(tab.labelResId),
                            style = MaterialTheme.typography.titleSmall,
                            color = PaperCanvas,
                            maxLines = 1
                        )
                    }
                    if (tab == HyperCleanTab.DUPLICATES && selectedCount > 0) {
                        Spacer(modifier = Modifier.width(7.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(AlertCoral)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$selectedCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
