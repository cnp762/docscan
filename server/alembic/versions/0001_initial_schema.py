"""initial schema

Revision ID: 0001
Revises:
Create Date: 2026-11-26

"""
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0001"
down_revision: Union[str, Sequence[str], None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "devices",
        sa.Column("device_id", sa.String(length=128), nullable=False),
        sa.Column("platform", sa.String(length=16), nullable=False),
        sa.Column("app_version", sa.String(length=32), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("device_id"),
    )
    op.create_table(
        "entitlements",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("store", sa.String(length=16), nullable=False),
        sa.Column("original_tx_id", sa.String(length=256), nullable=False),
        sa.Column("product_id", sa.String(length=64), nullable=False),
        sa.Column("is_pro", sa.Boolean(), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("store", "original_tx_id", name="uq_entitlement_store_tx"),
    )
    op.create_table(
        "device_entitlements",
        sa.Column("device_id", sa.String(length=128), nullable=False),
        sa.Column("entitlement_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("linked_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(["device_id"], ["devices.device_id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(
            ["entitlement_id"], ["entitlements.id"], ondelete="CASCADE"
        ),
        sa.PrimaryKeyConstraint("device_id", "entitlement_id"),
    )
    op.create_table(
        "quota_daily",
        sa.Column("device_id", sa.String(length=128), nullable=False),
        sa.Column("utc_day", sa.Date(), nullable=False),
        sa.Column("used", sa.Integer(), nullable=False),
        sa.ForeignKeyConstraint(["device_id"], ["devices.device_id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("device_id", "utc_day"),
    )


def downgrade() -> None:
    op.drop_table("quota_daily")
    op.drop_table("device_entitlements")
    op.drop_table("entitlements")
    op.drop_table("devices")
