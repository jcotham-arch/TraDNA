from __future__ import annotations

import uuid
from datetime import datetime
from decimal import Decimal

from sqlalchemy import JSON, DateTime, ForeignKey, Index, Numeric, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from .base import Base


class BrokerageAccount(Base):
    __tablename__ = "brokerage_accounts"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    broker: Mapped[str] = mapped_column(String(40), index=True)
    external_account_ref: Mapped[str] = mapped_column(String(200), unique=True)
    mode: Mapped[str] = mapped_column(String(20), default="read_only")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


class RawBrokerEvent(Base):
    __tablename__ = "raw_broker_events"
    __table_args__ = (
        UniqueConstraint("account_id", "provider_event_id"),
        Index("ix_raw_broker_events_account_occurred", "account_id", "occurred_at"),
    )

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    account_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("brokerage_accounts.id", ondelete="RESTRICT")
    )
    provider_event_id: Mapped[str] = mapped_column(String(300))
    event_type: Mapped[str] = mapped_column(String(80), index=True)
    occurred_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), index=True)
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    payload: Mapped[dict] = mapped_column(JSON)
    payload_sha256: Mapped[str] = mapped_column(String(64))


class TradeEpisodeRecord(Base):
    __tablename__ = "trade_episodes"
    __table_args__ = (UniqueConstraint("account_id", "reconstruction_version", "external_key"),)

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    account_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("brokerage_accounts.id"))
    reconstruction_version: Mapped[str] = mapped_column(String(40))
    external_key: Mapped[str] = mapped_column(String(300), index=True)
    asset_type: Mapped[str] = mapped_column(String(20), index=True)
    symbol: Mapped[str] = mapped_column(String(80), index=True)
    opened_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    closed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    status: Mapped[str] = mapped_column(String(20), index=True)
    realized_pnl: Mapped[Decimal] = mapped_column(Numeric(20, 8), default=Decimal(0))
    derived_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


class MarketCandleRecord(Base):
    __tablename__ = "market_candles"
    __table_args__ = (
        UniqueConstraint(
            "provider",
            "symbol",
            "timeframe",
            "started_at",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    provider: Mapped[str] = mapped_column(String(40))
    symbol: Mapped[str] = mapped_column(String(80))
    timeframe: Mapped[str] = mapped_column(String(20))
    started_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    open: Mapped[Decimal] = mapped_column(Numeric(20, 8))
    high: Mapped[Decimal] = mapped_column(Numeric(20, 8))
    low: Mapped[Decimal] = mapped_column(Numeric(20, 8))
    close: Mapped[Decimal] = mapped_column(Numeric(20, 8))
    volume: Mapped[Decimal] = mapped_column(Numeric(28, 8))
    source_delay: Mapped[str] = mapped_column(String(40))


class AnalysisRecord(Base):
    __tablename__ = "historical_analyses"
    __table_args__ = (UniqueConstraint("trade_episode_id", "analysis_version", "market_provider"),)

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    trade_episode_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("trade_episodes.id"))
    analysis_version: Mapped[str] = mapped_column(String(40), index=True)
    market_provider: Mapped[str] = mapped_column(String(40))
    feature_payload: Mapped[dict] = mapped_column(JSON)
    evidence_payload: Mapped[dict] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


class AuditEvent(Base):
    __tablename__ = "audit_events"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    occurred_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    actor_type: Mapped[str] = mapped_column(String(40))
    actor_ref: Mapped[str] = mapped_column(String(200))
    action: Mapped[str] = mapped_column(String(100), index=True)
    object_type: Mapped[str] = mapped_column(String(80))
    object_ref: Mapped[str] = mapped_column(String(300))
    detail: Mapped[str] = mapped_column(Text)
    metadata_json: Mapped[dict] = mapped_column(JSON)


class PaperShadowEventRecord(Base):
    __tablename__ = "paper_shadow_events"
    __table_args__ = (UniqueConstraint("session_id", "event_key"),)

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    session_id: Mapped[str] = mapped_column(String(100), index=True)
    event_key: Mapped[str] = mapped_column(String(300))
    event_type: Mapped[str] = mapped_column(String(40), index=True)
    occurred_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    symbol: Mapped[str] = mapped_column(String(20), index=True)
    payload: Mapped[dict] = mapped_column(JSON)
    payload_sha256: Mapped[str] = mapped_column(String(64))
