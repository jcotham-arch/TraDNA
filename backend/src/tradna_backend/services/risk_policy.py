from __future__ import annotations

from dataclasses import dataclass
from decimal import ROUND_DOWN, Decimal
from enum import StrEnum

AGENTIC_ACCOUNT_MAX_ENTRY_NOTIONAL_PERCENT = Decimal(10)
AGENTIC_MAX_TOTAL_EXPOSURE_PERCENT = Decimal(30)
AGENTIC_MAX_SYMBOL_EXPOSURE_PERCENT = Decimal(20)
AGENTIC_MAX_DAILY_LOSS_PERCENT = Decimal(2)
AGENTIC_MAX_ENTRIES_PER_DAY = 3
AGENTIC_MAX_ENTRIES_PER_SYMBOL_DAY = 2
_ONE_HUNDRED = Decimal(100)
_CENT = Decimal("0.01")


class RiskDecision(StrEnum):
    ALLOW = "ALLOW"
    REJECT = "REJECT"


@dataclass(frozen=True, slots=True)
class OrderRiskReview:
    decision: RiskDecision
    requested_notional: Decimal
    portfolio_value: Decimal
    maximum_notional: Decimal
    maximum_percent: Decimal
    reason: str


@dataclass(frozen=True, slots=True)
class PortfolioRiskContext:
    total_exposure: Decimal = Decimal(0)
    symbol_exposure: Decimal = Decimal(0)
    daily_pnl: Decimal = Decimal(0)
    entries_today: int = 0
    symbol_entries_today: int = 0
    kill_switch_active: bool = False


def review_order_notional(
    *,
    requested_notional: Decimal,
    portfolio_value: Decimal,
    increases_exposure: bool,
    maximum_percent: Decimal = AGENTIC_ACCOUNT_MAX_ENTRY_NOTIONAL_PERCENT,
    context: PortfolioRiskContext | None = None,
) -> OrderRiskReview:
    """Apply the non-overridable per-order entry-notional limit.

    Opening and add-on orders are capped independently. Exposure-reducing orders
    are not blocked by this rule; preventing an exit could increase risk.
    """
    context = context or PortfolioRiskContext()
    if requested_notional < 0:
        raise ValueError("Requested notional cannot be negative.")
    if portfolio_value <= 0:
        raise ValueError("Portfolio value must be positive.")
    if maximum_percent <= 0 or maximum_percent > _ONE_HUNDRED:
        raise ValueError("Maximum percent must be greater than 0 and no more than 100.")

    maximum_notional = (portfolio_value * maximum_percent / _ONE_HUNDRED).quantize(
        _CENT, rounding=ROUND_DOWN
    )

    if context.kill_switch_active:
        return _reject(
            requested_notional,
            portfolio_value,
            maximum_notional,
            maximum_percent,
            "The account kill switch is active.",
        )

    if not increases_exposure:
        return OrderRiskReview(
            decision=RiskDecision.ALLOW,
            requested_notional=requested_notional,
            portfolio_value=portfolio_value,
            maximum_notional=maximum_notional,
            maximum_percent=maximum_percent,
            reason="Exposure-reducing orders are not limited by the entry-size rule.",
        )

    projected_total = context.total_exposure + requested_notional
    projected_symbol = context.symbol_exposure + requested_notional
    if projected_total > portfolio_value * AGENTIC_MAX_TOTAL_EXPOSURE_PERCENT / _ONE_HUNDRED:
        return _reject(
            requested_notional,
            portfolio_value,
            maximum_notional,
            maximum_percent,
            "Projected total exposure exceeds 30% of portfolio value.",
        )
    if projected_symbol > portfolio_value * AGENTIC_MAX_SYMBOL_EXPOSURE_PERCENT / _ONE_HUNDRED:
        return _reject(
            requested_notional,
            portfolio_value,
            maximum_notional,
            maximum_percent,
            "Projected symbol exposure exceeds 20% of portfolio value.",
        )
    if context.daily_pnl <= -(portfolio_value * AGENTIC_MAX_DAILY_LOSS_PERCENT / _ONE_HUNDRED):
        return _reject(
            requested_notional,
            portfolio_value,
            maximum_notional,
            maximum_percent,
            "The 2% daily loss circuit breaker is active.",
        )
    if context.entries_today >= AGENTIC_MAX_ENTRIES_PER_DAY:
        return _reject(
            requested_notional,
            portfolio_value,
            maximum_notional,
            maximum_percent,
            "The daily entry limit has been reached.",
        )
    if context.symbol_entries_today >= AGENTIC_MAX_ENTRIES_PER_SYMBOL_DAY:
        return _reject(
            requested_notional,
            portfolio_value,
            maximum_notional,
            maximum_percent,
            "The per-symbol daily entry limit has been reached.",
        )

    if requested_notional > maximum_notional:
        return OrderRiskReview(
            decision=RiskDecision.REJECT,
            requested_notional=requested_notional,
            portfolio_value=portfolio_value,
            maximum_notional=maximum_notional,
            maximum_percent=maximum_percent,
            reason=(
                f"Entry notional exceeds {maximum_percent}% of portfolio value "
                f"(${maximum_notional})."
            ),
        )

    return OrderRiskReview(
        decision=RiskDecision.ALLOW,
        requested_notional=requested_notional,
        portfolio_value=portfolio_value,
        maximum_notional=maximum_notional,
        maximum_percent=maximum_percent,
        reason=f"Entry notional is within the {maximum_percent}% per-order limit.",
    )


def _reject(requested, portfolio, maximum, percent, reason) -> OrderRiskReview:
    return OrderRiskReview(RiskDecision.REJECT, requested, portfolio, maximum, percent, reason)
