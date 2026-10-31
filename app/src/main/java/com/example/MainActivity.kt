package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.DuplicateCleanerViewModel
import com.example.ui.screens.DuplicatesReviewScreen
import com.example.ui.screens.HistoryAndCacheScreen
import com.example.ui.screens.RadarScannerScreen
import com.example.ui.theme.ElevatedCardSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyanPrimary

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
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                HyperCleanApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
            val folderLabel = treeUri.lastPathSegment?.substringAfterLast(':') ?: "Cartella Dispositivo"
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
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NeonCyanPrimary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = NeonCyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Motore Progressivo 1 KB → 4 KB",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyanPrimary
                            )
                        }
                    }
                },
                actions = {
                    Surface(
                        color = ElevatedCardSurface,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = NeonCyanPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "1KB + 4KB ENGINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyanPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                HyperCleanTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        modifier = Modifier.testTag("nav_tab_${tab.routeId}"),
                        icon = {
                            if (tab == HyperCleanTab.DUPLICATES && uiState.totalSelectedFilesCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = NeonCyanPrimary) {
                                            Text("${uiState.totalSelectedFilesCount}")
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelResId)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.labelResId)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(tab.labelResId),
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyanPrimary,
                            selectedTextColor = NeonCyanPrimary,
                            indicatorColor = NeonCyanPrimary.copy(alpha = 0.16f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                when (currentTab) {
                    HyperCleanTab.SCANNER -> RadarScannerScreen(
                        uiState = uiState,
                        cachedSignaturesCount = cachedCount,
                        totalBytesFreedAllTime = totalBytesFreedAllTime,
                        linkedFolders = linkedFolders,
                        onStartScan = { viewModel.startUltraFastScan() },
                        onCancelScan = { viewModel.cancelScan() },
                        onAddSafFolderClick = { folderPickerLauncher.launch(null) },
                        onRemoveSafFolder = { uri -> viewModel.removeSafFolder(uri) },
                        onNavigateToDuplicates = { currentTab = HyperCleanTab.DUPLICATES }
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
}
