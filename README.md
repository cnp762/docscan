# DocScan

On-device document scanner for Android & iOS. Local-first library, OCR, export, and optional cloud backup.

| | |
|--|--|
| Product | DocScan |
| Domain | [docscan.cam](https://docscan.cam) |
| API | `https://api.docscan.cam` |
| Android / iOS ID | `cam.docscan` |
| Repo | https://github.com/cnp762/docscan |

## Monorepo layout

```
ldp/        Astro landing page → Cloudflare Pages
android/    Native Kotlin (CameraX)
ios/        Native Swift (AVFoundation)
server/     Python FastAPI + Postgres (Docker + Cloudflare Tunnel)
docs/       Product specs, screen mocks (ASCII), ADRs
```

## Quick start

### Prerequisites

- Node 20+ (LDP)
- Android Studio / JDK 17 (Android)
- Xcode 15+ (iOS)
- Docker (server)
- Copy `.env.example` → `.env` and fill secrets locally

### LDP

```bash
cd ldp && npm install && npm run dev
# Deploy: npm run deploy  (Wrangler → Cloudflare Pages)
```

### Android

```bash
cd android
./gradlew assembleDebug
# Release: configure keystore.properties (gitignored), then
./gradlew assembleRelease
# Prefer AAB for Play: ./gradlew bundleRelease
```

Full Play / App Store / Pages / Tunnel steps: [docs/store-upload.md](docs/store-upload.md).

### iOS

Open `ios/DocScan.xcodeproj` in Xcode, select team, run on device/simulator.

Archive → Organizer → Distribute, or see [docs/store-upload.md](docs/store-upload.md) for TestFlight / ASC API key flow.

### Server

```bash
cd server
cp ../.env.example .env   # or symlink root .env
docker compose up --build
# API health: http://localhost:8000/health
```

Cloudflare Tunnel: set `CLOUDFLARE_TUNNEL_TOKEN` and use the `tunnel` service (see `server/README.md`).

## Product constraints (do not drift)

- Free: **5 page commits / UTC day**; Pro: **$1/mo**, **$2/3mo**, **$6/yr**
- Custom camera + import + 7-day library suggest + manual preview/edit before save
- Models **bundled** in the app (no R2 download)
- UI strings: **English only**; OCR has auto language detection
- Server: auth, billing entitlement, quota — **not** document image storage
- Package IDs: **`cam.docscan`**

Screen mocks for UI review: [`docs/screens/`](docs/screens/).

## License

Proprietary — all rights reserved unless otherwise noted in `NOTICE` for third-party models.
