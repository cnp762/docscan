import asyncio
from datetime import UTC, date, datetime, timedelta
from unittest.mock import patch

from sqlalchemy import select

from app.models import DeviceEntitlement, Entitlement, QuotaDaily


async def _register(client, device_id: str = "quota-device-001") -> dict[str, str]:
    resp = await client.post(
        "/v1/devices/register",
        json={"device_id": device_id, "platform": "android"},
    )
    assert resp.status_code == 200
    return {"Authorization": f"Bearer {resp.json()['token']}"}


async def test_quota_today_fresh_device(client):
    headers = await _register(client, "quota-fresh-001")
    resp = await client.get("/v1/quota/today", headers=headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["used"] == 0
    assert data["limit"] == 5
    assert data["remaining"] == 5
    assert data["is_pro"] is False


async def test_quota_consume_and_exceed_returns_402(client):
    headers = await _register(client, "quota-limit-001")

    r1 = await client.post("/v1/quota/consume", json={"n": 3}, headers=headers)
    assert r1.status_code == 200
    assert r1.json() == {"ok": True, "used": 3, "remaining": 2}

    # Atomic rejection: requesting 3 when only 2 remain fails with 402 and does not mutate used
    r_fail = await client.post("/v1/quota/consume", json={"n": 3}, headers=headers)
    assert r_fail.status_code == 402
    assert r_fail.json()["detail"] == "quota_exceeded"

    r2 = await client.post("/v1/quota/consume", json={"n": 2}, headers=headers)
    assert r2.status_code == 200
    assert r2.json() == {"ok": True, "used": 5, "remaining": 0}

    r_fail2 = await client.post("/v1/quota/consume", json={"n": 1}, headers=headers)
    assert r_fail2.status_code == 402


async def test_quota_persists_in_postgres(client, db_session):
    headers = await _register(client, "quota-persist-001")
    await client.post("/v1/quota/consume", json={"n": 2}, headers=headers)

    row = (
        await db_session.execute(
            select(QuotaDaily).where(QuotaDaily.device_id == "quota-persist-001")
        )
    ).scalar_one_or_none()
    assert row is not None
    assert row.used == 2


async def test_quota_utc_day_rollover(client):
    headers = await _register(client, "quota-rollover-001")

    with patch("app.routers.quota.utc_today", return_value=date(2026, 11, 26)):
        r1 = await client.post("/v1/quota/consume", json={"n": 5}, headers=headers)
        assert r1.status_code == 200

    with patch("app.routers.quota.utc_today", return_value=date(2026, 11, 27)):
        r_today = await client.get("/v1/quota/today", headers=headers)
        assert r_today.status_code == 200
        assert r_today.json()["used"] == 0
        assert r_today.json()["remaining"] == 5


async def test_quota_pro_bypasses_limit(client, db_session):
    headers = await _register(client, "quota-pro-001")
    ent = Entitlement(
        store="apple",
        original_tx_id="tx-pro-001",
        product_id="pro_yearly",
        is_pro=True,
        expires_at=datetime.now(UTC) + timedelta(days=30),
    )
    db_session.add(ent)
    await db_session.flush()
    db_session.add(DeviceEntitlement(device_id="quota-pro-001", entitlement_id=ent.id))
    await db_session.commit()

    r_today = await client.get("/v1/quota/today", headers=headers)
    assert r_today.status_code == 200
    assert r_today.json()["is_pro"] is True
    assert r_today.json()["limit"] is None

    r_consume = await client.post("/v1/quota/consume", json={"n": 25}, headers=headers)
    assert r_consume.status_code == 200
    assert r_consume.json() == {"ok": True, "is_pro": True, "consumed": 25}


async def test_quota_concurrent_consume_respects_limit(client):
    headers = await _register(client, "quota-concurrent-001")

    results = await asyncio.gather(
        client.post("/v1/quota/consume", json={"n": 3}, headers=headers),
        client.post("/v1/quota/consume", json={"n": 3}, headers=headers),
    )
    statuses = sorted(r.status_code for r in results)
    assert statuses == [200, 402]
