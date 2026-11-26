# CamScanner capability map → DocScan rewrite

Source: CamScanner 7.29.0 analysis (`ref/`, `_apkm_extract/`). Analyze only — do not ship CS `.so`.

| Capability | CS native | Model asset | DocScan rewrite target |
|------------|-----------|-------------|------------------------|
| OCR det+rec+layout | `libOCREngine.so` (CVNN) | `mobile_ocr.data` (encrypted pack) | ML Kit / RapidOCR-ONNX + lang detect |
| Doc-type tag | `libTAGEngine*.so` (MNN) | `tagengine_*.data` (encrypted) | Heuristic + optional ONNX classifier |
| Border / dewarp | `libpagescanner.so` | — | OpenCV / Vision quad + warp |
| Zawu / clutter | `libmagicenhancer.so` | `zw_detect.data` (encrypted) | Optional det ONNX later |
| Enhance / deshadow | `libmagicenhancer.so` | embedded | Bitmap/Core Image presets |
| Multimodal recog | — | `doc_recog_mob_multimodal_*.data` | Phase later |
| Summary | cloud VLM | — | **New** on-device LM / extractive fallback |
| Autoname | server AI rename | — | Local title-line heuristic |

## JNI surface (OCR) — mirror behavior

- `LoadModel(path, threads)`
- `RecognizePage(image, polygons?, langOpts?, userOpts?)`
- `ReleaseModel`
- User opts: DetAndRec / DetOnly / RecOnly / FastMode
- Lang opts: many `ISLangOpt_*` (Vietnamese present)

## Tag IDs (taxonomy reference)

Contract-style labels: contract, invoice, id_card, passport, receipt, whiteboard, book, unknown (−1). DocScan uses English string tags.
