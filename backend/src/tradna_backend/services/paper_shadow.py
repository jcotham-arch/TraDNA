from __future__ import annotations

from dataclasses import dataclass, replace
from datetime import UTC, datetime, timedelta
from decimal import ROUND_DOWN, Decimal
from enum import StrEnum

from tradna_backend.domain.trading_universe import is_agentic_eligible

STARTING_CASH = Decimal("5000.00")
MAX_ENTRY_PERCENT = Decimal(10)
MAX_MARK_AGE = timedelta(minutes=20)


class ShadowDecision(StrEnum):
    WAIT = "WAIT"
    AVOID = "AVOID"
    FAVORABLE = "FAVORABLE"
    HIGH_CONVICTION = "HIGH_CONVICTION"


@dataclass(frozen=True, slots=True)
class FrozenRecommendation:
    id: str
    symbol: str
    decision: ShadowDecision
    created_at: datetime
    price: Decimal
    stop_price: Decimal | None = None
    target_price: Decimal | None = None


@dataclass(frozen=True, slots=True)
class PaperPosition:
    recommendation_id: str
    symbol: str
    quantity: Decimal
    entry_price: Decimal
    last_price: Decimal
    stop_price: Decimal | None
    target_price: Decimal | None

    @property
    def market_value(self) -> Decimal:
        return self.quantity * self.last_price


@dataclass(frozen=True, slots=True)
class PaperLedger:
    cash: Decimal = STARTING_CASH
    realized_pnl: Decimal = Decimal(0)
    positions: tuple[PaperPosition, ...] = ()

    @property
    def equity(self) -> Decimal:
        return self.cash + sum((item.market_value for item in self.positions), Decimal(0))


@dataclass(frozen=True, slots=True)
class ShadowEvent:
    event_type: str
    occurred_at: datetime
    recommendation_id: str
    symbol: str
    price: Decimal
    quantity: Decimal
    reason: str


def apply_recommendation(
    ledger: PaperLedger,
    recommendation: FrozenRecommendation,
    *,
    observed_at: datetime,
) -> tuple[PaperLedger, ShadowEvent]:
    _aware(observed_at)
    _aware(recommendation.created_at)
    if recommendation.id in {item.recommendation_id for item in ledger.positions}:
        return ledger, _event("REJECTED", recommendation, observed_at, Decimal(0), "duplicate")
    if not is_agentic_eligible(recommendation.symbol):
        return ledger, _event(
            "REJECTED", recommendation, observed_at, Decimal(0), "ineligible-symbol"
        )
    if observed_at - recommendation.created_at > MAX_MARK_AGE:
        return ledger, _event("REJECTED", recommendation, observed_at, Decimal(0), "stale")
    if recommendation.decision not in {
        ShadowDecision.FAVORABLE,
        ShadowDecision.HIGH_CONVICTION,
    }:
        return ledger, _event("OBSERVED", recommendation, observed_at, Decimal(0), "no-entry")
    if recommendation.price <= 0:
        return ledger, _event("REJECTED", recommendation, observed_at, Decimal(0), "invalid-price")

    cap = (ledger.equity * MAX_ENTRY_PERCENT / Decimal(100)).quantize(
        Decimal("0.01"), rounding=ROUND_DOWN
    )
    notional = min(cap, ledger.cash)
    quantity = notional / recommendation.price
    position = PaperPosition(
        recommendation_id=recommendation.id,
        symbol=recommendation.symbol,
        quantity=quantity,
        entry_price=recommendation.price,
        last_price=recommendation.price,
        stop_price=recommendation.stop_price,
        target_price=recommendation.target_price,
    )
    return replace(
        ledger, cash=ledger.cash - notional, positions=(*ledger.positions, position)
    ), _event("FILLED", recommendation, observed_at, quantity, "10-percent-cap")


def mark_positions(
    ledger: PaperLedger, *, symbol: str, price: Decimal, observed_at: datetime
) -> tuple[PaperLedger, tuple[ShadowEvent, ...]]:
    _aware(observed_at)
    if price <= 0:
        raise ValueError("Mark price must be positive.")
    cash = ledger.cash
    realized = ledger.realized_pnl
    remaining: list[PaperPosition] = []
    events: list[ShadowEvent] = []
    for position in ledger.positions:
        if position.symbol != symbol:
            remaining.append(position)
            continue
        marked = replace(position, last_price=price)
        exit_reason = (
            "stop"
            if marked.stop_price is not None and price <= marked.stop_price
            else "target"
            if marked.target_price is not None and price >= marked.target_price
            else None
        )
        if exit_reason is None:
            remaining.append(marked)
            continue
        cash += marked.quantity * price
        realized += marked.quantity * (price - marked.entry_price)
        events.append(
            ShadowEvent(
                "CLOSED",
                observed_at,
                marked.recommendation_id,
                symbol,
                price,
                marked.quantity,
                exit_reason,
            )
        )
    return PaperLedger(cash=cash, realized_pnl=realized, positions=tuple(remaining)), tuple(events)


def _event(
    kind: str, item: FrozenRecommendation, at: datetime, quantity: Decimal, reason: str
) -> ShadowEvent:
    return ShadowEvent(kind, at, item.id, item.symbol, item.price, quantity, reason)


def _aware(value: datetime) -> datetime:
    if value.tzinfo is None:
        raise ValueError("Shadow timestamps must include a timezone.")
    return value.astimezone(UTC)
