package cam.docscan.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddToDrive
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cam.docscan.ui.components.DocCardSurface
import cam.docscan.ui.components.DocPageThumbnail
import cam.docscan.ui.components.DocTopBar
import cam.docscan.ui.components.FreeAdBannerSlot
import cam.docscan.ui.components.LocalFirstTrustBanner
import cam.docscan.ui.components.OcrInspectionCard
import cam.docscan.ui.state.DocScanAppState
import cam.docscan.ui.state.DocScanRoute
import cam.docscan.ui.state.ExportFormat
import cam.docscan.ui.state.ExportQuality
import cam.docscan.ui.state.ProPlan
import cam.docscan.ui.theme.DocScanColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DocumentDetailScreen(
    state: DocScanAppState,
    onBack: () -> Unit,
) {
    val doc = state.selectedDocument()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var newTagInput by remember { mutableStateOf("") }

    if (doc == null) {
        Scaffold(containerColor = DocScanColors.SurfaceBase, topBar = { DocTopBar("Document", onBack = onBack) }) { inner ->
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Text("Document not found", color = DocScanColors.TextSecondary)
            }
        }
        return
    }

    val dateLabel = remember(doc.createdAt) {
        SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.US).format(Date(doc.createdAt))
    }

    Scaffold(
        containerColor = DocScanColors.SurfaceBase,
        topBar = {
            DocTopBar(
                title = doc.name,
                subtitle = "$dateLabel · ${doc.docType ?: "Document"}",
                onBack = onBack,
            )
        },
        bottomBar = { FreeAdBannerSlot(isPro = state.isPro) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Document preview surface
            DocCardSurface(containerColor = DocScanColors.SurfaceRaised) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DocScanColors.SurfaceCard)
                        .border(1.dp, DocScanColors.BorderSubtle, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    DocPageThumbnail(
                        imagePath = doc.imagePath,
                        modifier = Modifier.fillMaxSize(),
                        iconSize = 42.dp,
                        maxDimensionPx = 768,
                    )
                    if (doc.imagePath.isBlank()) {
                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(doc.name, style = MaterialTheme.typography.titleMedium, color = DocScanColors.TextPrimary)
                            Text("Ready for export · Private on this phone", style = MaterialTheme.typography.bodySmall, color = DocScanColors.TextSecondary)
                        }
                    }
                }
            }

            // Summary card
            DocCardSurface {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = DocScanColors.ScanGlow, modifier = Modifier.size(16.dp))
                    Text("Document Summary", style = MaterialTheme.typography.labelLarge, color = DocScanColors.TextPrimary)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = doc.summary ?: "Extractive summary generated on-device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DocScanColors.TextSecondary,
                )
            }

            // OCR Inspection Card
            OcrInspectionCard(
                text = doc.ocrText.orEmpty(),
                languageLabel = "Language: ${(doc.ocrLang ?: "en").uppercase()} · Confidence ${((doc.ocrLangConfidence ?: 0.99f) * 100).toInt()}%",
                copied = copied,
                onCopy = {
                    clipboard.setText(AnnotatedString(doc.ocrText.orEmpty()))
                    copied = true
                },
            )

            // Tags
            DocCardSurface {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Categorization & Tags", style = MaterialTheme.typography.labelLarge, color = DocScanColors.TextPrimary)
                    TextButton(onClick = { showAddTagDialog = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = DocScanColors.ScanGlow, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Add tag", color = DocScanColors.ScanGlow)
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    doc.tags.forEach { tag ->
                        Surface(
                            onClick = { state.removeTagFromSelected(tag) },
                            shape = RoundedCornerShape(8.dp),
                            color = DocScanColors.SurfaceOverlay,
                            border = BorderStroke(1.dp, DocScanColors.BorderSubtle),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("#$tag", style = MaterialTheme.typography.labelMedium, color = DocScanColors.Tertiary)
                                Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = DocScanColors.TextMuted, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                }
            }

            // Document Actions (Android: Backup Drive & Photos, Share only)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailActionButton(
                    icon = Icons.Rounded.Tune,
                    label = "Edit Scan",
                    sub = "No quota",
                    onClick = { state.editExistingDocument(doc) },
                    modifier = Modifier.weight(1f),
                )
                DetailActionButton(
                    icon = Icons.Rounded.Description,
                    label = "Export",
                    sub = "PDF · DOCX",
                    onClick = { state.currentRoute = DocScanRoute.EXPORT_SHARE },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailActionButton(
                    icon = Icons.Rounded.CloudUpload,
                    label = "Backup",
                    sub = "Drive & Photos",
                    onClick = { state.currentRoute = DocScanRoute.BACKUP },
                    modifier = Modifier.weight(1f),
                )
                DetailActionButton(
                    icon = Icons.Rounded.Share,
                    label = "Share",
                    sub = "System sheet",
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, doc.name)
                            putExtra(Intent.EXTRA_TEXT, doc.ocrText ?: doc.name)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share ${doc.name}"))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showAddTagDialog) {
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            containerColor = DocScanColors.SurfaceCard,
            title = { Text("Add Tag", color = DocScanColors.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newTagInput,
                    onValueChange = { newTagInput = it },
                    placeholder = { Text("e.g. tax 2025") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.addTagToSelected(newTagInput)
                        newTagInput = ""
                        showAddTagDialog = false
                    },
                ) { Text("Add", color = DocScanColors.ScanGlow) }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) { Text("Cancel", color = DocScanColors.TextSecondary) }
            },
        )
    }
}

