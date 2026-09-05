from __future__ import annotations

import asyncio
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime

from sqlalchemy import select
from sqlalchemy.orm import Session

from tradna_backend.brokers.robinhood_agentic import RobinhoodAgenticReadOnly
from tradna_backend.db.models import BrokerQuoteRecord, BrokerSyncRunRecord, RawBrokerEvent
from tradna_backend.services.broker_sync import collect_read_only_snapshot


@dataclass(frozen=True, slots=True)
class LiveSyncResult:
    run_id: uuid.UUID
    status: str
    event_count: int
    quote_count: int
    completed_at: datetime


async def run_live_sync(
    db: Session,
    *,
    account_id: uuid.UUID,
    broker: RobinhoodAgenticReadOnly,
    symbols: tuple[str, ...],
    now: datetime | None = None,
) -> LiveSyncResult:
    received_at = (now or datetime.now(UTC)).astimezone(UTC)
    run = BrokerSyncRunRecord(
        account_id=account_id,
        started_at=received_at,
        status="RUNNING",
        event_count=0,
        quote_count=0,
    )
    db.add(run)
    db.commit()
    try:
        snapshot, quotes = await asyncio.gather(
            collect_read_only_snapshot(broker, observed_at=received_at),
            broker.get_equity_quotes(symbols),
        )
        for event in snapshot.events:
            exists = db.scalar(
                select(RawBrokerEvent.id).where(
                    RawBrokerEvent.account_id == account_id,
                    RawBrokerEvent.provider_event_id == event.provider_event_id,
                )
            )
            if exists is None:
                db.add(
                    RawBrokerEvent(
                        account_id=account_id,
                        provider_event_id=event.provider_event_id,
                        event_type=event.event_type,
                        occurred_at=event.occurred_at,
                        received_at=received_at,
                        payload=event.payload,
                        payload_sha256=event.payload_sha256,
                    )
                )
        for quote in quotes:
            exists = db.scalar(
                select(BrokerQuoteRecord.id).where(
                    BrokerQuoteRecord.provider == quote.provider,
                    BrokerQuoteRecord.symbol == quote.symbol,
                    BrokerQuoteRecord.observed_at == quote.observed_at,
                )
            )
            if exists is None:
                db.add(
                    BrokerQuoteRecord(
                        provider=quote.provider,
                        symbol=quote.symbol,
                        observed_at=quote.observed_at,
                        received_at=received_at,
                        mark_price=quote.mark_price,
                        bid_price=quote.bid_price,
                        ask_price=quote.ask_price,
                        previous_close=quote.previous_close,
                    )
                )
        run.status = "COMPLETE"
        run.event_count = len(snapshot.events)
        run.quote_count = len(quotes)
        run.completed_at = received_at
        db.commit()
    except Exception as error:
        db.rollback()
        run = db.get(BrokerSyncRunRecord, run.id)
        if run is not None:
            run.status = "FAILED"
            run.completed_at = received_at
            run.error_code = type(error).__name__[:80]
            db.commit()
        raise
    return LiveSyncResult(run.id, run.status, run.event_count, run.quote_count, received_at)


def latest_sync_run(db: Session) -> BrokerSyncRunRecord | None:
    return db.scalar(select(BrokerSyncRunRecord).order_by(BrokerSyncRunRecord.started_at.desc()))


def latest_quotes(db: Session) -> list[BrokerQuoteRecord]:
    rows = db.scalars(
        select(BrokerQuoteRecord).order_by(BrokerQuoteRecord.observed_at.desc())
    ).all()
    seen: set[str] = set()
    result: list[BrokerQuoteRecord] = []
    for row in rows:
        if row.symbol not in seen:
            seen.add(row.symbol)
            result.append(row)
    return result
