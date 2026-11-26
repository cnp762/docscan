# Paywall / Pro

## Purpose

Upsell when free quota exhausted or user opens Pro from Settings.

## ASCII

```
┌─────────────────────────┐
│ ×                       │
│                         │
│      Go Pro             │
│                         │
│  Unlimited scans.       │
│  You've used 5/5 today. │
│                         │
│  Pro = unlimited saves  │
│  + ad-free              │
│  (OCR/export already    │
│   available on Free)    │
│                         │
│  ┌───────────────────┐  │
│  │ $6 / year   BEST  │  │
│  └───────────────────┘  │
│  ┌───────────────────┐  │
│  │ $2 / 3 months     │  │
│  └───────────────────┘  │
│  ┌───────────────────┐  │
│  │ $1 / month        │  │
│  └───────────────────┘  │
│                         │
│  [ Restore purchases ]  │
│  Terms · Privacy        │
│                         │
│  100% private — we      │
│  never send your docs   │
│  to DocScan.            │
│                         │
│  Billing via App Store  │
│  / Google Play.         │
└─────────────────────────┘
```

Also expose **Restore purchases** + **Manage subscription** in Settings (store review discoverability).

## Acceptance

- StoreKit 2 / Play Billing purchase → server verify → entitlement cache
- Restore uses App Store / Play account only — **no DocScan sign-in**
- Paywall shows price, period, and store billing disclosure
- Do **not** market OCR, Doc Clean, or export formats as Pro-only unlocks
- Pro benefit copy may include **unlimited saves** and **ad-free**

