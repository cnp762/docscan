from datetime import datetime, timezone
from typing import Annotated
from uuid import uuid4

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

from app.config import settings

app = FastAPI(title="DocScan API", version="0.1.0")

# In-memory stubs until Postgres models/migrations are wired in a follow-up slice.
_quota: dict[tuple[str, str], int] = {}
_entitlements: dict[str, dict] = {}


def utc_day() -> str:
    return datetime.now(timezone.utc).date().isoformat()


def subject_from_device(device_id: str) -> str:
    return f"device:{device_id}"


class DeviceRegister(BaseModel):
    device_id: str = Field(min_length=8)
    platform: str = Field(pattern="^(android|ios)$")
    app_version: str | None = None


class ConsumeBody(BaseModel):
    n: int = Field(ge=1, le=50)


class EntitlementOut(BaseModel):
    is_pro: bool
    product_id: str | None = None
    expires_at: datetime | None = None


@app.get("/health")
async def health():
    return {"ok": True, "service": "docscan-api", "host": settings.api_public_host}


@app.post("/v1/devices/register")
async def register_device(body: DeviceRegister):
    return {
        "device_id": body.device_id,
        "platform": body.platform,
        "token": str(uuid4()),
        "registered_at": datetime.now(timezone.utc).isoformat(),
    }


@app.get("/v1/quota/today")
async def quota_today(x_device_id: Annotated[str, Header(alias="X-Device-Id")]):
    key = (subject_from_device(x_device_id), utc_day())
    used = _quota.get(key, 0)
    ent = _entitlements.get(x_device_id)
    is_pro = bool(ent and ent.get("is_pro"))
    return {
        "utc_day": utc_day(),
        "used": used,
        "limit": None if is_pro else 5,
        "remaining": None if is_pro else max(0, 5 - used),
        "is_pro": is_pro,
    }


@app.post("/v1/quota/consume")
async def quota_consume(
    body: ConsumeBody,
    x_device_id: Annotated[str, Header(alias="X-Device-Id")],
):
    ent = _entitlements.get(x_device_id)
    if ent and ent.get("is_pro"):
        return {"ok": True, "is_pro": True, "consumed": body.n}

    key = (subject_from_device(x_device_id), utc_day())
    used = _quota.get(key, 0)
    if used + body.n > 5:
        raise HTTPException(status_code=402, detail="quota_exceeded")
    _quota[key] = used + body.n
    return {"ok": True, "used": _quota[key], "remaining": 5 - _quota[key]}


class VerifyPurchaseBody(BaseModel):
    store: str = Field(pattern="^(apple|google)$")
    product_id: str
    receipt_or_token: str


@app.post("/v1/billing/verify", response_model=EntitlementOut)
async def verify_purchase(
    body: VerifyPurchaseBody,
    x_device_id: Annotated[str, Header(alias="X-Device-Id")],
):
    # Stub: accept any token and mark Pro. Replace with StoreKit / Play verification.
    expires = datetime.now(timezone.utc)
    _entitlements[x_device_id] = {
        "is_pro": True,
        "product_id": body.product_id,
        "expires_at": expires,
    }
    return EntitlementOut(is_pro=True, product_id=body.product_id, expires_at=expires)


@app.get("/v1/billing/entitlement", response_model=EntitlementOut)
async def entitlement(x_device_id: Annotated[str, Header(alias="X-Device-Id")]):
    ent = _entitlements.get(x_device_id) or {"is_pro": False}
    return EntitlementOut(
        is_pro=bool(ent.get("is_pro")),
        product_id=ent.get("product_id"),
        expires_at=ent.get("expires_at"),
    )
