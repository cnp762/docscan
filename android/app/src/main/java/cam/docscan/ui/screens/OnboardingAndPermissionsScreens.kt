package cam.docscan.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cam.docscan.ui.components.DocCardSurface
import cam.docscan.ui.components.LocalFirstTrustBanner
import cam.docscan.ui.theme.DocScanColors

@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DocScanColors.SurfaceBase)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DocScanColors.ScanGlow.copy(alpha = 0.16f))
                        .border(1.dp, DocScanColors.ScanGlow.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DocumentScanner,
                        contentDescription = null,
                        tint = DocScanColors.ScanGlow,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = "DOCSCAN",
                    style = MaterialTheme.typography.labelLarge,
                    color = DocScanColors.TextPrimary,
                )
            }
            Surface(
                shape = CircleShape,
                color = DocScanColors.ScanGlow.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, DocScanColors.ScanGlow.copy(alpha = 0.3f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = DocScanColors.ScanGlow,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "100% private",
                        style = MaterialTheme.typography.labelMedium,
                        color = DocScanColors.ScanGlow,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Scan documents on your phone.\nPrivate by default.",
                style = MaterialTheme.typography.headlineLarge,
                color = DocScanColors.TextPrimary,
            )
            Text(
                text = "We never send your documents or their contents to DocScan.",
                style = MaterialTheme.typography.bodyMedium,
                color = DocScanColors.TextSecondary,
            )
        }

        OnboardingFeatureCard(
            icon = Icons.Rounded.CropFree,
            badge = "Fast",
            title = "Auto-capture",
            subtitle = "Real-time boundary tracking, perspective correction & shadow removal in milliseconds.",
        )
        OnboardingFeatureCard(
            icon = Icons.Rounded.DocumentScanner,
            badge = "Offline",
            title = "OCR & search",
            subtitle = "Find text inside receipts, contracts, and notes — on your phone.",
        )
        OnboardingFeatureCard(
            icon = Icons.Rounded.PictureAsPdf,
            badge = "Vectorized",
            title = "Export PDF & DOC",
            subtitle = "Generate lightweight, searchable PDF, DOCX, and JPEG archives ready to share.",
        )
        OnboardingFeatureCard(
            icon = Icons.Rounded.Verified,
            badge = "No watermark",
            title = "Free Forever",
            subtitle = "Zero forced watermarks on exported PDFs or docs. Core offline scanning remains free.",
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillTag(icon = Icons.Rounded.Bolt, label = "5 free scans / day", modifier = Modifier.weight(1f))
            PillTag(icon = Icons.Rounded.CloudOff, label = "Optional cloud sync", modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DocScanColors.ScanGlow,
                contentColor = DocScanColors.OnPrimary,
            ),
        ) {
            Text(
                text = "Get started",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
        }

        Text(
            text = "No account needed. Camera permission is requested only when you start scanning.",
            style = MaterialTheme.typography.bodySmall,
            color = DocScanColors.TextMuted,
        )
    }
}

@Composable
private fun OnboardingFeatureCard(
    icon: ImageVector,
    badge: String,
    title: String,
    subtitle: String,
) {
    DocCardSurface {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DocScanColors.ScanGlow.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = DocScanColors.ScanGlow, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = DocScanColors.TextPrimary)
                    Surface(
                        shape = CircleShape,
                        color = DocScanColors.SurfaceOverlay,
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = DocScanColors.Tertiary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DocScanColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun PillTag(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = DocScanColors.SurfaceRaised,
        border = BorderStroke(1.dp, DocScanColors.BorderSubtle),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = DocScanColors.ScanGlow, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = DocScanColors.TextPrimary)
        }
    }
}

