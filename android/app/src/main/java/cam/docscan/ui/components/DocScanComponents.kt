package cam.docscan.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cam.docscan.engine.api.EnhancePreset
import cam.docscan.ui.theme.DocScanColors

@Composable
fun QuotaChip(
    usedToday: Int,
    dailyLimit: Int,
    isPro: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (isPro) "PRO · UNLIMITED" else "$usedToday/$dailyLimit today"
    val bg = if (isPro) DocScanColors.ScanGlow.copy(alpha = 0.2f) else DocScanColors.ScanGlow.copy(alpha = 0.12f)
    val borderColor = if (isPro) DocScanColors.Tertiary else DocScanColors.ScanGlow.copy(alpha = 0.35f)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = bg,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = DocScanColors.ScanGlow,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = DocScanColors.ScanGlow,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun DocCardSurface(
    modifier: Modifier = Modifier,
    borderColor: Color = DocScanColors.BorderSubtle,
    containerColor: Color = DocScanColors.SurfaceCard,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val baseModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(containerColor)
        .border(1.dp, borderColor, shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)

    Column(modifier = baseModifier.padding(16.dp), content = content)
}

@Composable
fun DocTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(DocScanColors.SurfaceCard),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = DocScanColors.TextPrimary,
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = DocScanColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = DocScanColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun LocalFirstTrustBanner(
    text: String = "100% local-first · Documents never leave your phone",
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DocScanColors.SurfaceRaised,
        border = BorderStroke(1.dp, DocScanColors.BorderSubtle),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.VerifiedUser,
                contentDescription = null,
                tint = DocScanColors.ScanGlow,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = DocScanColors.TextSecondary,
            )
        }
    }
}

@Composable
fun FilterMatrixBar(
    selected: EnhancePreset,
    onSelect: (EnhancePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        EnhancePreset.ORIGINAL to "Original",
        EnhancePreset.COLOR to "Color",
        EnhancePreset.GRAY to "Gray",
        EnhancePreset.DOCUMENT to "Doc Clean",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DocScanColors.SurfaceRaised)
            .border(1.dp, DocScanColors.BorderSubtle, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (mode, label) ->
            val isSelected = selected == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) DocScanColors.SurfaceOverlay else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) DocScanColors.ScanGlow else DocScanColors.TextSecondary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun OcrInspectionCard(
    text: String,
    languageLabel: String,
    copied: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DocScanColors.SurfaceRaised)
            .border(1.dp, DocScanColors.BorderSubtle, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "OCR Extracted Text",
                style = MaterialTheme.typography.labelLarge,
                color = DocScanColors.TextPrimary,
            )
            Surface(
                onClick = onCopy,
                shape = RoundedCornerShape(8.dp),
                color = if (copied) DocScanColors.ScanGlow.copy(alpha = 0.16f) else DocScanColors.SurfaceOverlay,
                border = BorderStroke(
                    1.dp,
                    if (copied) DocScanColors.ScanGlow else DocScanColors.BorderSubtle,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "Copy OCR text",
                        tint = if (copied) DocScanColors.ScanGlow else DocScanColors.TextPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (copied) "Copied" else "Copy text",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (copied) DocScanColors.ScanGlow else DocScanColors.TextPrimary,
                    )
                }
            }
        }
        Text(
            text = text.ifBlank { "No OCR text extracted yet." },
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = DocScanColors.TextSecondary,
        )
        Text(
            text = languageLabel,
            style = MaterialTheme.typography.labelSmall,
            color = DocScanColors.Tertiary,
        )
    }
}

/**
 * Interactive 4-corner perspective quad overlay with neon mint glow and active loupe indicator.
 */
@Composable
fun InteractiveQuadOverlay(
    corners: List<Offset>,
    onUpdateCorner: (index: Int, normalized: Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeIndex by remember { mutableIntStateOf(-1) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(corners) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            var bestIdx = -1
                            var bestDist = Float.MAX_VALUE
                            corners.forEachIndexed { i, c ->
                                val px = Offset(c.x * w, c.y * h)
                                val d = (px - start).getDistance()
                                if (d < bestDist && d < 90f) {
                                    bestDist = d
                                    bestIdx = i
                                }
                            }
                            activeIndex = bestIdx
                        },
                        onDragEnd = { activeIndex = -1 },
                        onDragCancel = { activeIndex = -1 },
                        onDrag = { change, dragAmount ->
                            if (activeIndex in corners.indices) {
                                change.consume()
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val cur = corners[activeIndex]
                                val next = Offset(
                                    x = (cur.x + dragAmount.x / w).coerceIn(0.03f, 0.97f),
                                    y = (cur.y + dragAmount.y / h).coerceIn(0.03f, 0.97f),
                                )
                                onUpdateCorner(activeIndex, next)
                            }
                        },
                    )
                },
        ) {
            if (corners.size == 4) {
                val pts = corners.map { Offset(it.x * size.width, it.y * size.height) }
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    lineTo(pts[1].x, pts[1].y)
                    lineTo(pts[2].x, pts[2].y)
                    lineTo(pts[3].x, pts[3].y)
                    close()
                }
                // Glow halo
                drawPath(
                    path = path,
                    color = DocScanColors.ScanGlow.copy(alpha = 0.35f),
                    style = Stroke(width = 10f),
                )
                drawPath(
                    path = path,
                    color = DocScanColors.ScanGlow,
                    style = Stroke(width = 4f),
                )
                pts.forEachIndexed { idx, p ->
                    val isDragging = idx == activeIndex
                    drawCircle(
                        color = DocScanColors.ScanGlow.copy(alpha = if (isDragging) 0.45f else 0.25f),
                        radius = if (isDragging) 34f else 24f,
                        center = p,
                    )
                    drawCircle(
                        color = DocScanColors.ScanGlow,
                        radius = 16f,
                        center = p,
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 8f,
                        center = p,
                    )
                }

                // 64px magnified loupe preview above active corner
                if (activeIndex in pts.indices) {
                    val target = pts[activeIndex]
                    val loupeCenter = Offset(
                        x = target.x.coerceIn(72f, size.width - 72f),
                        y = (target.y - 110f).coerceAtLeast(72f),
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                DocScanColors.SurfaceOverlay,
                                DocScanColors.SurfaceBase,
                            ),
                            center = loupeCenter,
                            radius = 64f,
                        ),
                        radius = 64f,
                        center = loupeCenter,
                    )
                    drawCircle(
                        color = DocScanColors.ScanGlow,
                        radius = 64f,
                        center = loupeCenter,
                        style = Stroke(width = 4f),
                    )
                    drawLine(
                        color = DocScanColors.ScanGlow.copy(alpha = 0.7f),
                        start = Offset(loupeCenter.x - 16f, loupeCenter.y),
                        end = Offset(loupeCenter.x + 16f, loupeCenter.y),
                        strokeWidth = 2f,
                    )
                    drawLine(
                        color = DocScanColors.ScanGlow.copy(alpha = 0.7f),
                        start = Offset(loupeCenter.x, loupeCenter.y - 16f),
                        end = Offset(loupeCenter.x, loupeCenter.y + 16f),
                        strokeWidth = 2f,
                    )
                }
            }
        }
    }
}
