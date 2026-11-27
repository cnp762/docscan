from datetime import UTC, datetime, timedelta
from typing import Annotated

from fastapi import Header, HTTPException, status
from jose import JWTError, jwt

from app.config import settings


def create_device_token(device_id: str) -> str:
    """Issue a signed JWT for a registered device."""
    now = datetime.now(UTC)
    payload = {
        "sub": f"device:{device_id}",
        "device_id": device_id,
        "iat": int(now.timestamp()),
        "exp": int((now + timedelta(days=settings.jwt_expire_days)).timestamp()),
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)


def decode_device_token(token: str) -> str:
    """Decode and validate a device JWT, returning device_id."""
    try:
        payload = jwt.decode(
            token, settings.jwt_secret, algorithms=[settings.jwt_algorithm]
        )
    except JWTError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid_token"
        ) from exc
    device_id = payload.get("device_id")
    if not isinstance(device_id, str) or len(device_id) < 8:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid_token"
        )
    return device_id


async def get_device_id(
    authorization: Annotated[str | None, Header()] = None,
    x_device_id: Annotated[str | None, Header(alias="X-Device-Id")] = None,
) -> str:
    """Resolve device_id from Bearer JWT and/or X-Device-Id header."""
    jwt_device_id: str | None = None
    if authorization:
        scheme, _, token = authorization.partition(" ")
        if scheme.lower() != "bearer" or not token:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid_auth_header"
            )
        jwt_device_id = decode_device_token(token)

    if jwt_device_id and x_device_id and jwt_device_id != x_device_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="device_id_mismatch"
        )

    device_id = jwt_device_id or x_device_id
    if not device_id or len(device_id) < 8:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="missing_device_credentials"
        )
    return device_id
