# DocScan Landing Page (LDP) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the `docscan.cam` Astro static site (`ldp/`) so it meets App Store / Google Play compliance requirements (Privacy Policy naming Firebase + AdMob, Terms, Data Deletion instructions, `app-ads.txt`), aligns copy with `PRODUCT.md` and `docs/screens/09-paywall-pro.md`, and is ready to deploy to Cloudflare Pages.

**Architecture:** Static Astro 5 site (`output: 'static'`) with shared `BaseLayout.astro`, SEO/OpenGraph metadata, modular pages (`index.astro`, `privacy.astro`, `terms.astro`, `delete-data.astro`), and static compliance assets in `public/` (`app-ads.txt`, `robots.txt`, `favicon.svg`). Configurable `PUBLIC_*` store URLs via `import.meta.env`.

**Tech Stack:** Astro 5.7, TypeScript/HTML/CSS, Wrangler (Cloudflare Pages).

**Spec:** `PRODUCT.md`, `docs/ads-analytics.md`, `docs/screens/09-paywall-pro.md`, `docs/screens/10-settings.md`, `docs/store-upload.md`, `.cursor/rules/50-ldp.mdc`

## Global Constraints

- **UI/Copy language:** English only.
- **Domain & Package:** `docscan.cam`, Package/Bundle ID `cam.docscan`.
- **Pricing accuracy:** Free = 5 page commits / UTC day + banner ads; Pro = **$1/month**, **$2/3 months**, **$6/year** — unlimited saves, **ad-free**.
- **Feature honesty:** Do **not** market OCR, Doc Clean, or export formats as Pro-only unlocks (they are available on Free).
- **Privacy honesty:** Privacy Policy **must** explicitly disclose Firebase Analytics, Firebase Crashlytics, and Google AdMob (Free tier); do **not** claim "zero telemetry" or "zero cloud". Scope the privacy promise to document images and OCR content (*"We never send your scans or their contents to DocScan."*).
- **Env vars:** Only `PUBLIC_*` env vars in client code; no secrets in `wrangler.toml` or `src/`.

## Review Focus

1. **Store compliance links:** `/privacy`, `/terms`, and `/delete-data` must all resolve with HTTP 200 (no 404s from footer or app Settings links).
2. **AdMob verification:** `/app-ads.txt` must be served at the root of `docscan.cam` (`ldp/public/app-ads.txt`).
3. **Pro vs Free copy accuracy:** Pricing cards must clearly show "Ad-free" on Pro and "Banner ads" on Free, and must not imply OCR/export are Pro-only.
4. **Placeholder store links:** If `PUBLIC_APP_STORE_URL` / `PUBLIC_PLAY_STORE_URL` are not set yet, store badges should gracefully show a "Coming soon" state or link to `#pricing` instead of broken `href="#"` links.
5. **Accessibility & mobile layout:** Semantic landmarks (`header`, `main`, `footer`), sufficient color contrast, readable font sizes, and no horizontal overflow at 375px viewport width.

---

## File Structure

- Create: `ldp/public/app-ads.txt` — AdMob authorized seller placeholder/template (with `PUBLIC_` / comment instructions for publisher ID)
- Create: `ldp/public/robots.txt` — Allow indexing + sitemap pointer
- Create: `ldp/public/favicon.svg` — Clean DocScan SVG icon
- Create: `ldp/src/pages/delete-data.astro` — Public data-deletion instructions page for Google Play Data Safety & Apple 5.1.1(v)
- Modify: `ldp/src/layouts/BaseLayout.astro` — Add canonical URL, OpenGraph/Twitter meta tags, favicon link
- Modify: `ldp/src/pages/index.astro` — Add Store badges (App Store / Google Play via `PUBLIC_*` env), update Free vs Pro pricing copy (ad-free on Pro, banner ads on Free), add footer link to `/delete-data`
- Modify: `ldp/src/pages/privacy.astro` — Update to explicitly name Firebase Analytics, Firebase Crashlytics, Google AdMob, and in-app data deletion
- Modify: `ldp/src/pages/terms.astro` — Align subscription auto-renewal, store billing, and deletion terms
- Modify: `ldp/README.md` — Document `PUBLIC_*` env vars, `app-ads.txt` setup, and Wrangler deploy command

---

### Task 1: Static Compliance & SEO Assets (`public/` + `BaseLayout.astro`)

