# Permissions & OAuth

Request **just-in-time** with a short in-app pre-prompt. Deny must not brick unrelated features.

## Android

| Permission / API | When | Required for |
|------------------|------|----------------|
| `CAMERA` | First open Capture | Custom camera |
| `READ_MEDIA_IMAGES` | First open **Suggest last 7 days** | Library suggest |
| Photo Picker / SAF | Import | Manual import (no `READ_MEDIA_*`) |
| `INTERNET` | Install | API, Drive, Photos, billing verify |
| `POST_NOTIFICATIONS` (33+) | Long backup | Optional progress |
| Play Billing | — | No Android permission |
| OAuth `drive.file` | Backup → Drive | Drive backup |
| OAuth `photoslibrary.appendonly` | Backup → Photos | Photos backup |

**Play Photo/Video policy:** Declare core use case — document digitization from recent photos (7-day suggest). Do not request `READ_MEDIA_VIDEO` unless needed. Keep Picker for users who deny library access.

## iOS

| Key / capability | When | Notes |
|------------------|------|-------|
| `NSCameraUsageDescription` | First Capture | Required |
| `NSPhotoLibraryUsageDescription` | First Suggest 7 days | Support **Limited Photos Library** |
| PHPicker / PhotosPicker | Import | No library permission |
| `NSPhotoLibraryAddUsageDescription` | If save-to-Photos | Optional |
| iCloud Documents | iCloud backup | Entitlement |
| Sign in with Apple | Optional account / restore | |
| StoreKit | — | No privacy string |
| Google Sign-In URL scheme | Drive/Photos | Plist + client ID |

### Suggested purpose strings (English)

- Camera: `DocScan needs the camera to scan documents.`
- Photo Library: `DocScan looks at photos from the last 7 days to suggest documents you can import and scan.`

## Flow order

1. Onboarding — explain; do not request yet
2. Scan → camera permission
3. Import → system picker (no library prompt)
4. Suggest 7 days → library permission
5. Backup → Google OAuth scopes
6. Denials → Settings deep-link CTAs; other paths remain usable
