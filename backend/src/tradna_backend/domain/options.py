from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from enum import StrEnum


class OptionRight(StrEnum):
    CALL = "CALL"
    PUT = "PUT"


class OptionTransactionType(StrEnum):
    BUY_TO_OPEN = "BUY_TO_OPEN"
    BUY_TO_CLOSE = "BUY_TO_CLOSE"
    SELL_TO_OPEN = "SELL_TO_OPEN"
    SELL_TO_CLOSE = "SELL_TO_CLOSE"
    ASSIGNMENT = "ASSIGNMENT"
    EXERCISE = "EXERCISE"
    EXPIRATION = "EXPIRATION"


class OptionTradeStatus(StrEnum):
    OPEN = "OPEN"
    PARTIAL = "PARTIAL"
    CLOSED = "CLOSED"


@dataclass(frozen=True, slots=True)
class OptionContract:
    symbol: str
    underlying_symbol: str
    expiration_date: str
    strike_price: Decimal
    right: OptionRight
    contract_multiplier: Decimal = Decimal(100)


@dataclass(frozen=True, slots=True)
class RawOptionActivity:
    id: str
    contract: OptionContract
    transaction_type: OptionTransactionType
    contracts: Decimal
    premium: Decimal
    activity_date: str
    fees: Decimal = Decimal(0)


@dataclass(frozen=True, slots=True)
class OptionExecution:
    id: str
    contract: OptionContract
    transaction_type: OptionTransactionType
    contracts: Decimal
    premium: Decimal
    execution_date: str
    fees: Decimal = Decimal(0)


@dataclass(frozen=True, slots=True)
class OptionTradeEpisode:
    id: str
    contract: OptionContract
    status: OptionTradeStatus
    open_date: str
    close_date: str | None
    net_contracts: Decimal
    average_entry_premium: Decimal
    average_exit_premium: Decimal | None
    realized_pnl: Decimal
    executions: tuple[OptionExecution, ...]

    @property
    def is_closed(self) -> bool:
        return self.status is OptionTradeStatus.CLOSED
