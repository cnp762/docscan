import uuid
from datetime import UTC, date, datetime

from sqlalchemy import Boolean, Date, DateTime, ForeignKey, Integer, String, UniqueConstraint
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db import Base


def utc_now() -> datetime:
    return datetime.now(UTC)


class Device(Base):
    """Registered client device (no user accounts in MVP)."""

    __tablename__ = "devices"

    device_id: Mapped[str] = mapped_column(String(128), primary_key=True)
    platform: Mapped[str] = mapped_column(String(16), nullable=False)
    app_version: Mapped[str | None] = mapped_column(String(32), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utc_now, nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=False
    )

    quotas: Mapped[list["QuotaDaily"]] = relationship(
        back_populates="device", cascade="all, delete-orphan"
    )
    entitlement_links: Mapped[list["DeviceEntitlement"]] = relationship(
        back_populates="device", cascade="all, delete-orphan"
    )


class QuotaDaily(Base):
    """Authoritative UTC-day page commit counter per device."""

    __tablename__ = "quota_daily"

    device_id: Mapped[str] = mapped_column(
        String(128), ForeignKey("devices.device_id", ondelete="CASCADE"), primary_key=True
    )
    utc_day: Mapped[date] = mapped_column(Date, primary_key=True)
    used: Mapped[int] = mapped_column(Integer, default=0, nullable=False)

    device: Mapped["Device"] = relationship(back_populates="quotas")


class Entitlement(Base):
    """Store subscription entitlement keyed by store + original transaction."""

    __tablename__ = "entitlements"
    __table_args__ = (
        UniqueConstraint("store", "original_tx_id", name="uq_entitlement_store_tx"),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    store: Mapped[str] = mapped_column(String(16), nullable=False)
    original_tx_id: Mapped[str] = mapped_column(String(256), nullable=False)
    product_id: Mapped[str] = mapped_column(String(64), nullable=False)
    is_pro: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    expires_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=False
    )

    device_links: Mapped[list["DeviceEntitlement"]] = relationship(
        back_populates="entitlement", cascade="all, delete-orphan"
    )


class DeviceEntitlement(Base):
    """Many-to-many link allowing Restore Purchases across device reinstalls."""

    __tablename__ = "device_entitlements"

    device_id: Mapped[str] = mapped_column(
        String(128), ForeignKey("devices.device_id", ondelete="CASCADE"), primary_key=True
    )
    entitlement_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("entitlements.id", ondelete="CASCADE"), primary_key=True
    )
    linked_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utc_now, nullable=False
    )

    device: Mapped["Device"] = relationship(back_populates="entitlement_links")
    entitlement: Mapped["Entitlement"] = relationship(back_populates="device_links")
