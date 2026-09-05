import asyncio
import uuid
from datetime import UTC, datetime

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from tradna_backend.brokers.robinhood_agentic import RobinhoodAgenticReadOnly
from tradna_backend.db.base import Base
from tradna_backend.db.models import BrokerageAccount
from tradna_backend.services.live_scheduler import LiveSyncScheduler

NOW = datetime(2026, 9, 8, 14, 30, tzinfo=UTC)


class EmptyCaller:
    async def call_tool(self, name, arguments):
        if name == "get_equity_quotes":
            return {"quotes": []}
        if name == "get_equity_positions":
            return {"positions": []}
        return {"orders": []}


def test_scheduler_runs_one_bounded_read_only_cycle() -> None:
    engine = create_engine(
        "sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )
    Base.metadata.create_all(engine)
    factory = sessionmaker(bind=engine, expire_on_commit=False)
    account_id = uuid.uuid4()
    with factory() as db:
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

    scheduler = LiveSyncScheduler(
        session_factory=factory,
        broker=RobinhoodAgenticReadOnly(EmptyCaller()),
        account_id=account_id,
        symbols=("RXT",),
        interval_seconds=60,
    )
    result = asyncio.run(scheduler.run_once(now=NOW))
    assert result.status == "COMPLETE"
    assert result.event_count == 0
    assert result.quote_count == 0
