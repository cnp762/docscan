# Capture

## Purpose

Custom camera: single or batch, quad overlay, auto-capture, guiding, torch, import shortcut.

## Entry / exit

- Entry: Home Scan (after camera grant)
- Exit: shutter/auto → Review/Edit; × → Home; Import → picker

## Guiding messages

- Low light
- Move closer / show all corners
- Hold steady
- Blurry — hold still

## ASCII

```
┌─────────────────────────┐
│ ×   Batch · 3 pages  ⚡ │
│                         │
│      ╭───────────╮      │
│     ╱  document   ╲     │
│    │    overlay    │    │
│     ╲    quad     ╱     │
│      ╰───────────╯      │
│                         │
│  ! Low light — more lit │
│                         │
│  [Import] [Auto] [Torch]│
│                         │
│       (  shutter  )     │
│                         │
│    Single  |  Batch     │
└─────────────────────────┘
```

## Acceptance

- Auto mode: stable quad + sharpness → capture
- Manual shutter always available
- Batch keeps filmstrip count; each capture goes to Review session
