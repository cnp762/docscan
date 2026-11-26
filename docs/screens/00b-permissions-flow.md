# Permissions flow

## Purpose

When system permission / OAuth dialogs appear; pre-prompt copy (English).

## States

| Trigger | Pre-prompt | System | If denied |
|---------|------------|--------|-----------|
| First Scan | “Camera is used to scan documents.” | CAMERA / NSCamera | Scan disabled; Import + Suggest still OK |
| Import | — | Photo Picker / PHPicker | N/A |
| Suggest 7 days | “DocScan checks photos from the last 7 days to suggest documents.” | READ_MEDIA_IMAGES / Photo Library | Hide suggest; CTA Open Settings; Import still works |
| Backup Drive | “Connect Google Drive to back up scans to a DocScan folder.” | OAuth drive.file | Backup Drive off |
| Backup Photos | “Create a DocScan album in Google Photos.” | OAuth photoslibrary.appendonly | Backup Photos off |

## ASCII — camera pre-prompt

```
┌─────────────────────────┐
│                         │
│      📷                 │
│  Allow camera access?   │
│  Needed to scan pages.  │
│                         │
│  [ Not now ]  [ Continue]│
└─────────────────────────┘
         │ Continue
         ▼
   System permission sheet
```

## ASCII — library suggest pre-prompt

```
┌─────────────────────────┐
│  Find recent documents  │
│                         │
│  We'll look at photos   │
│  from the last 7 days   │
│  and suggest scans.     │
│  Nothing is uploaded.   │
│                         │
│  [ Not now ]  [ Allow ] │
└─────────────────────────┘
```

## Acceptance

- No permission requests on cold launch / onboarding finish alone
- Limited Photos Library still filters to last 7 days within allowed set
