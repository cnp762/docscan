# Feature matrix

| Area | MVP (in scope) | Later |
|------|----------------|-------|
| Capture | Custom camera, quad overlay, auto-capture, guiding (light/angle/blur) | Book mode, ID templates |
| Import | Multi-select images (JPEG/PNG/WebP/HEIC); Photo Picker / SAF / Files | PDF import → pages |
| Library suggest | Last **7 days**; library permission; document-like scoring; multi-select → Review/Edit | Smarter classifier, widget |
| Preview/Edit | Drag quad, rotate, color/gray/B&W, **document enhance / de-shadow**, reorder/delete, rename; re-edit from detail | Magic erase, demoire |
| OCR | Best licensed stack (ML Kit / Vision / Paddle/RapidOCR); **auto language detection** | Extra language packs |
| Tagging / autoname | Heuristic + light model | — |
| Summary | **On-device generative summary** (bundled model) | Larger models |
| Library | List, rename, delete, open; **copy OCR text** | Folders UI |
| Search | Name, tag, date, OCR FTS | Semantic search |
| Export | Images; **searchable PDF**; **DOCX**; **Print**; share sheet | Complex DOCX layout |
| Backup | Google Drive; iCloud Drive (iOS); **Google Photos** (accept OAuth verify risk) | Shared albums |
| Offline | Capture/import/edit/OCR/summary offline; network for quota reconcile, backup, auth | — |
| Quota / Pro | 5 commits/UTC day; $1 / $2 / $6 | Promo codes |
| Auth | Device guest + optional Apple/Google for restore | Full account doc sync |
| UI locale | English only | Other locales |
| Compliance | Privacy, Terms, clear local data, disconnect backup | — |
| Layout | Phone + tablet/landscape safe capture overlay | — |

## Quota definition

One **page commit** after Save. Cancel/retake/edit-before-save does not count. Re-edit saved pages does not count. Camera + import + suggest share the same free counter.
