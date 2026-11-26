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
│  ┌───────────────────┐  │
│  │ $1 / month        │  │
│  └───────────────────┘  │
│  ┌───────────────────┐  │
│  │ $2 / 3 months     │  │
│  └───────────────────┘  │
│  ┌───────────────────┐  │
│  │ $6 / year   BEST  │  │
│  └───────────────────┘  │
│                         │
│  [ Restore purchases ]  │
│                         │
│  Billing via App Store  │
│  / Google Play.         │
└─────────────────────────┘
```

## Acceptance

- StoreKit 2 / Play Billing purchase → server verify → entitlement cache
- Restore works across reinstall when signed in / store account
