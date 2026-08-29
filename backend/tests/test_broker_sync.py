import asyncio
from datetime import UTC, datetime
from decimal import Decimal

import pytest

from tradna_backend.brokers.base import BrokerOrder, BrokerPosition
from tradna_backend.services.broker_sync import collect_read_only_snapshot

NOW = datetime(2026, 8, 29, 12, tzinfo=UTC)


class FakeReadOnlyBroker:
    async def list_equity_positions(self) -> list[BrokerPosition]:
        return [BrokerPosition("position-1", "AAPL", Decimal(2), Decimal(100), NOW)]

    async def list_equity_orders(self) -> list[BrokerOrder]:
        return [BrokerOrder("stock-1", "AAPL", "buy", Decimal(2), "filled", NOW, NOW, Decimal(100))]

    async def list_option_orders(self) -> list[BrokerOrder]:
        return [
            BrokerOrder(
                "option-1", "AAPL call", "sell", Decimal(1), "filled", NOW, NOW, Decimal("1.25")
            )
        ]


def test_read_only_snapshot_is_deterministic_and_hashes_payloads() -> None:
    first = asyncio.run(collect_read_only_snapshot(FakeReadOnlyBroker(), observed_at=NOW))
    second = asyncio.run(collect_read_only_snapshot(FakeReadOnlyBroker(), observed_at=NOW))
    assert first == second
    assert first.event_count == 3
    assert all(len(event.payload_sha256) == 64 for event in first.events)
    assert {event.event_type for event in first.events} == {
        "equity_position",
        "equity_order",
        "option_order",
    }


class NaiveTimestampBroker(FakeReadOnlyBroker):
    async def list_equity_positions(self) -> list[BrokerPosition]:
        return [
            BrokerPosition(
                "position-1",
                "AAPL",
                Decimal(2),
                None,
                datetime(2026, 8, 29),  # noqa: DTZ001 - deliberately invalid broker data
            )
        ]


def test_snapshot_rejects_naive_broker_timestamps() -> None:
    with pytest.raises(ValueError, match="timezone"):
        asyncio.run(collect_read_only_snapshot(NaiveTimestampBroker(), observed_at=NOW))
