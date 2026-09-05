"""Add immutable paper-shadow event storage."""

import sqlalchemy as sa
from alembic import op

revision = "0002_paper_shadow_events"
down_revision = "0001_backend_foundation"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "paper_shadow_events",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("session_id", sa.String(100), nullable=False),
        sa.Column("event_key", sa.String(300), nullable=False),
        sa.Column("event_type", sa.String(40), nullable=False),
        sa.Column("occurred_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("symbol", sa.String(20), nullable=False),
        sa.Column("payload", sa.JSON(), nullable=False),
        sa.Column("payload_sha256", sa.String(64), nullable=False),
        sa.UniqueConstraint("session_id", "event_key"),
    )
    op.create_index("ix_paper_shadow_events_session_id", "paper_shadow_events", ["session_id"])
    op.create_index("ix_paper_shadow_events_event_type", "paper_shadow_events", ["event_type"])
    op.create_index("ix_paper_shadow_events_occurred_at", "paper_shadow_events", ["occurred_at"])
    op.create_index("ix_paper_shadow_events_symbol", "paper_shadow_events", ["symbol"])


def downgrade() -> None:
    op.drop_table("paper_shadow_events")
