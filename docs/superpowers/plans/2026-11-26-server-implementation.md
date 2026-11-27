# DocScan Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the in-memory FastAPI stubs in `server/` with a production-ready, Postgres-backed API supporting device registration + JWT auth, atomic UTC daily quota enforcement, Apple StoreKit 2 / Google Play purchase verification, and in-app device data deletion.

**Architecture:** FastAPI async service (`app/`) using SQLAlchemy 2.0 async + `asyncpg` + Alembic migrations against PostgreSQL 16. Device identity uses `X-Device-Id` with a signed JWT issued on registration; quota consumption uses row-level locking (`SELECT ... FOR UPDATE`) on `(device_id, utc_day)` to prevent race conditions. Store verification is abstracted behind a `StoreVerifier` interface with Apple JWS / Google Play API implementations and a configurable stub mode for local development/testing.

**Tech Stack:** Python 3.12, FastAPI, SQLAlchemy 2.0 (async), asyncpg, Alembic, python-jose, httpx, pydantic-settings, pytest, pytest-asyncio, PostgreSQL 16, Docker Compose, Cloudflare Tunnel.

**Spec:** `PRODUCT.md`, `docs/billing-quota.md`, `docs/screens/10-settings.md`, `.cursor/rules/40-server.mdc`

## Global Constraints

- **No DocScan user accounts:** Subject is `device:{device_id}` (`X-Device-Id` header / Bearer JWT).
- **No document image uploads or OCR storage** in MVP.
- **Free tier:** Exactly **5 page commits per UTC calendar day**; authoritative check happens at Save via `POST /v1/quota/consume`. Insufficient quota returns **HTTP 402** (`detail: "quota_exceeded"`).
- **Pro plans:** `pro_monthly` ($1), `pro_quarterly` ($2), `pro_yearly` ($6) — unlimited saves while entitled.
- **Store IDs:** `apple_bundle_id = "cam.docscan"`, `google_play_package_name = "cam.docscan"`.
- **Store compliance:** `DELETE /v1/devices/me` must delete device, quota, and entitlement/link rows for that device ID (Apple 5.1.1(v) / Play account deletion).
- **Migrations:** All schema changes via Alembic; no secrets committed in images or compose files.

## Review Focus

1. **Concurrent Save race condition:** Two simultaneous `POST /v1/quota/consume` requests with `n=3` when `used=0` must not both succeed (total must never exceed 5). Pinned by concurrency/row-lock test in Task 4.
2. **UTC day rollover:** Quota at `2026-11-26T23:59:59Z` vs `2026-11-27T00:00:00Z` must use separate daily buckets. Pinned by date-boundary test in Task 4.
3. **Expired Pro entitlement:** A device with an expired `expires_at` in `entitlements` must be treated as Free (`is_pro=False`, `limit=5`), not Pro. Pinned by entitlement expiry test in Task 5.
4. **Restore Purchases across reinstall:** A new `device_id` presenting an existing valid store `original_transaction_id` / `purchase_token` must inherit the active Pro entitlement. Pinned by restore test in Task 5.
5. **Delete device data completeness:** Calling `DELETE /v1/devices/me` removes the device, its quota ledger rows, and its device-entitlement links, and subsequent requests with the old token fail with 401/404. Pinned by deletion test in Task 3 & 4.

---

## File Structure

- Modify: `server/pyproject.toml` — add dev dependencies (`pytest`, `pytest-asyncio`, `ruff`) and pytest config
- Modify: `server/app/config.py` — add `billing_stub_mode`, `apple_*`, `google_play_*`, `jwt_algorithm`, `jwt_expire_days`
- Create: `server/app/db.py` — async SQLAlchemy engine, `async_sessionmaker`, `Base`, `get_db` dependency
- Create: `server/app/models.py` — ORM models (`Device`, `QuotaDaily`, `Entitlement`, `DeviceEntitlement`)
- Create: `server/alembic.ini` & `server/alembic/env.py` + initial migration — Postgres schema
- Create: `server/app/auth.py` — JWT creation/verification, `get_current_device_id` dependency (supports Bearer JWT or `X-Device-Id` with strict validation)
- Create: `server/app/routers/health.py` — `GET /health` with DB `SELECT 1` check
- Create: `server/app/routers/devices.py` — `POST /v1/devices/register`, `DELETE /v1/devices/me`
- Create: `server/app/routers/quota.py` — `GET /v1/quota/today`, `POST /v1/quota/consume`
- Create: `server/app/services/store_verifier.py` — Apple App Store JWS & Google Play Billing verification + stub mode
- Create: `server/app/routers/billing.py` — `POST /v1/billing/verify`, `GET /v1/billing/entitlement`
- Modify: `server/app/main.py` — wire routers, lifespan, CORS/error handlers
- Modify: `server/Dockerfile` — run Alembic migrations or entrypoint + uvicorn
- Create: `server/tests/conftest.py` & `server/tests/test_*.py` — full async API test suite

