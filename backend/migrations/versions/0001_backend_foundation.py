"""Create immutable ingestion and versioned analysis foundation."""

import sqlalchemy as sa
from alembic import op

revision = "0001_backend_foundation"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "brokerage_accounts",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("broker", sa.String(40), nullable=False),
        sa.Column("external_account_ref", sa.String(200), nullable=False, unique=True),
        sa.Column("mode", sa.String(20), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_brokerage_accounts_broker", "brokerage_accounts", ["broker"])
    op.create_table(
        "raw_broker_events",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column(
            "account_id",
            sa.Uuid(),
            sa.ForeignKey("brokerage_accounts.id", ondelete="RESTRICT"),
            nullable=False,
        ),
        sa.Column("provider_event_id", sa.String(300), nullable=False),
        sa.Column("event_type", sa.String(80), nullable=False),
        sa.Column("occurred_at", sa.DateTime(timezone=True)),
        sa.Column("received_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("payload", sa.JSON(), nullable=False),
        sa.Column("payload_sha256", sa.String(64), nullable=False),
        sa.UniqueConstraint("account_id", "provider_event_id"),
    )
    op.create_index(
        "ix_raw_broker_events_account_occurred",
        "raw_broker_events",
        ["account_id", "occurred_at"],
    )
    op.create_index("ix_raw_broker_events_event_type", "raw_broker_events", ["event_type"])
    op.create_index("ix_raw_broker_events_occurred_at", "raw_broker_events", ["occurred_at"])
    op.create_table(
        "trade_episodes",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("account_id", sa.Uuid(), sa.ForeignKey("brokerage_accounts.id")),
        sa.Column("reconstruction_version", sa.String(40), nullable=False),
        sa.Column("external_key", sa.String(300), nullable=False),
        sa.Column("asset_type", sa.String(20), nullable=False),
        sa.Column("symbol", sa.String(80), nullable=False),
        sa.Column("opened_at", sa.DateTime(timezone=True)),
        sa.Column("closed_at", sa.DateTime(timezone=True)),
        sa.Column("status", sa.String(20), nullable=False),
        sa.Column("realized_pnl", sa.Numeric(20, 8), nullable=False),
        sa.Column("derived_at", sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint("account_id", "reconstruction_version", "external_key"),
    )
    op.create_table(
        "market_candles",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("provider", sa.String(40), nullable=False),
        sa.Column("symbol", sa.String(80), nullable=False),
        sa.Column("timeframe", sa.String(20), nullable=False),
        sa.Column("started_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("open", sa.Numeric(20, 8), nullable=False),
        sa.Column("high", sa.Numeric(20, 8), nullable=False),
        sa.Column("low", sa.Numeric(20, 8), nullable=False),
        sa.Column("close", sa.Numeric(20, 8), nullable=False),
        sa.Column("volume", sa.Numeric(28, 8), nullable=False),
        sa.Column("source_delay", sa.String(40), nullable=False),
        sa.UniqueConstraint("provider", "symbol", "timeframe", "started_at"),
    )
    op.create_index("ix_trade_episodes_external_key", "trade_episodes", ["external_key"])
    op.create_index("ix_trade_episodes_asset_type", "trade_episodes", ["asset_type"])
    op.create_index("ix_trade_episodes_symbol", "trade_episodes", ["symbol"])
    op.create_index("ix_trade_episodes_status", "trade_episodes", ["status"])
    op.create_table(
        "historical_analyses",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("trade_episode_id", sa.Uuid(), sa.ForeignKey("trade_episodes.id")),
        sa.Column("analysis_version", sa.String(40), nullable=False),
        sa.Column("market_provider", sa.String(40), nullable=False),
        sa.Column("feature_payload", sa.JSON(), nullable=False),
        sa.Column("evidence_payload", sa.JSON(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint("trade_episode_id", "analysis_version", "market_provider"),
    )
    op.create_table(
        "audit_events",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("occurred_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("actor_type", sa.String(40), nullable=False),
        sa.Column("actor_ref", sa.String(200), nullable=False),
        sa.Column("action", sa.String(100), nullable=False),
        sa.Column("object_type", sa.String(80), nullable=False),
        sa.Column("object_ref", sa.String(300), nullable=False),
        sa.Column("detail", sa.Text(), nullable=False),
        sa.Column("metadata_json", sa.JSON(), nullable=False),
    )
    op.create_index(
        "ix_historical_analyses_analysis_version",
        "historical_analyses",
        ["analysis_version"],
    )
    op.create_index("ix_audit_events_occurred_at", "audit_events", ["occurred_at"])
    op.create_index("ix_audit_events_action", "audit_events", ["action"])


def downgrade() -> None:
    op.drop_table("audit_events")
    op.drop_table("historical_analyses")
    op.drop_table("market_candles")
    op.drop_table("trade_episodes")
    op.drop_table("raw_broker_events")
    op.drop_table("brokerage_accounts")
