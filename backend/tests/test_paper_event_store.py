from datetime import UTC, datetime
from decimal import Decimal

from sqlalchemy import create_engine
from sqlalchemy.orm import Session

from tradna_backend.db.base import Base
from tradna_backend.services.paper_event_store import append_shadow_event, list_shadow_events
from tradna_backend.services.paper_shadow import ShadowEvent


def test_shadow_events_are_immutable_and_idempotent() -> None:
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    event = ShadowEvent(
        "FILLED",
        datetime(2026, 8, 31, tzinfo=UTC),
        "r1",
        "NVDA",
        Decimal(100),
        Decimal(5),
        "10-percent-cap",
    )
    with Session(engine) as db:
        assert append_shadow_event(db, session_id="2026-08-31", event=event)
        assert not append_shadow_event(db, session_id="2026-08-31", event=event)
        stored = list_shadow_events(db, session_id="2026-08-31")
        assert len(stored) == 1
        assert stored[0].payload_sha256
        assert stored[0].payload["quantity"] == "5"
