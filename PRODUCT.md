# DocScan — Product

## Summary

DocScan digitizes paper on-device: custom camera capture, import from device, 7-day photo library suggestions, manual preview/edit, OCR with language detection, tagging, generative summary, local library, export (images / searchable PDF / DOCX), print/share, and optional backup to Google Drive, iCloud Drive, and Google Photos.

## Brand

| | |
|--|--|
| Name | DocScan |
| Domain | `docscan.cam` |
| Package / Bundle ID | `cam.docscan` |
| API | `api.docscan.cam` |
| UI language | English only |

## Objectives

- Fast, guided document capture without leaving the device for core processing
- Local-first storage of images and OCR text
- Clear free tier (5 page commits / UTC day) with affordable Pro
- Production-quality OCR/lang-detect/summary using commercially licensed models bundled in the app

## Non-goals (MVP)

- Multi-device live sync of document content
- Remote model download (R2)
- CamScanner or other competitor binaries
- UI locales other than English
- Desktop apps
- PDF import (export is in scope)

## Monetization

| Plan | Price / notes |
|------|----------------|
| Free | 5 page commits per UTC calendar day + small banner ads (Library / Search / Detail) |
| Pro monthly | $1 — unlimited saves, **no ads** |
| Pro quarterly (3 months) | $2 — unlimited saves, **no ads** |
| Pro yearly | $6 — unlimited saves, **no ads** |

Quota unit: each **page committed** to the library after Review/Edit → Save (camera, import, or library suggest). Re-edit of saved pages does not consume quota.

Telemetry: Firebase Analytics + Crashlytics (events only; never document images or OCR). Ads/analytics detail: `docs/ads-analytics.md`.

## Core journeys

1. Capture (single/batch) → Preview/Edit → Save → Library
2. Import / Suggest last 7 days → Preview/Edit → Save
3. Library → Detail → Copy OCR / Export / Print / Backup
4. Hit free limit → Paywall → StoreKit 2 / Play Billing → unlimited scans while entitled

## Server role

Device identity (no DocScan accounts), subscription entitlement, daily quota ledger (authoritative at Save). Product analytics/crashes via Firebase on-device SDKs (not DocScan server). **No document image upload** in MVP.
