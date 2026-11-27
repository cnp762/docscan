package cam.docscan.ui.screens

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import cam.docscan.data.PageEntity
import cam.docscan.ui.components.DocCardSurface
import cam.docscan.ui.components.DocTopBar
import cam.docscan.ui.components.FilterMatrixBar
import cam.docscan.ui.components.InteractiveQuadOverlay
import cam.docscan.ui.components.LocalFirstTrustBanner
import cam.docscan.ui.components.QuotaChip
import cam.docscan.ui.state.CaptureMode
import cam.docscan.ui.state.DocScanAppState
import cam.docscan.ui.state.DocScanRoute
import cam.docscan.ui.state.DraftPage
import cam.docscan.ui.state.MainTab
import cam.docscan.ui.theme.DocScanColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeLibraryScreen(
    state: DocScanAppState,
    onOpenImport: () -> Unit,
) {
    val categories = listOf("All", "Receipts", "Contracts", "Invoices", "ID & Legal")
    val filteredDocs = remember(state.documents.toList(), state.selectedCategoryFilter) {
        if (state.selectedCategoryFilter == "All") {
            state.documents.toList()
        } else {
            state.documents.filter {
                it.docType.equals(state.selectedCategoryFilter, ignoreCase = true) ||
                    it.tags.any { t -> t.contains(state.selectedCategoryFilter, ignoreCase = true) }
            }
        }
    }

    Scaffold(
        containerColor = DocScanColors.SurfaceBase,
        bottomBar = {
            FloatingBottomNavBar(
                activeTab = state.activeTab,
                onSelectTab = { tab ->
                    state.activeTab = tab
                    when (tab) {
                        MainTab.LIBRARY -> state.currentRoute = DocScanRoute.HOME_LIBRARY
                        MainTab.SCAN -> state.startNewCaptureSession()
                        MainTab.SEARCH -> state.currentRoute = DocScanRoute.SEARCH_FILTER
                        MainTab.IMPORT -> onOpenImport()
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "DocScan",
                            style = MaterialTheme.typography.headlineMedium,
                            color = DocScanColors.TextPrimary,
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DocScanColors.SurfaceOverlay,
                        ) {
                            Text(
                                text = "v1.0.0",
                                style = MaterialTheme.typography.labelSmall,
                                color = DocScanColors.TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QuotaChip(
                            usedToday = state.quotaUsedToday,
                            dailyLimit = DocScanAppState.FREE_DAILY_QUOTA,
                            isPro = state.isPro,
                            onClick = { state.currentRoute = DocScanRoute.PAYWALL },
                        )
                        IconButton(
                            onClick = { state.currentRoute = DocScanRoute.SETTINGS },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(DocScanColors.SurfaceCard),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Settings",
                                tint = DocScanColors.TextPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            if (state.statusBanner != null) {
                item {
                    Surface(
                        onClick = { state.statusBanner = null },
                        shape = RoundedCornerShape(12.dp),
                        color = DocScanColors.ScanGlow.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, DocScanColors.ScanGlow.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = state.statusBanner.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = DocScanColors.ScanGlow,
                            )
                            Icon(Icons.Rounded.Close, contentDescription = null, tint = DocScanColors.ScanGlow, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            item {
                Surface(
                    onClick = { state.currentRoute = DocScanRoute.SEARCH_FILTER },
                    shape = RoundedCornerShape(14.dp),
                    color = DocScanColors.SurfaceCard,
                    border = BorderStroke(1.dp, DocScanColors.BorderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = DocScanColors.TextSecondary, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Search OCR text, titles, or tags...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DocScanColors.TextMuted,
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categories.forEach { cat ->
                        val selected = state.selectedCategoryFilter == cat
                        FilterChip(
                            selected = selected,
                            onClick = { state.selectedCategoryFilter = cat },
                            label = {
                                val label = if (cat == "All") "All (${state.documents.size})" else cat
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DocScanColors.ScanGlow.copy(alpha = 0.18f),
                                selectedLabelColor = DocScanColors.ScanGlow,
                                containerColor = DocScanColors.SurfaceCard,
                                labelColor = DocScanColors.TextSecondary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = DocScanColors.BorderSubtle,
                                selectedBorderColor = DocScanColors.ScanGlow.copy(alpha = 0.5f),
                            ),
                        )
                    }
                }
            }

            // 7-day Suggested banner & horizontal cards
            item {
                DocCardSurface(
                    borderColor = DocScanColors.ScanGlow.copy(alpha = 0.28f),
                    onClick = { state.currentRoute = DocScanRoute.LIBRARY_SUGGEST },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = DocScanColors.ScanGlow, modifier = Modifier.size(18.dp))
                            Text("Suggested · 7 days", style = MaterialTheme.typography.titleMedium, color = DocScanColors.TextPrimary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("See all", style = MaterialTheme.typography.labelMedium, color = DocScanColors.ScanGlow)
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = DocScanColors.ScanGlow, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Detected ${state.suggestCandidates.count { it.isDocument }} unfiled paper items from your camera roll",
                        style = MaterialTheme.typography.bodySmall,
                        color = DocScanColors.TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.suggestCandidates.filter { it.isDocument }) { item ->
                            Surface(
                                onClick = { state.currentRoute = DocScanRoute.LIBRARY_SUGGEST },
                                shape = RoundedCornerShape(12.dp),
                                color = DocScanColors.SurfaceRaised,
                                border = BorderStroke(1.dp, DocScanColors.BorderSubtle),
                                modifier = Modifier.width(170.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(item.category, style = MaterialTheme.typography.labelSmall, color = DocScanColors.Tertiary)
                                    Text(
                                        item.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = DocScanColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = DocScanColors.TextSecondary, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Recent Documents (${filteredDocs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = DocScanColors.TextPrimary,
                    )
                    Text(
                        text = "Newest first",
                        style = MaterialTheme.typography.labelMedium,
                        color = DocScanColors.TextSecondary,
                    )
                }
            }

            if (filteredDocs.isEmpty()) {
                item {
                    DocCardSurface {
                        Text("No documents yet", style = MaterialTheme.typography.titleMedium, color = DocScanColors.TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap Scan to capture a document or Import to pick existing photos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DocScanColors.TextSecondary,
                        )
                    }
                }
            } else {
                items(filteredDocs, key = { it.id }) { doc ->
                    DocumentListItemCard(
                        doc = doc,
                        onClick = { state.openDocument(doc.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentListItemCard(
    doc: PageEntity,
    onClick: () -> Unit,
) {
    val dateLabel = remember(doc.createdAt) {
        SimpleDateFormat("MMM d", Locale.US).format(Date(doc.createdAt))
    }
    DocCardSurface(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 64.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DocScanColors.SurfaceRaised)
                    .border(1.dp, DocScanColors.BorderSubtle, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = null,
                    tint = DocScanColors.ScanGlow,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = doc.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = DocScanColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${doc.docType ?: "Document"} · $dateLabel · OCR ready",
                    style = MaterialTheme.typography.bodySmall,
                    color = DocScanColors.TextSecondary,
                    maxLines = 1,
                )
                if (doc.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        doc.tags.take(3).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = DocScanColors.SurfaceOverlay,
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DocScanColors.Tertiary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
            Icon(Icons.Rounded.MoreVert, contentDescription = "Options", tint = DocScanColors.TextSecondary)
        }
    }
}

@Composable
private fun FloatingBottomNavBar(
    activeTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        color = DocScanColors.SurfaceOverlay.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, DocScanColors.BorderMedium),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTabItem(
                icon = Icons.Rounded.FolderOpen,
                label = "Library",
                selected = activeTab == MainTab.LIBRARY,
                onClick = { onSelectTab(MainTab.LIBRARY) },
            )
            NavTabItem(
                icon = Icons.Rounded.DocumentScanner,
                label = "Scan",
                selected = activeTab == MainTab.SCAN,
                highlight = true,
                onClick = { onSelectTab(MainTab.SCAN) },
            )
            NavTabItem(
                icon = Icons.Rounded.Search,
                label = "Search",
                selected = activeTab == MainTab.SEARCH,
                onClick = { onSelectTab(MainTab.SEARCH) },
            )
            NavTabItem(
                icon = Icons.Rounded.PhotoLibrary,
                label = "Import",
                selected = activeTab == MainTab.IMPORT,
                onClick = { onSelectTab(MainTab.IMPORT) },
            )
        }
    }
}

@Composable
private fun NavTabItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        highlight -> DocScanColors.ScanGlow
        selected -> DocScanColors.Primary
        else -> DocScanColors.TextSecondary
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
fun LibrarySuggestScreen(
    state: DocScanAppState,
    onBack: () -> Unit,
) {
    val selectedCount = state.suggestCandidates.count { it.selected }
    Scaffold(
        containerColor = DocScanColors.SurfaceBase,
        topBar = {
            DocTopBar(title = "Library Suggest", subtitle = "Last 7 days · On-device", onBack = onBack)
        },
        bottomBar = {
            Surface(
                color = DocScanColors.SurfaceRaised,
                border = BorderStroke(1.dp, DocScanColors.BorderSubtle),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "$selectedCount document(s) selected",
                            style = MaterialTheme.typography.labelLarge,
                            color = DocScanColors.TextPrimary,
                        )
                        Text(
                            text = if (state.isPro) "Pro · Unlimited" else "Uses $selectedCount/${DocScanAppState.FREE_DAILY_QUOTA} daily quota",
                            style = MaterialTheme.typography.labelSmall,
                            color = DocScanColors.ScanGlow,
                        )
                    }
                    Button(
                        onClick = { state.importSelectedSuggestions() },
                        enabled = selectedCount > 0,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DocScanColors.ScanGlow,
                            contentColor = DocScanColors.OnPrimary,
                        ),
                    ) {
                        Text("Import & Scan ($selectedCount)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(8.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                    }
                }
            }
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LocalFirstTrustBanner("DocScan analyzed recent photos on-device. Select photos to import cleanly.")
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            for (i in state.suggestCandidates.indices) {
                                val item = state.suggestCandidates[i]
                                state.suggestCandidates[i] = item.copy(selected = item.isDocument)
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = DocScanColors.ScanGlow, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Select all docs (${state.suggestCandidates.count { it.isDocument }})", color = DocScanColors.ScanGlow)
                    }
                    Text("3 suggested", style = MaterialTheme.typography.labelMedium, color = DocScanColors.TextSecondary)
                }
            }

            itemsIndexed(state.suggestCandidates, key = { _, it -> it.id }) { idx, item ->
                DocCardSurface(
                    borderColor = if (item.selected) DocScanColors.ScanGlow.copy(alpha = 0.5f) else DocScanColors.BorderSubtle,
                    onClick = { state.suggestCandidates[idx] = item.copy(selected = !item.selected) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = if (item.isDocument) Icons.Rounded.ReceiptLong else Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            tint = if (item.isDocument) DocScanColors.ScanGlow else DocScanColors.TextMuted,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(item.category, style = MaterialTheme.typography.labelSmall, color = DocScanColors.Tertiary)
                                Text("${item.confidencePercent}%", style = MaterialTheme.typography.labelSmall, color = DocScanColors.TextSecondary)
                            }
                            Text(item.title, style = MaterialTheme.typography.titleMedium, color = DocScanColors.TextPrimary)
                            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = DocScanColors.TextSecondary)
                        }
                        Checkbox(
                            checked = item.selected,
                            onCheckedChange = { checked -> state.suggestCandidates[idx] = item.copy(selected = checked) },
                            colors = CheckboxDefaults.colors(checkedColor = DocScanColors.ScanGlow, checkmarkColor = DocScanColors.OnPrimary),
                        )
                    }
                }
            }

            item {
                DocCardSurface(onClick = { state.startNewCaptureSession() }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("Need a fresh capture?", style = MaterialTheme.typography.titleMedium, color = DocScanColors.TextPrimary)
                            Text("Open precision viewfinder with quad-auto warp.", style = MaterialTheme.typography.bodySmall, color = DocScanColors.TextSecondary)
                        }
                        OutlinedButton(onClick = { state.startNewCaptureSession() }) {
                            Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = DocScanColors.ScanGlow, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Camera", color = DocScanColors.ScanGlow)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraCaptureScreen(
    state: DocScanAppState,
    onOpenGallery: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var corners by remember { mutableStateOf(DraftPage.defaultQuad()) }
    var flashOn by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // CameraX PreviewView when camera permission is granted
        if (state.cameraPermissionGranted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        runCatching {
                            val provider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                            )
                        }
                    }, ContextCompat.getMainExecutor(context))
                },
            )
            DisposableEffect(lifecycleOwner) {
                onDispose {
                    runCatching {
                        ProcessCameraProvider.getInstance(context).get().unbindAll()
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DocScanColors.SurfaceBase),
                contentAlignment = Alignment.Center,
            ) {
                Text("Camera preview ready after permission", color = DocScanColors.TextSecondary)
            }
        }

        // 40% darkened mask + interactive QuadOverlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DocScanColors.SurfaceBase.copy(alpha = 0.32f)),
        )
        InteractiveQuadOverlay(
            corners = corners,
            onUpdateCorner = { idx, offset ->
                corners = corners.toMutableList().also { it[idx] = offset }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp, bottom = 180.dp),
        )

        // Top bar + Guiding HUD pill
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.clip(CircleShape).background(DocScanColors.SurfaceRaised.copy(alpha = 0.85f)),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = DocScanColors.TextPrimary)
                }
                QuotaChip(
                    usedToday = state.quotaUsedToday,
                    dailyLimit = DocScanAppState.FREE_DAILY_QUOTA,
                    isPro = state.isPro,
                    onClick = { state.currentRoute = DocScanRoute.PAYWALL },
                )
                IconButton(
                    onClick = { flashOn = !flashOn },
                    modifier = Modifier.clip(CircleShape).background(DocScanColors.SurfaceRaised.copy(alpha = 0.85f)),
                ) {
                    Icon(
                        Icons.Rounded.FlashOn,
                        contentDescription = "Flash",
                        tint = if (flashOn) DocScanColors.WarningAmber else DocScanColors.TextPrimary,
                    )
                }
            }

            Surface(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                shape = CircleShape,
                color = DocScanColors.SurfaceRaised.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, DocScanColors.ScanGlow.copy(alpha = 0.45f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Verified, contentDescription = null, tint = DocScanColors.ScanGlow, modifier = Modifier.size(16.dp))
                    Text("Hold steady · All corners detected", style = MaterialTheme.typography.labelMedium, color = DocScanColors.TextPrimary)
                }
            }
        }

        // Bottom capture deck
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(DocScanColors.SurfaceRaised.copy(alpha = 0.92f))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CaptureMode.entries.forEach { mode ->
                    val selected = state.captureMode == mode
                    Surface(
                        onClick = { state.captureMode = mode },
                        shape = CircleShape,
                        color = if (selected) DocScanColors.ScanGlow.copy(alpha = 0.2f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (selected) DocScanColors.ScanGlow else DocScanColors.BorderSubtle),
                    ) {
                        Text(
                            text = if (mode == CaptureMode.SINGLE) "Single" else "Batch",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) DocScanColors.ScanGlow else DocScanColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onOpenGallery,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DocScanColors.SurfaceOverlay),
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Import", tint = DocScanColors.TextPrimary)
                }

                // Triple-concentric shutter button
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .border(2.dp, DocScanColors.ScanGlow, CircleShape)
                        .clickable {
                            state.addCapturedPage("Scan ${state.draftPages.size + 1}")
                            if (state.captureMode == CaptureMode.SINGLE) {
                                state.currentRoute = DocScanRoute.REVIEW_EDIT
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                }

                Surface(
                    onClick = {
                        if (state.draftPages.isNotEmpty()) {
                            state.currentRoute = DocScanRoute.REVIEW_EDIT
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (state.draftPages.isNotEmpty()) DocScanColors.ScanGlow else DocScanColors.SurfaceOverlay,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${state.draftPages.size} pgs",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (state.draftPages.isNotEmpty()) DocScanColors.OnPrimary else DocScanColors.TextSecondary,
                        )
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Review",
                            tint = if (state.draftPages.isNotEmpty()) DocScanColors.OnPrimary else DocScanColors.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewEditScreen(
    state: DocScanAppState,
    onAddMore: () -> Unit,
    onDiscard: () -> Unit,
) {
    val activeIdx = state.activeDraftIndex.coerceIn(0, (state.draftPages.size - 1).coerceAtLeast(0))
    val activePage = state.draftPages.getOrNull(activeIdx)

    Scaffold(
        containerColor = DocScanColors.SurfaceBase,
        topBar = {
            DocTopBar(
                title = "Perspective Crop",
                subtitle = if (activePage != null) "Page ${activeIdx + 1} of ${state.draftPages.size} · Quad Active" else null,
                onBack = onDiscard,
                trailing = {
                    QuotaChip(
                        usedToday = state.quotaUsedToday,
                        dailyLimit = DocScanAppState.FREE_DAILY_QUOTA,
                        isPro = state.isPro,
                        onClick = { state.currentRoute = DocScanRoute.PAYWALL },
                    )
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DocScanColors.SurfaceRaised)
                    .border(1.dp, DocScanColors.BorderSubtle)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (activePage != null) {
                    FilterMatrixBar(
                        selected = activePage.enhanceMode,
                        onSelect = { mode ->
                            state.draftPages[activeIdx] = activePage.copy(enhanceMode = mode)
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onDiscard,
                        modifier = Modifier.height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, DocScanColors.DangerCrimson.copy(alpha = 0.5f)),
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = DocScanColors.DangerCrimson, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Discard", color = DocScanColors.DangerCrimson)
                    }
                    Button(
                        onClick = { state.commitDraftPages() },
                        enabled = state.draftPages.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DocScanColors.ScanGlow,
                            contentColor = DocScanColors.OnPrimary,
                        ),
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        val label = if (state.editingDocumentId != null) {
                            "Save Changes (Free)"
                        } else {
                            "Save ${state.draftPages.size} Page(s)"
                        }
                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = DocScanColors.TextMuted, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("No cloud upload. Digitized entirely on device.", style = MaterialTheme.typography.bodySmall, color = DocScanColors.TextMuted)
                }
            }
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Interactive Filmstrip
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.draftPages.forEachIndexed { idx, page ->
                    val isSelected = idx == activeIdx
                    Box(
                        modifier = Modifier
                            .size(width = 58.dp, height = 74.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DocScanColors.SurfaceCard)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) DocScanColors.ScanGlow else DocScanColors.BorderSubtle,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable { state.activeDraftIndex = idx }
                            .padding(6.dp),
                    ) {
                        Text(
                            text = "p${idx + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) DocScanColors.ScanGlow else DocScanColors.TextSecondary,
                            modifier = Modifier.align(Alignment.TopStart),
                        )
                        Text(
                            text = if (isSelected) "Edit" else "Ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = DocScanColors.TextMuted,
                            modifier = Modifier.align(Alignment.BottomStart),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(width = 58.dp, height = 74.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DocScanColors.SurfaceRaised)
                        .border(1.dp, DocScanColors.BorderMedium, RoundedCornerShape(10.dp))
                        .clickable(onClick = onAddMore),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.AddAPhoto, contentDescription = "Add", tint = DocScanColors.ScanGlow, modifier = Modifier.size(18.dp))
                        Text("+ Add", style = MaterialTheme.typography.labelSmall, color = DocScanColors.ScanGlow)
                    }
                }
            }

            // Perspective crop canvas with InteractiveQuadOverlay
            if (activePage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(330.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DocScanColors.SurfaceRaised)
                        .border(1.dp, DocScanColors.BorderSubtle, RoundedCornerShape(16.dp)),
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.DocumentScanner, contentDescription = null, tint = DocScanColors.TextMuted, modifier = Modifier.size(44.dp))
                        Text(activePage.title, style = MaterialTheme.typography.titleMedium, color = DocScanColors.TextPrimary)
                        Text(
                            "Touch & drag any corner for 4× loupe · Rotated ${activePage.rotationDegrees}°",
                            style = MaterialTheme.typography.bodySmall,
                            color = DocScanColors.TextSecondary,
                        )
                    }
                    InteractiveQuadOverlay(
                        corners = activePage.corners,
                        onUpdateCorner = { cornerIdx, offset ->
                            val updated = activePage.corners.toMutableList().also { it[cornerIdx] = offset }
                            state.draftPages[activeIdx] = activePage.copy(corners = updated)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Quick geometry controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            state.draftPages[activeIdx] = activePage.copy(
                                rotationDegrees = (activePage.rotationDegrees + 90) % 360,
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Rounded.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Rotate 90°", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = {
                            state.draftPages[activeIdx] = activePage.copy(corners = DraftPage.defaultQuad())
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Reset Quad", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = {
                            state.draftPages[activeIdx] = activePage.copy(
                                corners = listOf(
                                    Offset(0.03f, 0.03f),
                                    Offset(0.97f, 0.03f),
                                    Offset(0.97f, 0.97f),
                                    Offset(0.03f, 0.97f),
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Rounded.CropFree, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Full Bleed", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