@Composable
private fun DetailActionButton(
    icon: ImageVector,
    label: String,
    sub: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DocCardSurface(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = DocScanColors.ScanGlow, modifier = Modifier.size(20.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge, color = DocScanColors.TextPrimary)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = DocScanColors.TextSecondary)
            }
        }
    }
}

@Composable
fun SearchFilterScreen(
    state: DocScanAppState,
    onBack: () -> Unit,
) {
    val results = remember(state.documents.toList(), state.searchQuery, state.searchOnlyTagged) {
        val q = state.searchQuery.trim().lowercase()
        state.documents.filter { doc ->
            val tagMatch = !state.searchOnlyTagged || doc.tags.isNotEmpty()
            val queryMatch = q.isEmpty() ||
                doc.name.lowercase().contains(q) ||
                doc.ocrText?.lowercase()?.contains(q) == true ||
                doc.tags.any { it.lowercase().contains(q) }
            tagMatch && queryMatch
        }
    }

    Scaffold(
        containerColor = DocScanColors.SurfaceBase,
        topBar = { DocTopBar(title = "Search", onBack = onBack) },
        bottomBar = { FreeAdBannerSlot(isPro = state.isPro) },
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { state.searchQuery = it },
                placeholder = { Text("Search OCR text, invoices, tags...", color = DocScanColors.TextMuted) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = DocScanColors.ScanGlow) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { state.searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = DocScanColors.TextSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DocScanColors.ScanGlow,
                    unfocusedBorderColor = DocScanColors.BorderSubtle,
                    focusedTextColor = DocScanColors.TextPrimary,
                    unfocusedTextColor = DocScanColors.TextPrimary,
                    focusedContainerColor = DocScanColors.SurfaceCard,
                    unfocusedContainerColor = DocScanColors.SurfaceCard,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.searchOnlyTagged,
                    onClick = { state.searchOnlyTagged = !state.searchOnlyTagged },
                    label = { Text("Has Tag") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DocScanColors.ScanGlow.copy(alpha = 0.18f),
                        selectedLabelColor = DocScanColors.ScanGlow,
                    ),
                )
                FilterChip(
                    selected = false,
                    onClick = {},
                    label = { Text("Newest first") },
                )
            }

            Text("${results.size} result(s)", style = MaterialTheme.typography.labelMedium, color = DocScanColors.TextSecondary)

            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(results, key = { it.id }) { doc ->
                    DocCardSurface(onClick = { state.openDocument(doc.id) }) {
                        Text(doc.name, style = MaterialTheme.typography.titleMedium, color = DocScanColors.TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = doc.ocrText?.replace("\n", " · ").orEmpty(),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = DocScanColors.Tertiary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExportShareScreen(
    state: DocScanAppState,
    onBack: () -> Unit,
) {
    val doc = state.selectedDocument()
    val context = LocalContext.current
    var pageSelection by remember { mutableStateOf("All pages") }

    Scaffold(
        containerColor = DocScanColors.SurfaceBase,
        topBar = {
            DocTopBar(
                title = "Export & Share",
                subtitle = doc?.name ?: "Document",
                onBack = onBack,
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DocScanColors.SurfaceRaised)
                    .border(1.dp, DocScanColors.BorderSubtle)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        state.exportSelectedDocument(context)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DocScanColors.ScanGlow,
                        contentColor = DocScanColors.OnPrimary,
                    ),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Share & Export ${state.exportFormat.name}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { state.printSelectedDocument(context) },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.Print, contentDescription = null, tint = DocScanColors.TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Print Document", color = DocScanColors.TextPrimary)
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
            Text("Export Format", style = MaterialTheme.typography.labelLarge, color = DocScanColors.TextPrimary)
            ExportFormat.entries.forEach { fmt ->
                val selected = state.exportFormat == fmt
                DocCardSurface(
                    borderColor = if (selected) DocScanColors.ScanGlow else DocScanColors.BorderSubtle,
                    onClick = { state.exportFormat = fmt },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(fmt.label, style = MaterialTheme.typography.titleMedium, color = DocScanColors.TextPrimary)
                            Text(fmt.subtitle, style = MaterialTheme.typography.bodySmall, color = DocScanColors.TextSecondary)
                        }
                        if (selected) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = DocScanColors.ScanGlow)
                        }
                    }
                }
            }

            Text("Page Selection", style = MaterialTheme.typography.labelLarge, color = DocScanColors.TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All pages", "Current page", "Custom").forEach { opt ->
                    FilterChip(
                        selected = pageSelection == opt,
                        onClick = { pageSelection = opt },
                        label = { Text(opt) },
                    )
                }
            }

            Text("Output Quality", style = MaterialTheme.typography.labelLarge, color = DocScanColors.TextPrimary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportQuality.entries.forEach { q ->
                    val selected = state.exportQuality == q
                    DocCardSurface(
                        modifier = Modifier.weight(1f),
                        borderColor = if (selected) DocScanColors.ScanGlow else DocScanColors.BorderSubtle,
                        onClick = { state.exportQuality = q },
                    ) {
                        Text(q.label, style = MaterialTheme.typography.labelLarge, color = if (selected) DocScanColors.ScanGlow else DocScanColors.TextPrimary)
                        Text(q.estSize, style = MaterialTheme.typography.labelSmall, color = DocScanColors.TextSecondary)
                    }
                }
            }

            // Password Protection (in scope)
            DocCardSurface(
                borderColor = if (state.exportEncryptPassword) DocScanColors.ScanGlow.copy(alpha = 0.5f) else DocScanColors.BorderSubtle,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = DocScanColors.ScanGlow)
                        Column {
                            Text("Encrypt with Password", style = MaterialTheme.typography.titleMedium, color = DocScanColors.TextPrimary)
                            Text("Optional password · Stored only on this device", style = MaterialTheme.typography.bodySmall, color = DocScanColors.TextSecondary)
                        }
                    }
                    Switch(
                        checked = state.exportEncryptPassword,
                        onCheckedChange = { state.exportEncryptPassword = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = DocScanColors.OnPrimary, checkedTrackColor = DocScanColors.ScanGlow),
                    )
                }
                if (state.exportEncryptPassword) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.exportPassword,
                        onValueChange = { state.exportPassword = it },
                        label = { Text("Document password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
fun BackupScreen(
    state: DocScanAppState,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = DocScanColors.SurfaceBase,
        topBar = {
            DocTopBar(
                title = "Backup & Sync",
                subtitle = state.lastSyncMessage,
                onBack = onBack,
                trailing = {
                    TextButton(onClick = { state.lastSyncMessage = "Images synced just now" }) {
                        Icon(Icons.Rounded.Sync, contentDescription = null, tint = DocScanColors.ScanGlow, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Sync All", color = DocScanColors.ScanGlow)
                    }
                },
            )
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
            LocalFirstTrustBanner("Backup sends page images only to accounts you connect. We never send your scans to DocScan.")

            Text("Connected Clouds", style = MaterialTheme.typography.labelLarge, color = DocScanColors.TextPrimary)

            // Android: Google Drive & Google Photos only (iCloud Drive hidden on Android)
            DocCardSurface(
                borderColor = if (state.googleDriveConnected) DocScanColors.ScanGlow.copy(alpha = 0.45f) else DocScanColors.BorderSubtle,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.AddToDrive, contentDescription = null, tint = DocScanColors.ScanGlow)
                        Column {
                            Text("Google Drive", style = MaterialTheme.typography.titleMedium, color = DocScanColors.TextPrimary)
                            Text(
                                if (state.googleDriveConnected) "Connected · /DocScan/Images/" else "Not connected · Images only",
                                style = MaterialTheme.typography.bodySmall,
                                color = DocScanColors.TextSecondary,
                            )
                        }
                    }
                    Switch(
                        checked = state.googleDriveConnected,
                        onCheckedChange = { state.googleDriveConnected = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = DocScanColors.OnPrimary, checkedTrackColor = DocScanColors.ScanGlow),
                    )
                }
            }

            DocCardSurface(
                borderColor = if (state.googlePhotosConnected) DocScanColors.ScanGlow.copy(alpha = 0.45f) else DocScanColors.BorderSubtle,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = DocScanColors.Tertiary)
                        Column {
                            Text("Google Photos", style = MaterialTheme.typography.titleMedium, color = DocScanColors.TextPrimary)
                            Text(
                                if (state.googlePhotosConnected) "Connected · ‘DocScan’ album (JPEGs)" else "Auto-create dedicated ‘DocScan’ album (JPEGs only)",
                                style = MaterialTheme.typography.bodySmall,
                                color = DocScanColors.TextSecondary,
                            )
                        }
                    }
                    Switch(
                        checked = state.googlePhotosConnected,
                        onCheckedChange = { state.googlePhotosConnected = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = DocScanColors.OnPrimary, checkedTrackColor = DocScanColors.ScanGlow),
                    )
                }
            }

            Text("Backup Scope & Network", style = MaterialTheme.typography.labelLarge, color = DocScanColors.TextPrimary)
            DocCardSurface {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { state.backupEntireLibrary = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Entire document library (Images)", style = MaterialTheme.typography.titleMedium, color = DocScanColors.TextPrimary)
                        Text("Backs up all scanned page images", style = MaterialTheme.typography.bodySmall, color = DocScanColors.TextSecondary)
                    }
                    if (state.backupEntireLibrary) Icon(Icons.Rounded.Check, contentDescription = null, tint = DocScanColors.ScanGlow)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { state.backupEntireLibrary = false },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Starred & tagged images only", style = MaterialTheme.typography.titleMedium, color = DocScanColors.TextPrimary)
                        Text("Preserves storage quota for curated documents", style = MaterialTheme.typography.bodySmall, color = DocScanColors.TextSecondary)
                    }
                    if (!state.backupEntireLibrary) Icon(Icons.Rounded.Check, contentDescription = null, tint = DocScanColors.ScanGlow)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Wifi, contentDescription = null, tint = DocScanColors.ScanGlow, modifier = Modifier.size(18.dp))
                        Text("Sync over Wi-Fi only", style = MaterialTheme.typography.bodyMedium, color = DocScanColors.TextPrimary)
                    }
                    Switch(
                        checked = state.backupWifiOnly,
                        onCheckedChange = { state.backupWifiOnly = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = DocScanColors.OnPrimary, checkedTrackColor = DocScanColors.ScanGlow),
                    )
                }
            }
        }
    }
}

