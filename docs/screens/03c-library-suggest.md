# Library suggest (last 7 days)

## Purpose

After photo library permission, scan last 7 days for document-like photos and suggest import & scan.

## Entry / exit

- Entry: Home “Suggested” / Settings enable
- First time: permissions pre-prompt → system grant
- Exit: multi-select → Preview/Edit; dismiss items; deny → empty + Settings CTA

## Behavior

- Filter media by creation/modification within rolling 7×24h
- Score document-like (resolution, aspect, edge/classifier) on-device only
- Dedup against already imported `source_media_id`
- Respect Limited Library on iOS
- Scoring runs on the **shared background WorkQueue** (Suggest priority): scan MediaStore/Photos in **chunks**, enqueue one `SuggestScoreItem` per media — never score the whole 7 days in one job. FreshScan Save NLP always jumps ahead of suggest work (see [resource-limits.md](../engines/resource-limits.md)).

## ASCII — results

```
┌─────────────────────────┐
│ ←  Suggested · 7 days   │
│                         │
│  Select photos that     │
│  look like documents.   │
│                         │
│  ┌────┐ ┌────┐ ┌────┐  │
│  │ ✓  │ │    │ │ ✓  │  │
│  │doc │ │pic │ │doc │  │
│  └────┘ └────┘ └────┘  │
│  ┌────┐ ┌────┐         │
│  │    │ │ ✓  │         │
│  └────┘ └────┘         │
│                         │
│  [ Select all docs ]    │
│  [ Import & scan (3) ]  │
└─────────────────────────┘
```

## ASCII — permission denied

```
┌─────────────────────────┐
│ ←  Suggested            │
│                         │
│  Photo access is off.   │
│  Enable it to find      │
│  documents from the     │
│  last 7 days.           │
│                         │
│  [ Open Settings ]      │
│  [ Import manually ]    │
└─────────────────────────┘
```

## Acceptance

- Quota only on Save after Review/Edit
- Nothing uploaded to DocScan server