---

### Task 1: Project Setup, Config & Database Foundation

**Files:**
- Modify: `server/pyproject.toml`
- Modify: `server/app/config.py`
- Create: `server/app/db.py`
- Create: `server/app/models.py`
- Create: `server/tests/conftest.py`

**Interfaces:**
- Produces:
  - `app.db.Base` (SQLAlchemy DeclarativeBase)
  - `app.db.get_db() -> AsyncGenerator[AsyncSession, None]`
  - `app.models.Device`, `QuotaDaily`, `Entitlement`, `DeviceEntitlement`

- [ ] **Step 1: Add dev dependencies to `server/pyproject.toml`**
  Add `[project.optional-dependencies]` with `dev = ["pytest>=8.3.0", "pytest-asyncio>=0.24.0", "ruff>=0.8.0"]` and `[tool.pytest.ini_options] asyncio_mode = "auto"`.
- [ ] **Step 2: Expand `server/app/config.py`**
  Add `jwt_algorithm: str = "HS256"`, `billing_stub_mode: bool = True` (default True for local dev, False in prod), `apple_issuer_id: str = ""`, `apple_key_id: str = ""`, `apple_private_key: str = ""`, `google_play_service_account_json: str = ""`.
- [ ] **Step 3: Create `server/app/db.py` and `server/app/models.py`**
  Define:
  - `Device(device_id PK str, platform str, app_version str|None, created_at timestamptz, updated_at timestamptz)`
  - `QuotaDaily(device_id FK CASCADE, utc_day date, used int, PK(device_id, utc_day))`
  - `Entitlement(id PK uuid, store str, original_tx_id str UNIQUE, product_id str, is_pro bool, expires_at timestamptz|None, updated_at timestamptz)`
  - `DeviceEntitlement(device_id FK CASCADE, entitlement_id FK CASCADE, PK(device_id, entitlement_id))`
- [ ] **Step 4: Write smoke test for DB models & session in `server/tests/test_models.py`**
  Run `pytest tests/test_models.py -v` and verify PASS.
- [ ] **Step 5: Commit**

---

### Task 2: Alembic Migrations & Health Endpoint

**Files:**
- Create: `server/alembic.ini`
- Create: `server/alembic/env.py`
- Create: `server/alembic/versions/0001_initial_schema.py`
- Create: `server/app/routers/health.py`
- Modify: `server/app/main.py`
- Test: `server/tests/test_health.py`

**Interfaces:**
- Produces: `GET /health` returning `{"ok": true, "service": "docscan-api", "db": "ok", "host": ...}` (503 if DB unreachable).

- [ ] **Step 1: Write failing test `server/tests/test_health.py`**
  Test `GET /health` returns 200 with `"db": "ok"`.
- [ ] **Step 2: Initialize Alembic async template and generate `0001_initial_schema.py`**
- [ ] **Step 3: Implement `server/app/routers/health.py` and mount in `app/main.py`**
- [ ] **Step 4: Run `alembic upgrade head` against local Postgres & run `pytest tests/test_health.py -v`**
- [ ] **Step 5: Commit**

---

### Task 3: Device Registration, JWT Auth & Device Deletion

**Files:**
- Create: `server/app/auth.py`
- Create: `server/app/routers/devices.py`
- Modify: `server/app/main.py`
- Test: `server/tests/test_devices.py`

**Interfaces:**
- Produces:
  - `app.auth.create_device_token(device_id: str) -> str`
  - `app.auth.get_device_id(x_device_id: str | None, authorization: str | None) -> str`
  - `POST /v1/devices/register` -> `{device_id, platform, token, registered_at}`
  - `DELETE /v1/devices/me` -> `{ok: true, deleted_device_id}` (deletes `Device` row; cascades to `QuotaDaily` and `DeviceEntitlement`).

- [ ] **Step 1: Write failing tests in `server/tests/test_devices.py`**
  - Register new device returns JWT token; re-registering same `device_id` upserts `app_version` and returns valid token.
  - Invalid `platform` or short `device_id` returns 422.
  - `DELETE /v1/devices/me` with valid Bearer token (or `X-Device-Id`) deletes device + cascades quota/entitlement links; second `DELETE` returns 404.
- [ ] **Step 2: Run tests to verify failure**
- [ ] **Step 3: Implement `server/app/auth.py` and `server/app/routers/devices.py`**
- [ ] **Step 4: Run `pytest tests/test_devices.py -v` and verify PASS**
- [ ] **Step 5: Commit**

