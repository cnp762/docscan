# Billing & quota

## Free tier

- **5 page commits** per **UTC calendar day**
- Sources share one counter: camera, import, library suggest
- Offline free: local counter hard-capped at 5; reconcile with server when online (never exceed 5 for free)

## Pro

| Product ID | Price | Period |
|------------|-------|--------|
| `pro_monthly` | $1 | 1 month |
| `pro_quarterly` | $2 | 3 months |
| `pro_yearly` | $6 | 1 year |

Stores: Play Billing + StoreKit 2. Server verifies receipts/JWS and caches entitlement on device.

## Consume API

`POST /quota/consume` with `{ "n": <pages being saved> }` at Save time.

- Insufficient free quota → paywall (do not partial-save silently without telling the user)
- Prefer block at Save with remaining count copy: “You have X scans left today”

## Paywall triggers

- Save would exceed free remaining and user is not Pro
- Optional soft entry from Settings / feature gates later

## Re-edit

Editing an already saved page does **not** consume quota.
