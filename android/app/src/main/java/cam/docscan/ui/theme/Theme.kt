package cam.docscan.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val scheme = darkColorScheme(
    primary = Color(0xFF3DD68C),
    onPrimary = Color(0xFF062015),
    background = Color(0xFF0C1210),
    onBackground = Color(0xFFE8F0EA),
    surface = Color(0xFF151C19),
    onSurface = Color(0xFFE8F0EA),
)

@Composable
fun DocScanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