**Files:**
- Create: `ldp/public/favicon.svg`
- Create: `ldp/public/robots.txt`
- Create: `ldp/public/app-ads.txt`
- Modify: `ldp/src/layouts/BaseLayout.astro`

- [ ] **Step 1: Add `ldp/public/favicon.svg` and `ldp/public/robots.txt`**
  `robots.txt` allows all user-agents (`Allow: /`).
- [ ] **Step 2: Add `ldp/public/app-ads.txt`**
  Include standard `google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0` template with clear comment to replace `pub-XXXXXXXXXXXXXXXX` before AdMob production launch.
- [ ] **Step 3: Update `ldp/src/layouts/BaseLayout.astro`**
  Add `<link rel="icon" type="image/svg+xml" href="/favicon.svg" />`, `<link rel="canonical" href={canonicalUrl} />`, and OpenGraph (`og:title`, `og:description`, `og:type`, `og:url`) meta tags.
- [ ] **Step 4: Run `npm run build` in `ldp/` and verify `dist/favicon.svg`, `dist/robots.txt`, `dist/app-ads.txt` exist**
- [ ] **Step 5: Commit**

---

### Task 2: Store-Compliant Legal & Data Deletion Pages (`/privacy`, `/terms`, `/delete-data`)

**Files:**
- Modify: `ldp/src/pages/privacy.astro`
- Modify: `ldp/src/pages/terms.astro`
- Create: `ldp/src/pages/delete-data.astro`

- [ ] **Step 1: Update `ldp/src/pages/privacy.astro`**
  - Keep scoped promise: *"We never send your scans or their contents to DocScan."*
  - Add explicit sections for:
    - **Analytics & Crash Reporting:** Firebase Analytics and Firebase Crashlytics (collects app usage events and crash diagnostics; never document images or OCR text).
    - **Advertising (Free Tier):** Google AdMob banner ads on Free; Pro is ad-free. Link/note non-personalized ads when ATT is declined on iOS.
    - **Data Deletion:** Link to `/delete-data` and explain `Settings → Data → Delete account & data`.
- [ ] **Step 2: Update `ldp/src/pages/terms.astro`**
  - Include subscription auto-renewal disclosure, cancellation via App Store / Google Play, and note that deleting app data does not automatically cancel an active store subscription.
- [ ] **Step 3: Create `ldp/src/pages/delete-data.astro`**
  - Clear step-by-step instructions for in-app deletion (`Settings → Data → Delete account & data`), what is deleted (local library, backup OAuth tokens, server device/quota/entitlement rows), what is not deleted (remote Drive/iCloud/Photos backups, active store subscription), and fallback support contact (`privacy@docscan.cam`).
- [ ] **Step 4: Run `npm run build` and verify `dist/privacy/index.html`, `dist/terms/index.html`, `dist/delete-data/index.html` generate cleanly**
- [ ] **Step 5: Commit**

---

### Task 3: Landing Page (`index.astro`) Store Badges, Pricing Alignment & Responsive Polish

**Files:**
- Modify: `ldp/src/pages/index.astro`
- Modify: `ldp/README.md`

- [ ] **Step 1: Update pricing section in `ldp/src/pages/index.astro`**
  - Free: `$0` — `5 page commits / UTC day`, `Includes banner ads`, `Full OCR & export included`.
  - Pro monthly: `$1` — `Unlimited saves`, `Ad-free`.
  - Pro 3 months: `$2` — `Unlimited saves`, `Ad-free`.
  - Pro yearly: `$6` — `Unlimited saves`, `Ad-free` (highlighted as best value).
- [ ] **Step 2: Add App Store & Google Play download section / badges**
  Read `import.meta.env.PUBLIC_APP_STORE_URL` and `import.meta.env.PUBLIC_PLAY_STORE_URL`. Render store links when configured, or clean disabled/coming-soon badges pointing to `cam.docscan` when unset.
- [ ] **Step 3: Update footer navigation**
  Include links to `/privacy`, `/terms`, `/delete-data`, and `mailto:support@docscan.cam`.
- [ ] **Step 4: Update `ldp/README.md`**
  Document `PUBLIC_APP_STORE_URL`, `PUBLIC_PLAY_STORE_URL`, `app-ads.txt`, and Wrangler deploy steps.
- [ ] **Step 5: Run `npm run build` and `npx astro check` (if configured) in `ldp/`, verify zero errors**
- [ ] **Step 6: Commit**
