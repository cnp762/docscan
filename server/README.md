# DocScan server

Python FastAPI API for device-ID identity (no user accounts), Play/App Store purchase verification, and authoritative UTC daily quota.

Public host target: **https://api.docscan.cam** (Cloudflare Tunnel).

## Run locally

```bash
docker compose up --build
curl http://localhost:8000/health
```

Quota example:

```bash
curl -s http://localhost:8000/v1/quota/today -H 'X-Device-Id: test-device-001'
curl -s -X POST http://localhost:8000/v1/quota/consume \
  -H 'Content-Type: application/json' \
  -H 'X-Device-Id: test-device-001' \
  -d '{"n":1}'
```

## Endpoints

- `GET /health` — service + Postgres health check
- `POST /v1/devices/register` — register/upsert device and issue Bearer JWT
- `DELETE /v1/devices/me` — delete device + cascade quota/entitlement links (Apple 5.1.1(v) / Play deletion)
- `GET /v1/quota/today` — current UTC day usage & Pro status
- `POST /v1/quota/consume` — atomic quota consume (`402 quota_exceeded` when free limit 5 reached)
- `POST /v1/billing/verify` — verify Apple/Google purchase (`BILLING_STUB_MODE=false` for production)
- `GET /v1/billing/entitlement` — active Pro entitlement status

## Tests & lint

```bash
docker compose up -d db
.venv/Scripts/pytest -v
.venv/Scripts/ruff check app tests
```

## Tunnel

1. Create a Cloudflare Tunnel routing `api.docscan.cam` → `http://api:8000` (or host localhost:8000)
2. Set `CLOUDFLARE_TUNNEL_TOKEN` in `.env`
3. `docker compose --profile tunnel up`

## Notes

- MVP does **not** accept document image uploads
- Configure `APPLE_ISSUER_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`, `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`, and `BILLING_STUB_MODE=false` before production
