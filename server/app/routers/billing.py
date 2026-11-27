from datetime import UTC, datetime
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import or_, select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth import get_device_id
from app.config import settings
from app.db import get_db
from app.models import Device, DeviceEntitlement, Entitlement
from app.services import store_verifier

router = APIRouter(prefix="/v1/billing", tags=["billing"])


class VerifyPurchaseBody(BaseModel):
    store: str = Field(pattern="^(apple|google)$")
    product_id: str = Field(pattern="^(pro_monthly|pro_quarterly|pro_yearly)$")
    receipt_or_token: str = Field(min_length=1, max_length=4096)


class EntitlementOut(BaseModel):
    is_pro: bool
    product_id: str | None = None
    expires_at: datetime | None = None


@router.post("/verify", response_model=EntitlementOut)
async def verify_purchase(
    body: VerifyPurchaseBody,
    device_id: Annotated[str, Depends(get_device_id)],
    db: Annotated[AsyncSession, Depends(get_db)],
):
    try:
        verified = await store_verifier.verify_store_purchase(
            body.store,
            body.product_id,
            body.receipt_or_token,
            stub_mode=settings.billing_stub_mode,
        )
    except ValueError as exc:
        raise HTTPException(status_code=402, detail=str(exc)) from exc

    now = datetime.now(UTC)
    await db.execute(
        pg_insert(Device)
        .values(
            device_id=device_id,
            platform="ios" if body.store == "apple" else "android",
            app_version=None,
            created_at=now,
            updated_at=now,
        )
        .on_conflict_do_nothing(index_elements=[Device.device_id])
    )

    ent_stmt = (
        pg_insert(Entitlement)
        .values(
            store=body.store,
            original_tx_id=verified.original_tx_id,
            product_id=verified.product_id,
            is_pro=verified.is_pro,
            expires_at=verified.expires_at,
            updated_at=now,
        )
        .on_conflict_do_update(
            constraint="uq_entitlement_store_tx",
            set_={
                "product_id": verified.product_id,
                "is_pro": verified.is_pro,
                "expires_at": verified.expires_at,
                "updated_at": now,
            },
        )
        .returning(Entitlement.id)
    )
    entitlement_id = (await db.execute(ent_stmt)).scalar_one()

    await db.execute(
        pg_insert(DeviceEntitlement)
        .values(device_id=device_id, entitlement_id=entitlement_id, linked_at=now)
        .on_conflict_do_nothing(
            index_elements=[DeviceEntitlement.device_id, DeviceEntitlement.entitlement_id]
        )
    )
    await db.commit()

    return EntitlementOut(
        is_pro=verified.is_pro,
        product_id=verified.product_id,
        expires_at=verified.expires_at,
    )


@router.get("/entitlement", response_model=EntitlementOut)
async def get_entitlement(
    device_id: Annotated[str, Depends(get_device_id)],
    db: Annotated[AsyncSession, Depends(get_db)],
):
    now = datetime.now(UTC)
    stmt = (
        select(Entitlement)
        .join(DeviceEntitlement, DeviceEntitlement.entitlement_id == Entitlement.id)
        .where(
            DeviceEntitlement.device_id == device_id,
            Entitlement.is_pro.is_(True),
            or_(Entitlement.expires_at.is_(None), Entitlement.expires_at > now),
        )
        .order_by(Entitlement.expires_at.desc().nulls_first())
        .limit(1)
    )
    ent = (await db.execute(stmt)).scalar_one_or_none()
    if ent is None:
        return EntitlementOut(is_pro=False)
    return EntitlementOut(
        is_pro=True,
        product_id=ent.product_id,
        expires_at=ent.expires_at,
    )
