# DocScanEngine contracts

Unified on-device API for Android (`cam.docscan.engine`) and iOS (`DocScanEngine`).

## Types

```text
Quad: four normalized or pixel points (tl, tr, br, bl)
OcrLine: text, confidence?, box?
OcrPage: text, lang?, langConfidence?, lines[], title?, pageDirection?
TagResult: tags[], scores?, flags? (table/handwritten/formula)
EnhancePreset: ORIGINAL | COLOR | GRAY | BW | DOCUMENT | DESHADOW

LangResult: lang (BCP-47), confidence
DocTypeResult: docType, confidence, alternatives[], tags[], scores?, flags?
SummaryResult: text, source (extractive | generative)
SummaryMode: EXTRACTIVE | AUTO (AUTO may try generative when gated)
PageNlpMetadata: pageId, lang, langConfidence, docType, tags, summary, summarySource, nlpVersion

WorkPriority: FreshScan(100) | UserSave(80) | Suggest(40) | Deferred(10)
WorkJob: EnhancePage | DetectPage | OcrPage | LangPage | ClassifyPage | SummarizePage
         | WriteMetadata | SummarizeBatch | SuggestScanChunk | SuggestScoreItem
```

## API

```text
initialize(modelsDir | asset root)
detectDocument(image) -> Quad?
recognize(image, quad?, langHint?) -> OcrPage
detectLanguage(ocrText) -> LangResult
classifyDocument(ocr, image?) -> DocTypeResult
tag(ocr, image?) -> TagResult
summarize(ocrText, mode?) -> SummaryResult
enhance(image, preset) -> image
writeImageMetadata(path, PageNlpMetadata) -> ok | skipped
close()
```

Heavy work is scheduled via shared `WorkQueue` (see [resource-limits.md](resource-limits.md)).

## Pipeline after Save (stepped)

UI enqueues a job **group** and returns `pageId` immediately. Worker runs one step at a time:

1. `EnhancePage` / `DetectPage` (if needed)  
2. `OcrPage` → persist `ocr_text`  
3. `LangPage` → `ocr_lang`, `ocr_lang_confidence`  
4. `ClassifyPage` → `doc_type`, tags  
5. `SummarizePage` → `summary`, `summary_source`  
6. `WriteMetadata` → sidecar + EXIF best-effort  
7. Batch (optional): `SummarizeBatch` (concat OCR ≤8k)

New capture/import uses **FreshScan** priority. Re-edit uses **UserSave**.

## Suggest (same queue)

```text
SuggestScanChunk(cursor) -> enqueue SuggestScoreItem×N
SuggestScoreItem(mediaId) -> score + SuggestCache upsert
```

Suggest jobs use **Suggest** priority and only run when no FreshScan/UserSave work is pending.

Re-edit does not consume quota; re-run OCR/NLP only if image bytes or enhance preset changed.

## CS parity notes (behavior only)

- Lang options include Vietnamese among ~50 scripts — DocScan auto-detect + packs  
- Tag taxonomy may map from CS-style ids (invoice, id_card, …) to English labels  
- No cloud VLM: on-device summary replaces CS `needVLM` path  
