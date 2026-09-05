from __future__ import annotations

import hashlib
import json
from dataclasses import asdict
from decimal import Decimal

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from tradna_backend.db.models import PaperShadowEventRecord
from tradna_backend.services.paper_shadow import ShadowEvent


def append_shadow_event(db: Session, *, session_id: str, event: ShadowEvent) -> bool:
    payload = {key: _json(value) for key, value in asdict(event).items()}
    canonical = json.dumps(payload, sort_keys=True, separators=(",", ":"))
    event_key = f"{event.recommendation_id}:{event.event_type}:{event.occurred_at.isoformat()}"
    db.add(
        PaperShadowEventRecord(
            session_id=session_id,
            event_key=event_key,
            event_type=event.event_type,
            occurred_at=event.occurred_at,
            symbol=event.symbol,
            payload=payload,
            payload_sha256=hashlib.sha256(canonical.encode()).hexdigest(),
        )
    )
    try:
        db.commit()
        return True
    except IntegrityError:
        db.rollback()
        return False


def list_shadow_events(db: Session, *, session_id: str) -> list[PaperShadowEventRecord]:
    return list(
        db.scalars(
            select(PaperShadowEventRecord)
            .where(PaperShadowEventRecord.session_id == session_id)
            .order_by(PaperShadowEventRecord.occurred_at, PaperShadowEventRecord.event_key)
        )
    )


def _json(value):
    if isinstance(value, Decimal):
        return format(value, "f")
    if hasattr(value, "isoformat"):
        return value.isoformat()
    return value