---

### Task 4: Authoritative UTC Daily Quota (`GET /v1/quota/today`, `POST /v1/quota/consume`)

**Files:**
- Create: `server/app/routers/quota.py`
- Modify: `server/app/main.py`
- Test: `server/tests/test_quota.py`

**Interfaces:**
- Consumes: `app.auth.get_device_id`, `app.models.QuotaDaily`, `app.models.Entitlement`, `app.models.DeviceEntitlement`
- Produces:
  - `GET /v1/quota/today` -> `{utc_day, used, limit, remaining, is_pro}`
  - `POST /v1/quota/consume` (`{n: 1..50}`) -> `{ok: true, used, remaining}` or `{ok: true, is_pro: true, consumed: n}`, or **402** `{"detail": "quota_exceeded"}`

- [ ] **Step 1: Write failing tests in `server/tests/test_quota.py`**
  - Fresh device has `used=0, limit=5, remaining=5, is_pro=False`.
  - Consume `n=3` then `n=2` succeeds (`remaining=0`).
  - Consume `n=1` when `used=5` returns **402** `quota_exceeded` and leaves `used=5`.
  - Consume `n=3` when `used=3` returns **402** `quota_exceeded` (atomic — no partial consume).
  - Active Pro entitlement bypasses limit (`is_pro=True`, `limit=None`, `remaining=None`).
  - UTC day rollover uses a fresh bucket for the next UTC date.
  - Concurrent consume calls (`asyncio.gather`) respect the 5/day cap without race conditions.
- [ ] **Step 2: Run tests to verify failure**
- [ ] **Step 3: Implement `server/app/routers/quota.py`**
  Use `SELECT ... FOR UPDATE` inside a DB transaction on `QuotaDaily` (creating the row with `ON CONFLICT DO NOTHING` first if missing) so concurrent requests serialize cleanly. Auto-register `Device` if called by a legacy client with `X-Device-Id` only, or require valid device row.
- [ ] **Step 4: Run `pytest tests/test_quota.py -v` and verify PASS**
- [ ] **Step 5: Commit**

---

### Task 5: Billing Verification & Entitlement (`POST /v1/billing/verify`, `GET /v1/billing/entitlement`)

**Files:**
- Create: `server/app/services/store_verifier.py`
- Create: `server/app/routers/billing.py`
- Modify: `server/app/main.py`
- Test: `server/tests/test_billing.py`

**Interfaces:**
- Produces:
  - `StoreVerifier.verify(store, product_id, receipt_or_token) -> VerifiedPurchase(original_tx_id, product_id, is_pro, expires_at)`
  - `POST /v1/billing/verify` -> `EntitlementOut(is_pro, product_id, expires_at)`
  - `GET /v1/billing/entitlement` -> `EntitlementOut(is_pro, product_id, expires_at)`

- [ ] **Step 1: Write failing tests in `server/tests/test_billing.py`**
  - Valid `product_id` (`pro_monthly`, `pro_quarterly`, `pro_yearly`) marks device Pro and persists to Postgres.
  - Invalid `product_id` returns 422.
  - Expired `expires_at` causes `GET /v1/billing/entitlement` to return `is_pro=False`.
  - Restore Purchases: Device B verifying the same `original_tx_id` links to the same `Entitlement` and becomes Pro.
  - Real verifier unit tests (mocking `httpx` responses for Apple JWS / Google Play Subscriptions v2 API).
- [ ] **Step 2: Run tests to verify failure**
- [ ] **Step 3: Implement `store_verifier.py` & `routers/billing.py`**
  Validate `product_id` against `{"pro_monthly", "pro_quarterly", "pro_yearly"}`. Implement stub verifier (active when `settings.billing_stub_mode=True`) and production Apple App Store Server API + Google Play `purchases.subscriptionsv2.get` verifier.
- [ ] **Step 4: Run `pytest tests/test_billing.py -v` and verify PASS**
- [ ] **Step 5: Commit**

---

### Task 6: Docker Compose, Production Hardening & Full Verification

**Files:**
- Modify: `server/Dockerfile`
- Modify: `server/docker-compose.yml`
- Modify: `server/README.md`

- [ ] **Step 1: Update `Dockerfile` to copy `alembic.ini` and `alembic/` and run `alembic upgrade head` before `uvicorn`**
- [ ] **Step 2: Verify `docker compose up --build` starts Postgres + API cleanly and passes `/health`**
- [ ] **Step 3: Run full test suite & linter:**
  - `pytest -v` (all tests pass, exit code 0)
  - `ruff check app tests` (exit code 0)
- [ ] **Step 4: Commit**
