from datetime import UTC, datetime, timedelta
from unittest.mock import AsyncMock, patch

from app.models import DeviceEntitlement, Entitlement


async def _register(client, device_id: str) -> dict[str, str]:
    resp = await client.post(
        "/v1/devices/register",
        json={"device_id": device_id, "platform": "ios"},
    )
    assert resp.status_code == 200
    return {"Authorization": f"Bearer {resp.json()['token']}"}


async def test_billing_verify_and_entitlement_persists(client):
    headers = await _register(client, "bill-device-001")

    ent0 = await client.get("/v1/billing/entitlement", headers=headers)
    assert ent0.status_code == 200
    assert ent0.json()["is_pro"] is False

    verify = await client.post(
        "/v1/billing/verify",
        headers=headers,
        json={
            "store": "apple",
            "product_id": "pro_yearly",
            "receipt_or_token": "stub-tx-yearly-001",
        },
    )
    assert verify.status_code == 200
    v_data = verify.json()
    assert v_data["is_pro"] is True
    assert v_data["product_id"] == "pro_yearly"
    assert v_data["expires_at"] is not None

    ent1 = await client.get("/v1/billing/entitlement", headers=headers)
    assert ent1.status_code == 200
    assert ent1.json()["is_pro"] is True
    assert ent1.json()["product_id"] == "pro_yearly"


async def test_billing_rejects_unknown_product_id(client):
    headers = await _register(client, "bill-device-002")
    resp = await client.post(
        "/v1/billing/verify",
        headers=headers,
        json={
            "store": "google",
            "product_id": "pro_lifetime",
            "receipt_or_token": "tok-123",
        },
    )
    assert resp.status_code == 422


async def test_billing_expired_entitlement_returns_free(client, db_session):
    headers = await _register(client, "bill-expired-001")
    ent = Entitlement(
        store="google",
        original_tx_id="tx-expired-001",
        product_id="pro_monthly",
        is_pro=True,
        expires_at=datetime.now(UTC) - timedelta(hours=1),
    )
    db_session.add(ent)
    await db_session.flush()
    db_session.add(DeviceEntitlement(device_id="bill-expired-001", entitlement_id=ent.id))
    await db_session.commit()

    resp = await client.get("/v1/billing/entitlement", headers=headers)
    assert resp.status_code == 200
    assert resp.json()["is_pro"] is False


async def test_billing_restore_purchases_across_devices(client):
    h1 = await _register(client, "bill-restore-a")
    h2 = await _register(client, "bill-restore-b")

    r1 = await client.post(
        "/v1/billing/verify",
        headers=h1,
        json={
            "store": "apple",
            "product_id": "pro_quarterly",
            "receipt_or_token": "shared-original-tx-999",
        },
    )
    assert r1.status_code == 200
    assert r1.json()["is_pro"] is True

    # Device B restores the same purchase token
    r2 = await client.post(
        "/v1/billing/verify",
        headers=h2,
        json={
            "store": "apple",
            "product_id": "pro_quarterly",
            "receipt_or_token": "shared-original-tx-999",
        },
    )
    assert r2.status_code == 200
    assert r2.json()["is_pro"] is True

    ent_b = await client.get("/v1/billing/entitlement", headers=h2)
    assert ent_b.json()["is_pro"] is True


async def test_billing_production_verifier_failure_returns_402(client):
    headers = await _register(client, "bill-prod-001")
    with patch("app.routers.billing.settings.billing_stub_mode", False), patch(
        "app.services.store_verifier.verify_store_purchase",
        new=AsyncMock(side_effect=ValueError("invalid_receipt")),
    ):
        resp = await client.post(
            "/v1/billing/verify",
            headers=headers,
            json={
                "store": "apple",
                "product_id": "pro_monthly",
                "receipt_or_token": "bad-jws",
            },
        )
        assert resp.status_code == 402
        assert resp.json()["detail"] == "invalid_receipt"
