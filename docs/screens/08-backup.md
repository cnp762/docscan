# Backup

## Purpose

Connect and sync to Google Drive, Google Photos, and (iOS) iCloud Drive.

## ASCII

```
┌─────────────────────────┐
│ ←  Backup               │
│                         │
│ Google Drive      [On]  │
│  Folder: DocScan/       │
│  Last sync: 2m ago      │
│  [ Sync now ]           │
│                         │
│ Google Photos     [Off] │
│  Album: DocScan         │
│  [ Connect ]            │
│                         │
│ iCloud Drive      [On]  │  ← iOS only
│  Last sync: today       │
│                         │
│ Scope: ○ This doc       │
│        ● Entire library │
└─────────────────────────┘
```

## Acceptance

- Drive mirrors local folder hierarchy
- Photos uses app-created album only
- Errors show retry; local files never deleted on backup failure
- Disconnect revokes tokens locally
