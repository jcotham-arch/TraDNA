from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal
from typing import Protocol


@dataclass(frozen=True, slots=True)
class BrokerPosition:
    external_id: str
    symbol: str
    quantity: Decimal
    average_cost: Decimal | None
    observed_at: datetime


@dataclass(frozen=True, slots=True)
class BrokerOrder:
    external_id: str
    symbol: str
    side: str
    quantity: Decimal
    status: str
    submitted_at: datetime | None
    filled_at: datetime | None
    average_fill_price: Decimal | None


class ReadOnlyBroker(Protocol):
    async def list_equity_positions(self) -> list[BrokerPosition]: ...
    async def list_equity_orders(self) -> list[BrokerOrder]: ...
    async def list_option_orders(self) -> list[BrokerOrder]: ...
