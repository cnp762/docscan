from datetime import UTC, datetime
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth import create_device_token, get_device_id
from app.db import get_db
from app.models import Device

router = APIRouter(prefix="/v1/devices", tags=["devices"])


class DeviceRegister(BaseModel):
    device_id: str = Field(min_length=8, max_length=128)
    platform: str = Field(pattern="^(android|ios)$")
    app_version: str | None = Field(default=None, max_length=32)


@router.post("/register")
async def register_device(
    body: DeviceRegister,
    db: Annotated[AsyncSession, Depends(get_db)],
):
    now = datetime.now(UTC)
    stmt = (
        pg_insert(Device)
        .values(
            device_id=body.device_id,
            platform=body.platform,
            app_version=body.app_version,
            created_at=now,
            updated_at=now,
        )
        .on_conflict_do_update(
            index_elements=[Device.device_id],
            set_={
                "platform": body.platform,
                "app_version": body.app_version,
                "updated_at": now,
            },
        )
    )
    await db.execute(stmt)
    await db.commit()

    return {
        "device_id": body.device_id,
        "platform": body.platform,
        "token": create_device_token(body.device_id),
        "registered_at": now.isoformat(),
    }


@router.delete("/me")
async def delete_device_me(
    device_id: Annotated[str, Depends(get_device_id)],
    db: Annotated[AsyncSession, Depends(get_db)],
):
    result = await db.execute(select(Device).where(Device.device_id == device_id))
    device = result.scalar_one_or_none()
    if device is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="device_not_found")

    await db.delete(device)
    await db.commit()
    return {"ok": True, "deleted_device_id": device_id}
