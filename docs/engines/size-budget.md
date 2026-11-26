# Engine / model size budget

Estimates for rewrite MVP (no CS `.data` ciphertext, no generative weights yet).

| Component | Approx contribution |
|-----------|---------------------|
| App shell + Compose + CameraX | ~8–15 MB |
| ML Kit text-recognition (bundled path) | ~2–5 MB code; models via Play Services or bundled per config |
| Heuristic detect/tag/summary/enhance | &lt; 0.5 MB |
| Optional future RapidOCR ONNX tiny | ~6–15 MB |
| Optional summary LM quantized | ~200–500 MB (major APK driver) |

## Policy

- Keep generative summary weights **optional** behind `assets/models/summary/`
- Prefer Play Services unbundled OCR on Android when store policy allows smaller APK
- Re-measure with `./gradlew :app:assembleRelease` and Xcode archive after each model pin

## Current MVP

No large weight files shipped; size dominated by AndroidX + ML Kit stubs.
