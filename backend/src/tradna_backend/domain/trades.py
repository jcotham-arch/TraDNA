from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from enum import StrEnum

from .activities import RobinhoodActivity


class TradeStatus(StrEnum):
    OPEN = "OPEN"
    PARTIAL = "PARTIAL"
    CLOSED = "CLOSED"


@dataclass(frozen=True, slots=True)
class StockExecution:
    activity_date: str
    symbol: str
    side: str
    quantity: Decimal
    stated_price: Decimal
    actual_cash: Decimal
    source: RobinhoodActivity


@dataclass(frozen=True, slots=True)
class TradeEpisode:
    id: str
    symbol: str
    sequence_number: int
    open_date: str
    close_date: str | None
    total_shares_bought: Decimal
    total_shares_sold: Decimal
    remaining_shares: Decimal
    total_buy_cost: Decimal
    total_sell_proceeds: Decimal
    average_entry_price: Decimal
    average_exit_price: Decimal | None
    realized_pnl: Decimal
    status: TradeStatus
    executions: tuple[StockExecution, ...]
