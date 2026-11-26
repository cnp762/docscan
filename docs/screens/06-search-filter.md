# Search / filter / sort

## Purpose

Find documents by name, tags, OCR text; sort by date/name.

## ASCII

```
┌─────────────────────────┐
│ ←  Search               │
│ 🔍 invoice tax          │
│                         │
│ Sort: Newest ▾  Filters │
│                         │
│ ┌─────────────────────┐ │
│ │ Invoice Mar 12      │ │
│ │ …tax ID 0312…       │ │
│ └─────────────────────┘ │
│ ┌─────────────────────┐ │
│ │ Tax_return_2024     │ │
│ └─────────────────────┘ │
│ (empty: No matches)     │
├─[ Ad banner · Free ]────┤
└─────────────────────────┘
```

Free: banner above bottom safe area. Pro: no banner.

## Acceptance

- FTS includes OCR body
- Filter chips: has tag, date range, batch vs standalone
- Banner Free-only
