# AGENTS.md — DocScan

Instructions for AI agents working in this repository.

## Product north star

DocScan (`docscan.cam`, package `cam.docscan`) is a **local-first** document scanner. Read `PRODUCT.md` and `docs/features.md` before changing scope.

## Hard constraints

- UI strings: **English only**
- Package / Bundle ID: **`cam.docscan`**
- Free: **5 page commits / UTC day** + banner ads; Pro: **$1 / $2 / $6**, unlimited saves, **ad-free**
- Custom camera + import + 7-day library suggest + **manual Preview/Edit before Save**
- Models **bundled** in the app — **no R2** model hosting
- **Never ship** CamScanner/competitor **`.so` engine binaries**; rewrite loaders/inference in DocScan native code
- CamScanner **`.data`** may be used **after** offline decrypt/normalize into our format; otherwise rewrite with commercial-OK models (see `docs/engines/`)
- Server: device identity (no DocScan accounts), billing, quota (authoritative at Save) — **no document image storage**
- Firebase Analytics + Crashlytics; **never** send doc images/OCR to Firebase — see `docs/ads-analytics.md`
- Implement UI from `docs/screens/*` after user review when changing flows
- Engine API: `docs/engines/contracts.md`

## Package map

| Path | Stack |
|------|-------|
| `ldp/` | Astro → Cloudflare Pages |
| `android/` | Kotlin, CameraX, Compose |
| `ios/` | Swift, SwiftUI, AVFoundation |
| `server/` | FastAPI, Postgres, Docker, Cloudflare Tunnel |
| `docs/` | Specs & ASCII mocks |

## Skills (on this machine)

When relevant, **read** the skill file — do not paste skill bodies into the repo:

- Domain / ADR: `~/.cursor/skills/domain-modeling/SKILL.md`
- PRD: `~/.cursor/skills/create-prd/SKILL.md`
- User stories: `~/.cursor/skills/user-stories/SKILL.md`
- Plans: `~/.cursor/skills/writing-plans/SKILL.md`
- Agent docs: `~/.cursor/skills/writing-for-agents/SKILL.md`
- UI: `~/.cursor/skills/ui-ux-pro-max/SKILL.md`, `web-design-guidelines`
- Cursor rules: `~/.cursor/skills-cursor/create-rule/SKILL.md`
- RE of CamScanner (parent workspace only, if user asks): `../.agents/skills/reverse-engineer-anything/SKILL.md`

Project rules live in `.cursor/rules/`.
