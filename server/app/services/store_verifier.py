import json
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

import httpx
from jose import jwt

from app.config import settings

PRODUCT_DURATIONS_DAYS: dict[str, int] = {
    "pro_monthly": 30,
    "pro_quarterly": 90,
    "pro_yearly": 365,
}


@dataclass(frozen=True)
class VerifiedPurchase:
    original_tx_id: str
    product_id: str
    is_pro: bool
    expires_at: datetime | None


def _apple_api_jwt() -> str:
    if not (settings.apple_issuer_id and settings.apple_key_id and settings.apple_private_key):
        raise ValueError("apple_credentials_not_configured")
    now = int(datetime.now(UTC).timestamp())
    headers = {"alg": "ES256", "kid": settings.apple_key_id, "typ": "JWT"}
    payload = {
        "iss": settings.apple_issuer_id,
        "iat": now,
        "exp": now + 300,
        "aud": "appstoreconnect-v1",
        "bid": settings.apple_bundle_id,
    }
    return jwt.encode(payload, settings.apple_private_key, algorithm="ES256", headers=headers)


async def _verify_apple(product_id: str, transaction_id: str) -> VerifiedPurchase:
    base = (
        "https://api.storekit-sandbox.itunes.apple.com"
        if settings.apple_use_sandbox
        else "https://api.storekit.itunes.apple.com"
    )
    token = _apple_api_jwt()
    async with httpx.AsyncClient(timeout=10.0) as client:
        resp = await client.get(
            f"{base}/inApps/v1/transactions/{transaction_id}",
            headers={"Authorization": f"Bearer {token}"},
        )
    if resp.status_code != 200:
        raise ValueError(f"apple_verify_failed:{resp.status_code}")

    signed_tx = resp.json().get("signedTransactionInfo")
    if not signed_tx:
        raise ValueError("apple_missing_signed_tx")

    # Decode JWS payload; in production also verify Apple x5c chain if required.
    claims = jwt.get_unverified_claims(signed_tx)
    if claims.get("bundleId") != settings.apple_bundle_id:
        raise ValueError("apple_bundle_mismatch")

    expires_ms = claims.get("expiresDate")
    expires_at = (
        datetime.fromtimestamp(expires_ms / 1000, tz=UTC) if expires_ms else None
    )
    is_pro = expires_at is None or expires_at > datetime.now(UTC)
    return VerifiedPurchase(
        original_tx_id=str(claims.get("originalTransactionId") or transaction_id),
        product_id=str(claims.get("productId") or product_id),
        is_pro=is_pro,
        expires_at=expires_at,
    )


async def _google_access_token() -> str:
    if not settings.google_play_service_account_json:
        raise ValueError("google_credentials_not_configured")
    sa = json.loads(settings.google_play_service_account_json)
    now = int(datetime.now(UTC).timestamp())
    assertion = jwt.encode(
        {
            "iss": sa["client_email"],
            "scope": "https://www.googleapis.com/auth/androidpublisher",
            "aud": "https://oauth2.googleapis.com/token",
            "iat": now,
            "exp": now + 3600,
        },
        sa["private_key"],
        algorithm="RS256",
    )
    async with httpx.AsyncClient(timeout=10.0) as client:
        resp = await client.post(
            "https://oauth2.googleapis.com/token",
            data={
                "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                "assertion": assertion,
            },
        )
    if resp.status_code != 200:
        raise ValueError(f"google_oauth_failed:{resp.status_code}")
    return resp.json()["access_token"]


async def _verify_google(product_id: str, purchase_token: str) -> VerifiedPurchase:
    access_token = await _google_access_token()
    url = (
        "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
        f"{settings.google_play_package_name}/purchases/subscriptionsv2/tokens/{purchase_token}"
    )
    async with httpx.AsyncClient(timeout=10.0) as client:
        resp = await client.get(url, headers={"Authorization": f"Bearer {access_token}"})
    if resp.status_code != 200:
        raise ValueError(f"google_verify_failed:{resp.status_code}")

    data = resp.json()
    state = data.get("subscriptionState", "")
    active_states = {"SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD"}
    line_items = data.get("lineItems") or [{}]
    expiry_raw = line_items[0].get("expiryTime")
    expires_at = datetime.fromisoformat(expiry_raw) if expiry_raw else None
    return VerifiedPurchase(
        original_tx_id=data.get("linkedPurchaseToken") or purchase_token,
        product_id=line_items[0].get("productId") or product_id,
        is_pro=state in active_states,
        expires_at=expires_at,
    )


async def verify_store_purchase(
    store: str, product_id: str, receipt_or_token: str, *, stub_mode: bool | None = None
) -> VerifiedPurchase:
    """Verify a store receipt/token and return normalized entitlement data."""
    if product_id not in PRODUCT_DURATIONS_DAYS:
        raise ValueError("unknown_product_id")

    use_stub = settings.billing_stub_mode if stub_mode is None else stub_mode
    if use_stub:
        expires = datetime.now(UTC) + timedelta(
            days=PRODUCT_DURATIONS_DAYS[product_id]
        )
        return VerifiedPurchase(
            original_tx_id=receipt_or_token,
            product_id=product_id,
            is_pro=True,
            expires_at=expires,
        )

    if store == "apple":
        return await _verify_apple(product_id, receipt_or_token)
    if store == "google":
        return await _verify_google(product_id, receipt_or_token)
    raise ValueError("unsupported_store")