/**
 * Go Pro & Quota Paywall: Content of Stitch v2 + Style of Stitch v1 (Tactile Cyber-Minimalism cards & neon mint glow).
 */
@Composable
fun PaywallScreen(
    state: DocScanAppState,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        containerColor = DocScanColors.SurfaceBase,
        topBar = {
            DocTopBar(
                title = "Go Pro",
                onBack = onClose,
                trailing = {
                    Surface(
                        shape = CircleShape,
                        color = DocScanColors.ScanGlow.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, DocScanColors.ScanGlow.copy(alpha = 0.4f)),
                    ) {
                        Text(
                            text = "DOCSCAN PRO",
                            style = MaterialTheme.typography.labelSmall,
                            color = DocScanColors.ScanGlow,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                },
            )
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Unlimited scans", style = MaterialTheme.typography.displaySmall, color = DocScanColors.TextPrimary)
                Text(
                    "You've used ${state.quotaUsedToday}/${DocScanAppState.FREE_DAILY_QUOTA} free scans today. Go Pro for unlimited page saves and ad-free scanning.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DocScanColors.TextSecondary,
                )
            }

            // v2 content in v1 card style
            DocCardSurface(borderColor = DocScanColors.ScanGlow.copy(alpha = 0.3f)) {
                Text("WHAT'S INCLUDED", style = MaterialTheme.typography.labelSmall, color = DocScanColors.ScanGlow)
                Spacer(Modifier.height(10.dp))
                PaywallFeatureRow(Icons.Rounded.AllInclusive, "Unlimited scans", "No 5/day limit while Pro is active")
                Spacer(Modifier.height(10.dp))
                PaywallFeatureRow(Icons.Rounded.DocumentScanner, "Continuous capture", "Keep scanning when free daily quota would block you")
                Spacer(Modifier.height(10.dp))
                PaywallFeatureRow(Icons.Rounded.Speed, "Faster background processing", "OCR and page naming finish in the queue")
                Spacer(Modifier.height(10.dp))
                PaywallFeatureRow(Icons.Rounded.Favorite, "Support independent development", "Keeps DocScan private, ad-free, and tracker-free")
            }

            // 3 Pricing Plans ($6/yr, $2/3mos, $1/mo)
            ProPlan.entries.forEach { plan ->
                val selected = state.selectedPlan == plan
                DocCardSurface(
                    borderColor = if (selected) DocScanColors.ScanGlow else DocScanColors.BorderSubtle,
                    containerColor = if (selected) DocScanColors.SurfaceOverlay else DocScanColors.SurfaceCard,
                    onClick = { state.selectedPlan = plan },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (plan.badge != null) {
                                Surface(
                                    shape = CircleShape,
                                    color = DocScanColors.ScanGlow.copy(alpha = 0.16f),
                                ) {
                                    Text(
                                        text = plan.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DocScanColors.ScanGlow,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(plan.title, style = MaterialTheme.typography.titleMedium, color = DocScanColors.TextPrimary)
                            Text(plan.perMonth, style = MaterialTheme.typography.bodySmall, color = DocScanColors.TextSecondary)
                        }
                        Text(plan.price, style = MaterialTheme.typography.headlineSmall, color = DocScanColors.ScanGlow)
                    }
                }
            }

            Button(
                onClick = { state.purchasePlan(context as? android.app.Activity, state.selectedPlan) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DocScanColors.ScanGlow,
                    contentColor = DocScanColors.OnPrimary,
                ),
            ) {
                Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Continue with ${state.selectedPlan.title}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { state.restorePurchases() }) {
                    Text("Restore Purchases", color = DocScanColors.TextSecondary)
                }
                Text("·", color = DocScanColors.TextMuted)
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://docscan.cam/terms")))
                    }
                }) {
                    Text("Terms & Privacy", color = DocScanColors.TextSecondary)
                }
            }

            LocalFirstTrustBanner("100% private: We never send your documents or their contents to DocScan. Backup only if you connect an account.")
        }
    }
}

