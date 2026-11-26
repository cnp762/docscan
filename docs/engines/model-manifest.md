# Model manifest (shipped / planned)

| ID | Capability | Source | Format | License | Status |
|----|------------|--------|--------|---------|--------|
| `mlkit-text-recognition` | OCR + script hints | Google ML Kit | Play Services / bundled AAR | ML Kit ToS | **Active Android** |
| `vision-ocr` | OCR | Apple Vision | System | Apple SDK | **Active iOS** |
| `vision-doc-seg` | Document quad | Apple Vision | System | Apple SDK | **Active iOS** |
| `android-quad-heuristic` | Document quad MVP | DocScan code | — | Proprietary DocScan | **Active Android** |
| `enhance-bitmap` | Filters / deshadow proxy | DocScan code | — | Proprietary DocScan | **Active** |
| `tag-heuristic-v1` | Tags from OCR | DocScan code | — | Proprietary DocScan | **Active** |
| `summary-extractive-v1` | Summary fallback | DocScan code | — | Proprietary DocScan | **Active** |
| `summary-generative-placeholder` | Generative summary | TBD ExecuTorch/ORT small LM | weights in `assets/models/summary/` | TBD | **Stub** — drop weights later |
| `paddle-rapidocr-onnx` | OCR rewrite upgrade | Paddle/RapidOCR | ONNX | Apache-2.0 | Planned |
| `docaligner-onnx` | Detect upgrade | DocAligner-class | ONNX | Apache-2.0 | Planned |

## Asset directories

- Android: `android/app/src/main/assets/models/`
- iOS: `ios/DocScan/Resources/Models/`

Place generative weights under `summary/` when pinned; engine loads if present, else extractive.
