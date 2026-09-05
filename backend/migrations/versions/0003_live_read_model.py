"""Add broker synchronization and quote read models."""

import sqlalchemy as sa
from alembic import op

revision = "0003_live_read_model"
down_revision = "0002_paper_shadow_events"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "broker_sync_runs",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("account_id", sa.Uuid(), sa.ForeignKey("brokerage_accounts.id"), nullable=False),
        sa.Column("started_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("completed_at", sa.DateTime(timezone=True)),
        sa.Column("status", sa.String(20), nullable=False),
        sa.Column("event_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("quote_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("error_code", sa.String(80)),
    )
    op.create_index("ix_broker_sync_runs_started_at", "broker_sync_runs", ["started_at"])
    op.create_index("ix_broker_sync_runs_status", "broker_sync_runs", ["status"])
    op.create_table(
        "broker_quotes",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("provider", sa.String(40), nullable=False),
        sa.Column("symbol", sa.String(20), nullable=False),
        sa.Column("observed_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("received_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("mark_price", sa.Numeric(20, 8), nullable=False),
        sa.Column("bid_price", sa.Numeric(20, 8)),
        sa.Column("ask_price", sa.Numeric(20, 8)),
        sa.Column("previous_close", sa.Numeric(20, 8)),
        sa.UniqueConstraint("provider", "symbol", "observed_at"),
    )
    op.create_index("ix_broker_quotes_symbol", "broker_quotes", ["symbol"])
    op.create_index("ix_broker_quotes_observed_at", "broker_quotes", ["observed_at"])


def downgrade() -> None:
    op.drop_table("broker_quotes")
    op.drop_table("broker_sync_runs")