@Composable
private fun PaywallFeatureRow(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = DocScanColors.ScanGlow, modifier = Modifier.size(20.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, color = DocScanColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DocScanColors.TextSecondary)
        }
    }
}

/**
 * Settings & Preferences: Content of Stitch v2 + Style of Stitch v1, version 1.0.0, iCloud Drive hidden on Android.
 */
@Composable
fun SettingsScreen(
    state: DocScanAppState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DocScanColors.SurfaceBase,
        topBar = { DocTopBar(title = "Settings", onBack = onBack) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Tier & Daily UTC Quota card (v2 content, v1 style)
            DocCardSurface(borderColor = DocScanColors.ScanGlow.copy(alpha = 0.35f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = if (state.isPro) "DocScan Pro" else "DocScan Free",
                            style = MaterialTheme.typography.headlineSmall,
                            color = DocScanColors.TextPrimary,
                        )
                        Text(
                            text = "Documents never leave your phone",
                            style = MaterialTheme.typography.bodySmall,
                            color = DocScanColors.TextSecondary,
                        )
                    }
                    Icon(Icons.Rounded.Bolt, contentDescription = null, tint = DocScanColors.ScanGlow)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (state.isPro) "Daily scans · Unlimited" else "Daily scans · ${state.quotaUsedToday} / ${DocScanAppState.FREE_DAILY_QUOTA} today",
                    style = MaterialTheme.typography.labelLarge,
                    color = DocScanColors.ScanGlow,
                )
                if (!state.isPro) {
                    Text(
                        text = "Resets at midnight UTC · ${state.remainingFreeQuota} pages remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = DocScanColors.TextSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { state.currentRoute = DocScanRoute.PAYWALL },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DocScanColors.ScanGlow,
                            contentColor = DocScanColors.OnPrimary,
                        ),
                    ) {
                        Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Upgrade to Pro", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { state.statusBanner = "Restored Play Store purchases" }.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Restore purchases", style = MaterialTheme.typography.bodyMedium, color = DocScanColors.TextPrimary)
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = DocScanColors.TextSecondary)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions")),
                                )
                            }
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Manage subscription", style = MaterialTheme.typography.bodyMedium, color = DocScanColors.TextPrimary)
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = DocScanColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            // PERMISSIONS
            Text("PERMISSIONS", style = MaterialTheme.typography.labelSmall, color = DocScanColors.TextMuted)
            DocCardSurface {
                SettingsRow(
                    icon = Icons.Rounded.PhotoCamera,
                    title = "Camera",
                    subtitle = "Needed to scan documents",
                    trailingText = if (state.cameraPermissionGranted) "Allowed" else "Ask",
                    onClick = { state.currentRoute = DocScanRoute.CAMERA_PERMISSION },
                )
                Spacer(Modifier.height(10.dp))
                SettingsRow(
                    icon = Icons.Rounded.PhotoLibrary,
                    title = "Photo library",
                    subtitle = "For import and 7-day suggestions",
                    trailingText = if (state.photoPermissionGranted) "Allowed" else "7 days",
                    onClick = { state.currentRoute = DocScanRoute.PHOTO_PERMISSION },
                )
            }

            // PREFERENCES
            Text("PREFERENCES", style = MaterialTheme.typography.labelSmall, color = DocScanColors.TextMuted)
            DocCardSurface {
                SettingsSwitchRow("Auto-capture", "Capture when document edges lock", state.autoCaptureEnabled) { state.autoCaptureEnabled = it }
                Spacer(Modifier.height(8.dp))
                SettingsSwitchRow("Auto-rename from suggestion", "Use a suggested title when you save", state.autoRenameEnabled) { state.autoRenameEnabled = it }
                Spacer(Modifier.height(8.dp))
                SettingsSwitchRow("Background processing", "Finish OCR and naming after you leave", state.backgroundProcessingEnabled) { state.backgroundProcessingEnabled = it }
                Spacer(Modifier.height(8.dp))
                SettingsSwitchRow("Notifications", "When processing finishes or suggestions are ready", state.notificationsEnabled) { state.notificationsEnabled = it }
            }

            // BACKUP (Android: Google Drive & Google Photos only — iCloud Drive hidden)
            Text("BACKUP", style = MaterialTheme.typography.labelSmall, color = DocScanColors.TextMuted)
            DocCardSurface(onClick = { state.currentRoute = DocScanRoute.BACKUP }) {
                SettingsRow(
                    icon = Icons.Rounded.AddToDrive,
                    title = "Google Drive",
                    subtitle = "Images only backup",
                    trailingText = if (state.googleDriveConnected) "Connected" else "Off",
                    onClick = { state.currentRoute = DocScanRoute.BACKUP },
                )
                Spacer(Modifier.height(10.dp))
                SettingsRow(
                    icon = Icons.Rounded.PhotoLibrary,
                    title = "Google Photos",
                    subtitle = "Images only backup",
                    trailingText = if (state.googlePhotosConnected) "Connected" else "Off",
                    onClick = { state.currentRoute = DocScanRoute.BACKUP },
                )
            }

            LocalFirstTrustBanner("100% private: We never send your documents or their contents to DocScan. Backup only runs if you connect an account.")

            // ABOUT
            Text("ABOUT", style = MaterialTheme.typography.labelSmall, color = DocScanColors.TextMuted)
            DocCardSurface {
                SettingsRow(Icons.Rounded.Info, "Privacy Policy", "docscan.cam/privacy", null) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://docscan.cam/privacy")))
                    }
                }
                Spacer(Modifier.height(8.dp))
                SettingsRow(Icons.Rounded.Info, "Terms of Use", "docscan.cam/terms", null) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://docscan.cam/terms")))
                    }
                }
                Spacer(Modifier.height(8.dp))
                SettingsRow(Icons.Rounded.Info, "Licenses (NOTICE)", "Open-source & model licenses", null) {
                    showLicensesDialog = true
                }
            }

            // DATA
            Text("DATA", style = MaterialTheme.typography.labelSmall, color = DocScanColors.TextMuted)
            DocCardSurface {
                SettingsRow(
                    icon = Icons.Rounded.DeleteOutline,
                    title = "Clear local library",
                    subtitle = "Removes documents on this device. Cloud backups are not deleted.",
                    trailingText = null,
                    onClick = { showClearDialog = true },
                )
                Spacer(Modifier.height(10.dp))
                SettingsRow(
                    icon = Icons.Rounded.Warning,
                    title = "Delete account & data",
                    subtitle = "Removes this device’s DocScan records and local documents",
                    trailingText = null,
                    tint = DocScanColors.DangerCrimson,
                    onClick = { showDeleteAccountDialog = true },
                )
            }

            Text(
                text = "DocScan 1.0.0 · cam.docscan · We never upload your scans",
                style = MaterialTheme.typography.labelSmall,
                color = DocScanColors.TextMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp),
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = DocScanColors.SurfaceCard,
            title = { Text("Clear local library?", color = DocScanColors.TextPrimary) },
            text = {
                Text(
                    "This permanently deletes documents and OCR text stored on this phone. Files already backed up to Drive or Photos are not removed.",
                    color = DocScanColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { state.clearLocalLibrary(); showClearDialog = false }) {
                    Text("Clear library", color = DocScanColors.DangerCrimson)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel", color = DocScanColors.TextSecondary) }
            },
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            containerColor = DocScanColors.SurfaceCard,
            title = { Text("Delete account & data?", color = DocScanColors.TextPrimary) },
            text = {
                Text(
                    "This deletes documents on this phone and DocScan server data tied to this device (quota and device ID). Connected Drive / Photos backups are not deleted there.",
                    color = DocScanColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { state.deleteAccountAndData(); showDeleteAccountDialog = false }) {
                    Text("Delete everything", color = DocScanColors.DangerCrimson)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Cancel", color = DocScanColors.TextSecondary) }
            },
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            containerColor = DocScanColors.SurfaceCard,
            title = { Text("Open-Source & Model Licenses", color = DocScanColors.TextPrimary) },
            text = {
                Text(
                    "DocScan uses Jetpack Compose, CameraX, Room, ML Kit Text Recognition, and bundled on-device document detection models. All processing runs locally on your phone.",
                    color = DocScanColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("Close", color = DocScanColors.ScanGlow)
                }
            },
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingText: String?,
    tint: Color = DocScanColors.ScanGlow,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = DocScanColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DocScanColors.TextSecondary)
        }
        if (trailingText != null) {
            Text(trailingText, style = MaterialTheme.typography.labelMedium, color = DocScanColors.ScanGlow)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = DocScanColors.TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = DocScanColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DocScanColors.TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = DocScanColors.OnPrimary, checkedTrackColor = DocScanColors.ScanGlow),
        )
    }
}
