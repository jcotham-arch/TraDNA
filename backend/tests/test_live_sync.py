import asyncio
import uuid
from datetime import UTC, datetime

from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session

from tradna_backend.brokers.robinhood_agentic import RobinhoodAgenticReadOnly
from tradna_backend.db.base import Base
from tradna_backend.db.models import BrokerageAccount, BrokerQuoteRecord, RawBrokerEvent
from tradna_backend.services.live_sync import latest_quotes, latest_sync_run, run_live_sync

NOW = datetime(2026, 9, 8, 14, 30, tzinfo=UTC)


class Caller:
    async def call_tool(self, name, arguments):
        if name == "get_equity_positions":
            return {"positions": [{"id": "p1", "symbol": "RXT", "quantity": "2"}]}
        if name == "get_equity_quotes":
            return {
                "quotes": [
                    {
                        "symbol": "RXT",
                        "price": "4.25",
                        "bid": "4.24",
                        "ask": "4.26",
                        "updated_at": NOW.isoformat(),
                    }
                ]
            }
        return {"orders": []}


def test_live_sync_persists_events_quotes_and_status() -> None:
    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    account_id = uuid.uuid4()
    with Session(engine) as db:
        db.add(
            BrokerageAccount(
                id=account_id,
                broker="robinhood",
                external_account_ref="agentic-test",
                mode="read_only",
                created_at=NOW,
            )
        )
        db.commit()
        result = asyncio.run(
            run_live_sync(
                db,
                account_id=account_id,
                broker=RobinhoodAgenticReadOnly(Caller()),
                symbols=("RXT",),
                now=NOW,
            )
        )
        assert result.status == "COMPLETE"
        assert result.event_count == 1
        assert result.quote_count == 1
        assert db.scalar(select(RawBrokerEvent)) is not None
        assert db.scalar(select(BrokerQuoteRecord)) is not None
        assert latest_sync_run(db).status == "COMPLETE"
        assert latest_quotes(db)[0].symbol == "RXT"
