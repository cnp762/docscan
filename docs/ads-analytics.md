# Ads & analytics (MVP)

## Firebase

| Product | Use |
|---------|-----|
| **Analytics** | Funnel + monetization events (no document content) |
| **Crashlytics** | Native crash / non-fatal reports |

### Privacy rules

- Never send document images, OCR text, summaries, file names that contain PII, or backup tokens to Firebase.
- Allowed: event name, coarse params (`source=camera|import|suggest`, `n_pages`, `is_pro`, `quota_remaining`, screen id, store product id).
- Privacy Policy / Settings copy must disclose Analytics + Crashlytics + ads; do **not** claim “zero telemetry.”
- Document privacy promise stays scoped: **we never send scans or their contents to DocScan** (Firebase is third-party telemetry, not DocScan document storage).

### Core events (English names)

| Event | When |
|-------|------|
| `onboarding_complete` | First-run done |
| `scan_save` | Successful Save commit (`n`, `source`) |
| `quota_hit` | Save blocked by free limit |
| `paywall_view` | Paywall shown (`trigger`) |
| `purchase_success` / `purchase_fail` | Store result |
| `restore_success` | Restore Purchases OK |
| `ad_impression` / `ad_click` | Banner lifecycle (if SDK does not already) |
| `export_share` | Export/print/share (`format`) |

Screen views: use Firebase automatic / manual `screen_view` with stable screen ids from `docs/screens/`.

## Banner ads (Free only)

- Network: **Google AdMob** (or Play/App Store–compliant equivalent); standard **banner** (~50–90 dp tall).
- Placement: **overlay strip immediately above the bottom tab / nav bar** (not floating mid-content).
- **Pro: no ads** — hide banner and reclaim vertical space; entitlement from local cache (refresh with billing verify).

### Show on

| Screen | Notes |
|--------|-------|
| Home / Library | Tab shell |
| Search / Filter | Above bottom safe area / back stack chrome |
| Doc / Batch Detail | Above bottom action row or tab if present |

### Never show on

Onboarding, Capture, Preview/Edit, Paywall, Export/Share sheets, Backup connect flows, system pickers, full-screen processing.

### UX rules

- Banner must not cover primary CTAs (Save, Capture shutter, paywall SKUs).
- Fail-soft: ad load failure → no empty gray box; content uses full height.
- iOS: follow ATT / AdMob guidance; prefer non-personalized if ATT denied.
- Paywall copy may list **Ad-free** as a Pro benefit (alongside unlimited saves).

## Acceptance

- Free builds show banner only on allowed screens; Pro builds never request/show banner.
- Crashlytics + Analytics initialize without blocking first frame; offline queues OK.
- No document bytes or OCR strings in any Firebase payload (spot-check in debug).
