# Settings

## Purpose

Quota / Pro, store billing controls, permissions, scan preferences, backup accounts, privacy promise, legal, clear/delete data (store compliance).

## ASCII

```
┌─────────────────────────┐
│ ←  Settings             │
│                         │
│ Plan & Pro              │
│  Plan: Free · 3/5 today │
│  Resets midnight UTC    │
│  [ Upgrade to Pro ]     │
│  Restore purchases    > │
│  Manage subscription  > │
│                         │
│ Permissions             │
│  Camera        Allowed  │
│  Photo library Limited> │
│                         │
│ Preferences             │
│  Auto-capture      [On] │
│  Default enhance     >  │
│  Auto-rename       [On] │
│  Background work   [On] │
│  Notifications     [On] │
│                         │
│ Backup                  │
│  Google Drive    Conn > │
│  Google Photos    Off > │
│  iCloud Drive   Active> │  ← iOS
│                         │
│ Privacy                 │
│  Documents never leave  │
│  your phone. We never   │
│  send scans or content  │
│  to DocScan. Backup     │
│  only if you connect.   │
│                         │
│ About                   │
│  Privacy Policy       > │
│  Terms of Use         > │
│  Licenses (NOTICE)    > │
│                         │
│ Data                    │
│  Clear local library  > │
│  Delete account & data> │
│                         │
│ Version 0.1.0 · cam.doc…│
└─────────────────────────┘
```

## Preference rows

| Row | Default | Behavior |
|-----|---------|----------|
| Auto-capture | On | Capture when edges are stable (same control as Capture screen) |
| Default enhance | Doc Clean | Applied after crop on new pages; last choice on Review also updates this |
| Auto-rename from suggestion | On | On Save, apply suggested title when confidence is high; user can still edit |
| Background processing | On | Continue OCR, naming, summary after leaving Review; may use background task APIs |
| Notifications | Off until OS grant | Notify when background work finishes, suggest candidates ready, backup errors |

## Store compliance (Apple / Google)

| Control | Why | Behavior |
|---------|-----|----------|
| **Restore purchases** | App Store subscriptions / Guideline 3.1.x; Play restore | Sync entitlement from App Store / Play account → server verify → cache on device. **No DocScan login.** Also on Paywall. |
| **Manage subscription** | Subscription UX; cancel path | Deep-link to App Store subscriptions / Play subscription management |
| **Clear local library** | User data control | Deletes on-device docs + OCR; does **not** wipe server device/quota; does **not** delete remote Drive/Photos/iCloud |
| **Delete account & data** | Apple 5.1.1(v) for automatically created / guest accounts; Play account-deletion | Wipes local library; revokes backup OAuth; `DELETE` server device + quota/entitlement rows for this device ID; warns that **store billing continues** until user cancels via Manage subscription |

DocScan has **no email/password account**. Device registration (`POST /v1/devices/register`) still counts as an automatically created account for App Review — deletion must be in-app and easy to find (Settings → Data).

## Privacy copy (product voice)

Prefer outcome language. Avoid jargon (*local-first engine*, *on-device neural*, *zero cloud relay*, *hardware quota*, *optic reticle*).

Canonical lines:

- **Documents never leave your phone.**
- **We never send your scans or their contents to DocScan.**
- **100% private for scanning & OCR — backup only if you connect an account.**

Do **not** claim absolute “zero cloud” or “zero telemetry” while quota reconcile, Firebase Analytics/Crashlytics, AdMob (Free), or user-chosen backup exist. Scope the promise to document images and extracted content (never sent to DocScan). Privacy Policy must name Firebase + ads.

## Acceptance

- Quota counter uses **UTC calendar day** (copy: “Resets at midnight UTC”)
- Package / bundle shown as `cam.docscan` (not `cam.docscan.app`)
- Restore purchases is discoverable in **Settings and Paywall**
- Manage subscription opens the platform subscription UI
- Clear local library confirms; remotes untouched unless user chooses
- Delete account & data confirms with billing warning; completes in-app (no email/support ticket required)
- Privacy Policy, Terms of Use, Licenses are **separate** rows; Privacy/Terms open LDP URLs on docscan.cam
- Background processing Off → work runs only while the processing UI is active (or until OS kills the app)
- Notifications Off → no push/local alerts; in-app status still updates
- Auto-rename Off → keep camera/import default name; suggestion still shown as editable hint on Save
