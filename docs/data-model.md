# Data model

## Local (device)

### Batch

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | |
| name | string | |
| created_at / updated_at | instant | |
| page_count | int | |
| cover_page_id | UUID? | |
| tags | string[] | |
| summary | string? | Aggregated NLP summary |
| summary_source | string? | `extractive` \| `generative` |

### Page (DocumentPage)

| Field | Type | Notes |
|-------|------|-------|
| id | UUID | |
| batch_id | UUID? | null = standalone |
| name | string | |
| source | enum | `camera` \| `import` \| `library_suggest` |
| image_path / thumb_path | path | App sandbox |
| original_path | path? | Pre-crop for reset |
| created_at | instant | |
| width / height | int | |
| quad | 4 points? | Normalized or pixel |
| enhance_preset | string | |
| rotation | int | Degrees |
| ocr_text | string? | |
| ocr_lang | string? | BCP-47 from lang detect |
| ocr_lang_confidence | float? | |
| doc_type | string? | Primary classify label |
| doc_type_confidence | float? | |
| doc_types_alt | string[] | Secondary types |
| tags | string[] | |
| summary | string? | Per-page (max ~500 chars) |
| summary_source | string? | `extractive` \| `generative` |
| nlp_version | string? | e.g. `1.0.0` |
| metadata_written_at | instant? | When sidecar/EXIF last written |
| auto_name_source | string? | |
| sort_index | int | Within batch |
| source_media_id | string? | Dedup library suggest |

### Image sidecar `{stem}.docscan.json`

Written next to the page image (best-effort EXIF/XMP also). Schema:

```json
{
  "docscan": 1,
  "page_id": "...",
  "lang": "en",
  "lang_confidence": 0.82,
  "doc_type": "invoice",
  "tags": ["invoice"],
  "summary": "...",
  "summary_source": "extractive",
  "nlp_version": "1.0.0"
}
```

EXIF/XMP write failures must not fail Save (DB + sidecar suffice).

### Tag

`id`, `label`, `color?`

### SearchIndex

FTS over name + ocr_text + tags + doc_type + summary.

### EntitlementCache

`is_pro`, `product_id`, `expires_at`, `last_verified_at`

### QuotaLocal

`utc_day` (YYYY-MM-DD UTC), `scan_count` — mirror; server is source of truth when online.

### BackupCursor

Provider, remote folder/album id, last_sync_at, per-entity remote ids.

### SuggestCache

`media_id`, `taken_at`, `score`, `dismissed_at`

## Server (Postgres)

- **users** — id, auth_providers, created_at, locale, app_version
- **devices** — device_id, user_id?, platform, push_token?
- **subscriptions** — store, product_id, status, original_tx, expires_at, raw_receipt_ref
- **quota_ledger** — subject_id, utc_day, scan_count
- **events** — paywall_view, purchase, quota_hit (no document content)

## API (MVP)

- Register/login device
- Verify Apple / Google purchase → entitlement
- `GET /quota/today`
- `POST /quota/consume` `{ n: number }`
- Health check
