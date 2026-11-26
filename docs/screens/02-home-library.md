# Home / Library

## Purpose

Browse batches and standalone pages; entry to scan, import, suggest, search.

## States

- Empty library
- Populated list (sort: recent)
- Suggest banner if library permission granted and candidates exist
- Quota chip: “3/5 today” or “Pro”

## ASCII — populated

```
┌─────────────────────────┐
│ DocScan          3/5 ⚙️ │
│ 🔍 Search documents…    │
│                         │
│ Suggested (7 days)  See>│
│ ┌────┐ ┌────┐ ┌────┐   │
│ │img │ │img │ │img │   │
│ └────┘ └────┘ └────┘   │
│                         │
│ Recent                  │
│ ┌─────────────────────┐ │
│ │ 📄 Invoice Mar 12   │ │
│ │    4 pages · today  │ │
│ └─────────────────────┘ │
│ ┌─────────────────────┐ │
│ │ 🖼  Receipt_0912    │ │
│ │    standalone       │ │
│ └─────────────────────┘ │
│         ( + Scan )      │
├─[ Ad banner · Free ]────┤
│  Library   Scan   Import│
└─────────────────────────┘
```

Free: AdMob banner strip above tab bar. Pro: no banner row.

## Acceptance

- Tap batch/page → Detail
- FAB / Scan → Capture (permission flow)
- Suggest row → `03c-library-suggest` or multi-select into Review/Edit
- Banner visible on Free only; never covers FAB shutter path into Capture
