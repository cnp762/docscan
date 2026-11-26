# DocScan server

Python FastAPI API for device auth, Play/App Store purchase verification stubs, and UTC daily quota.

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

## Tunnel

1. Create a Cloudflare Tunnel routing `api.docscan.cam` → `http://api:8000` (or host localhost:8000)
2. Set `CLOUDFLARE_TUNNEL_TOKEN` in `.env`
3. `docker compose --profile tunnel up`

## Notes

- MVP does **not** accept document image uploads
- Replace in-memory quota/entitlement with Postgres + Alembic in the next slice
- Wire real Apple JWS / Google Play Developer API verification before production
