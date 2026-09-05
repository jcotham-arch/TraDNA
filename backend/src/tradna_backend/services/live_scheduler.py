from __future__ import annotations

import asyncio
import uuid
from collections.abc import Callable
from datetime import UTC, datetime

from sqlalchemy.orm import Session

from tradna_backend.brokers.robinhood_agentic import RobinhoodAgenticReadOnly
from tradna_backend.services.live_sync import LiveSyncResult, run_live_sync


class LiveSyncScheduler:
    """Runs bounded read-only sync cycles; process supervision belongs to the host."""

    def __init__(
        self,
        *,
        session_factory: Callable[[], Session],
        broker: RobinhoodAgenticReadOnly,
        account_id: uuid.UUID,
        symbols: tuple[str, ...],
        interval_seconds: int = 60,
    ) -> None:
        if interval_seconds < 15:
            raise ValueError("Live sync interval cannot be less than 15 seconds.")
        self._session_factory = session_factory
        self._broker = broker
        self._account_id = account_id
        self._symbols = symbols
        self._interval_seconds = interval_seconds

    async def run_once(self, *, now: datetime | None = None) -> LiveSyncResult:
        with self._session_factory() as db:
            return await run_live_sync(
                db,
                account_id=self._account_id,
                broker=self._broker,
                symbols=self._symbols,
                now=now or datetime.now(UTC),
            )

    async def run_forever(self, stop: asyncio.Event) -> None:
        while not stop.is_set():
            await self.run_once()
            try:
                await asyncio.wait_for(stop.wait(), timeout=self._interval_seconds)
            except TimeoutError:
                continue
