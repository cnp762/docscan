async def test_register_device_creates_and_upserts(client):
    resp = await client.post(
        "/v1/devices/register",
        json={"device_id": "device-abc-12345", "platform": "android", "app_version": "1.0.0"},
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["device_id"] == "device-abc-12345"
    assert data["platform"] == "android"
    assert data["token"]
    assert "registered_at" in data

    # Re-registering updates app_version and returns a valid JWT
    resp2 = await client.post(
        "/v1/devices/register",
        json={"device_id": "device-abc-12345", "platform": "android", "app_version": "1.0.1"},
    )
    assert resp2.status_code == 200
    assert resp2.json()["token"]


async def test_register_device_validation_error(client):
    resp = await client.post(
        "/v1/devices/register",
        json={"device_id": "short", "platform": "windows"},
    )
    assert resp.status_code == 422


async def test_delete_device_me_removes_device_and_cascades(client):
    reg = await client.post(
        "/v1/devices/register",
        json={"device_id": "device-del-99999", "platform": "ios"},
    )
    token = reg.json()["token"]

    del_resp = await client.delete(
        "/v1/devices/me",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert del_resp.status_code == 200
    assert del_resp.json()["ok"] is True
    assert del_resp.json()["deleted_device_id"] == "device-del-99999"

    # Second delete returns 404
    del_resp2 = await client.delete(
        "/v1/devices/me",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert del_resp2.status_code == 404


async def test_delete_device_me_requires_auth(client):
    resp = await client.delete("/v1/devices/me")
    assert resp.status_code == 401
