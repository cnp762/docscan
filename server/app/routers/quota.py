from datetime import UTC, date, datetime
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import or_, select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth import get_device_id
from app.db import get_db
from app.models import Device, DeviceEntitlement, Entitlement, QuotaDaily

router = APIRouter(prefix="/v1/quota", tags=["quota"])

FREE_DAILY_LIMIT = 5


def utc_today() -> date:
    return datetime.now(UTC).date()


class ConsumeBody(BaseModel):
    n: int = Field(ge=1, le=50)


async def is_device_pro(db: AsyncSession, device_id: str) -> bool:
    """Return True if the device has at least one active, non-expired Pro entitlement."""
    now = datetime.now(UTC)
    stmt = (
        select(Entitlement.id)
        .join(DeviceEntitlement, DeviceEntitlement.entitlement_id == Entitlement.id)
        .where(
            DeviceEntitlement.device_id == device_id,
            Entitlement.is_pro.is_(True),
            or_(Entitlement.expires_at.is_(None), Entitlement.expires_at > now),
        )
        .limit(1)
    )
    res = await db.execute(stmt)
    return res.scalar_one_or_none() is not None


async def _ensure_device_row(db: AsyncSession, device_id: str) -> None:
    now = datetime.now(UTC)
    await db.execute(
        pg_insert(Device)
        .values(
            device_id=device_id,
            platform="unknown",
            app_version=None,
            created_at=now,
            updated_at=now,
        )
        .on_conflict_do_nothing(index_elements=[Device.device_id])
    )


@router.get("/today")
async def quota_today(
    device_id: Annotated[str, Depends(get_device_id)],
    db: Annotated[AsyncSession, Depends(get_db)],
):
    today = utc_today()
    pro = await is_device_pro(db, device_id)
    row = (
        await db.execute(
            select(QuotaDaily).where(
                QuotaDaily.device_id == device_id,
                QuotaDaily.utc_day == today,
            )
        )
    ).scalar_one_or_none()
    used = row.used if row else 0
    return {
        "utc_day": today.isoformat(),
        "used": used,
        "limit": None if pro else FREE_DAILY_LIMIT,
        "remaining": None if pro else max(0, FREE_DAILY_LIMIT - used),
        "is_pro": pro,
    }


@router.post("/consume")
async def quota_consume(
    body: ConsumeBody,
    device_id: Annotated[str, Depends(get_device_id)],
    db: Annotated[AsyncSession, Depends(get_db)],
):
    if await is_device_pro(db, device_id):
        return {"ok": True, "is_pro": True, "consumed": body.n}

    today = utc_today()
    await _ensure_device_row(db, device_id)
    await db.execute(
        pg_insert(QuotaDaily)
        .values(device_id=device_id, utc_day=today, used=0)
        .on_conflict_do_nothing(index_elements=[QuotaDaily.device_id, QuotaDaily.utc_day])
    )

    row = (
        await db.execute(
            select(QuotaDaily)
            .where(
                QuotaDaily.device_id == device_id,
                QuotaDaily.utc_day == today,
            )
            .with_for_update()
        )
    ).scalar_one()

    if row.used + body.n > FREE_DAILY_LIMIT:
        await db.rollback()
        raise HTTPException(status_code=402, detail="quota_exceeded")

    row.used += body.n
    await db.commit()
    return {
        "ok": True,
        "used": row.used,
        "remaining": FREE_DAILY_LIMIT - row.used,
    }
