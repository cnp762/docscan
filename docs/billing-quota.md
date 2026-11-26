# Billing & quota

## Identity (MVP)

- **No DocScan user accounts.** Subject = device (`X-Device-Id` / `device:{id}`).
- App generates a stable UUID, persists in Keychain (iOS) / EncryptedSharedPreferences (Android), registers once via `POST /v1/devices/register`.
- Pro entitlement is verified from **App Store / Play** receipts; Restore Purchases uses the store account, not a DocScan login.
- Acceptable trade-off: reinstall / new device ID can reset the free daily counter (unless Restore recovers Pro).
- **Delete account & data** (Settings): wipe local docs, revoke backup OAuth, delete server rows for this device ID. Warn that subscription billing continues until cancelled in the store (Manage subscription).

## Free tier

- **5 page commits** per **UTC calendar day**
- Sources share one counter: camera, import, library suggest
- **Server is source of truth.** Authoritative check happens on Save via `POST /quota/consume`.
- Client may cache remaining (`QuotaLocal` / `GET /quota/today`) for UI hints only.
- Offline free: local counter hard-capped at 5; when online, Save must succeed consume before committing to the library (never exceed 5 for free).

## Pro

| Product ID | Price | Period |
|------------|-------|--------|
| `pro_monthly` | $1 | 1 month |
| `pro_quarterly` | $2 | 3 months |
| `pro_yearly` | $6 | 1 year |

Stores: Play Billing + StoreKit 2. Server verifies receipts/JWS, keys entitlement by store original transaction (device presents receipt), and caches on device.

## Consume API

`POST /quota/consume` with `{ "n": <pages being saved> }` at **Save** time (Review/Edit → Save).

- Insufficient free quota → HTTP 402 / paywall (do not partial-save silently without telling the user)
- Prefer block at Save with remaining count copy: “You have X scans left today”
- Do not rely on client-only counters to enforce the free tier

## Ads (Free)

Small AdMob banner above bottom nav on Library / Search / Detail. **Pro: no ads.** Spec: `docs/ads-analytics.md`.

## Paywall triggers

- Save would exceed free remaining and user is not Pro
- Optional soft entry from Settings / feature gates later (including “Remove ads”)

## Re-edit

Editing an already saved page does **not** consume quota.
