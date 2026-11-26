package cam.docscan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cam.docscan.BuildConfig
import cam.docscan.engine.api.OcrPage
import cam.docscan.engine.api.SummaryMode
import cam.docscan.engine.classify.DocClassifyEngine
import cam.docscan.engine.impl.DefaultDocScanEngine
import cam.docscan.engine.lang.LangDetectEngine
import cam.docscan.engine.summary.SummaryEngine
import kotlinx.coroutines.launch

/**
 * Shell navigation + engine smoke hooks. Full UI mocks live under docs/screens.
 */
@Composable
fun DocScanAppRoot() {
    var status by remember {
        mutableStateOf("Engines: OCR + lang/classify/summary + metadata (resource-gated)")
    }
    val scope = rememberCoroutineScope()

    Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("DocScan", style = MaterialTheme.typography.headlineLarge)
            Text(
                "cam.docscan · API ${BuildConfig.API_BASE_URL}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Text(status, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { /* TODO: Capture */ }) { Text("Scan") }
            Button(onClick = { /* TODO: Photo Picker */ }) { Text("Import") }
            Button(onClick = {
                scope.launch {
                    val sample = OcrPage(
                        text = "INVOICE #1042\nAmount due: \$120.00\nThank you for your business.",
                        title = "INVOICE #1042",
                    )
                    val lang = LangDetectEngine().detect(sample.text)
                    val doc = DocClassifyEngine().classify(sample)
                    val sum = SummaryEngine().summarize(sample.text, SummaryMode.EXTRACTIVE)
                    status = "lang=${lang.lang} type=${doc.docType} tags=${doc.tags} · ${sum.text}"
                }
            }) { Text("Smoke NLP") }
            Button(onClick = {
                scope.launch {
                    val eng = DefaultDocScanEngine()
                    eng.initialize(null)
                    status = "DefaultDocScanEngine ready=${eng.isReady()}"
                    eng.close()
                }
            }) { Text("Init engine") }
            Button(onClick = { /* TODO: Billing */ }) { Text("Go Pro") }
        }
    }
}
