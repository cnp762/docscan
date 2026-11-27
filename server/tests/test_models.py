from datetime import date

from sqlalchemy import select

from app.models import Device, QuotaDaily


async def test_device_and_quota_cascade_delete(db_session):
    device = Device(device_id="test-device-001", platform="android", app_version="0.1.0")
    db_session.add(device)
    await db_session.flush()

    quota = QuotaDaily(device_id="test-device-001", utc_day=date(2026, 11, 26), used=2)
    db_session.add(quota)
    await db_session.commit()

    await db_session.delete(device)
    await db_session.commit()

    result = await db_session.execute(
        select(QuotaDaily).where(QuotaDaily.device_id == "test-device-001")
    )
    assert result.scalar_one_or_none() is None