@Composable
fun CameraPermissionScreen(
    onAllowCamera: () -> Unit,
    onNotNow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DocScanColors.SurfaceBase)
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Permissions Flow", style = MaterialTheme.typography.labelLarge, color = DocScanColors.TextSecondary)
            IconButton(onClick = onNotNow) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = DocScanColors.TextSecondary)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(DocScanColors.ScanGlow.copy(alpha = 0.16f))
                    .border(1.dp, DocScanColors.ScanGlow.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = DocScanColors.ScanGlow, modifier = Modifier.size(32.dp))
            }

            Text("Allow camera access?", style = MaterialTheme.typography.headlineLarge, color = DocScanColors.TextPrimary)
            Text(
                "DocScan needs camera access to detect document edges and scan pages in real-time. Scans are processed entirely on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = DocScanColors.TextSecondary,
            )

            LocalFirstTrustBanner("Documents never leave your phone. We never send your scans to DocScan.")

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DocCardSurface(modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.CropFree, contentDescription = null, tint = DocScanColors.ScanGlow)
                    Spacer(Modifier.height(6.dp))
                    Text("Auto-Edge Quad", style = MaterialTheme.typography.labelLarge, color = DocScanColors.TextPrimary)
                    Text("Millimeter snap", style = MaterialTheme.typography.bodySmall, color = DocScanColors.TextSecondary)
                }
                DocCardSurface(modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.DocumentScanner, contentDescription = null, tint = DocScanColors.Tertiary)
                    Spacer(Modifier.height(6.dp))
                    Text("Instant OCR", style = MaterialTheme.typography.labelLarge, color = DocScanColors.TextPrimary)
                    Text("Offline text extract", style = MaterialTheme.typography.bodySmall, color = DocScanColors.TextSecondary)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onAllowCamera,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DocScanColors.ScanGlow,
                    contentColor = DocScanColors.OnPrimary,
                ),
            ) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Continue", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) {
                Text("Not now", color = DocScanColors.TextSecondary)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = DocScanColors.TextMuted, modifier = Modifier.size(12.dp))
                Spacer(Modifier.size(4.dp))
                Text("Zero cloud storage by default", style = MaterialTheme.typography.bodySmall, color = DocScanColors.TextMuted)
            }
        }
    }
}

@Composable
fun PhotoPermissionScreen(
    onAllowAccess: () -> Unit,
    onSelectManually: () -> Unit,
    onNotNow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DocScanColors.SurfaceBase)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Permissions Flow", style = MaterialTheme.typography.labelLarge, color = DocScanColors.TextSecondary)
            IconButton(onClick = onNotNow) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = DocScanColors.TextSecondary)
            }
        }

        Text("Allow photo & file access?", style = MaterialTheme.typography.headlineLarge, color = DocScanColors.TextPrimary)
        Text(
            "DocScan securely imports existing receipts, documents, and past 7-day images directly from your storage. Nothing is ever sent out.",
            style = MaterialTheme.typography.bodyMedium,
            color = DocScanColors.TextSecondary,
        )

        LocalFirstTrustBanner("Scanning and OCR stay on your phone. Backup only if you connect an account.")

        OnboardingFeatureCard(
            icon = Icons.Rounded.FolderOpen,
            badge = "Direct",
            title = "Direct Photo & File Import",
            subtitle = "Select forms, receipts, invoices, and high-res images directly from your device storage.",
        )
        OnboardingFeatureCard(
            icon = Icons.Rounded.AutoAwesome,
            badge = "7 Days",
            title = "Smart 7-Day Auto Discovery",
            subtitle = "Library Suggest finds document-like snapshots taken recently, skipping selfies and scenery.",
        )
        OnboardingFeatureCard(
            icon = Icons.Rounded.PhotoLibrary,
            badge = "Scoped",
            title = "Scoped & Private Access",
            subtitle = "Fully compatible with Android Photo Picker. You control which images DocScan can see.",
        )

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onAllowAccess,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DocScanColors.ScanGlow,
                contentColor = DocScanColors.OnPrimary,
            ),
        ) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Allow Access", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onSelectManually,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, DocScanColors.BorderMedium),
        ) {
            Text("Select Manually", color = DocScanColors.TextPrimary)
        }

        TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) {
            Text("Not now", color = DocScanColors.TextSecondary)
        }
    }
}
