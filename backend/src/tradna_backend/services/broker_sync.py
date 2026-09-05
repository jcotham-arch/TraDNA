from __future__ import annotations

import asyncio
import hashlib
import json
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from decimal import Decimal
from typing import Any

from tradna_backend.brokers.base import BrokerOrder, BrokerPosition, ReadOnlyBroker

SYNC_VERSION = "broker-sync-v1"


@dataclass(frozen=True, slots=True)
class NormalizedBrokerEvent:
    provider_event_id: str
    event_type: str
    occurred_at: datetime
    received_at: datetime
    payload: dict[str, Any]
    payload_sha256: str


@dataclass(frozen=True, slots=True)
class ReadOnlyBrokerSnapshot:
    sync_version: str
    observed_at: datetime
    events: tuple[NormalizedBrokerEvent, ...]

    @property
    def event_count(self) -> int:
        return len(self.events)


async def collect_read_only_snapshot(
    broker: ReadOnlyBroker, *, observed_at: datetime | None = None
) -> ReadOnlyBrokerSnapshot:
    """Collect broker state concurrently without exposing a mutation path."""
    received_at = _aware(observed_at or datetime.now(UTC))
    positions, equity_orders, option_orders = await asyncio.gather(
        broker.list_equity_positions(),
        broker.list_equity_orders(),
        broker.list_option_orders(),
    )
    events = [
        *(_position_event(position, received_at) for position in positions),
        *(_order_event(order, "equity_order", received_at) for order in equity_orders),
        *(_order_event(order, "option_order", received_at) for order in option_orders),
    ]
    event_ids = [event.provider_event_id for event in events]
    if len(event_ids) != len(set(event_ids)):
        raise ValueError("Broker snapshot contains duplicate provider event IDs.")
    return ReadOnlyBrokerSnapshot(
        sync_version=SYNC_VERSION,
        observed_at=received_at,
        events=tuple(
            sorted(events, key=lambda event: (event.occurred_at, event.provider_event_id))
        ),
    )


def _position_event(position: BrokerPosition, received_at: datetime) -> NormalizedBrokerEvent:
    occurred_at = _aware(position.observed_at)
    payload = _json_payload(position)
    return _event(
        f"position:{position.external_id}", "equity_position", occurred_at, received_at, payload
    )


def _order_event(
    order: BrokerOrder, event_type: str, received_at: datetime
) -> NormalizedBrokerEvent:
    occurred_at = _aware(order.filled_at or order.submitted_at or received_at)
    payload = _json_payload(order)
    return _event(
        f"{event_type}:{order.external_id}", event_type, occurred_at, received_at, payload
    )


def _event(
    provider_event_id: str,
    event_type: str,
    occurred_at: datetime,
    received_at: datetime,
    payload: dict[str, Any],
) -> NormalizedBrokerEvent:
    canonical = json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
    payload_sha256 = hashlib.sha256(canonical.encode()).hexdigest()
    return NormalizedBrokerEvent(
        provider_event_id=f"{provider_event_id}:{payload_sha256[:16]}",
        event_type=event_type,
        occurred_at=occurred_at,
        received_at=received_at,
        payload=payload,
        payload_sha256=payload_sha256,
    )


def _json_payload(value: BrokerPosition | BrokerOrder) -> dict[str, Any]:
    return {key: _json_value(item) for key, item in asdict(value).items()}


def _json_value(value: Any) -> Any:
    if isinstance(value, Decimal):
        return format(value, "f")
    if isinstance(value, datetime):
        return _aware(value).isoformat()
    return value


def _aware(value: datetime) -> datetime:
    if value.tzinfo is None:
        raise ValueError("Broker timestamps must include a timezone.")
    return value.astimezone(UTC)
