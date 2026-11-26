# Import

## Purpose

Pick images from device without requiring full library access.

## Entry / exit

- Entry: Home Import, or Capture → Import
- Exit: selected images → Preview/Edit; cancel → previous

## Behavior

- Android: Photo Picker / SAF multi-select `image/*`
- iOS: PHPicker / PhotosPicker / fileImporter
- Copy into app storage before edit
- HEIC decode failure: toast, skip that item

## ASCII

```
┌─────────────────────────┐
│ ←  Import               │
│                         │
│  System photo picker    │
│  (multi-select)         │
│                         │
│  ┌───┐ ┌───┐ ┌───┐     │
│  │ ✓ │ │ ✓ │ │   │ …   │
│  └───┘ └───┘ └───┘     │
│                         │
│  Or: Browse files…      │
│                         │
│      [ Add 2 photos ]   │
└─────────────────────────┘
```

## Acceptance

- No READ_MEDIA / full library prompt for this path
- Lands on Review/Edit with filmstrip
