from decimal import Decimal

import pytest

from tradna_backend.services.risk_policy import (
    PortfolioRiskContext,
    RiskDecision,
    review_order_notional,
)


def test_allows_agentic_entry_at_exactly_ten_percent() -> None:
    review = review_order_notional(
        requested_notional=Decimal("50.00"),
        portfolio_value=Decimal("500.00"),
        increases_exposure=True,
    )

    assert review.decision is RiskDecision.ALLOW
    assert review.maximum_notional == Decimal("50.00")


def test_rejects_agentic_entry_above_ten_percent() -> None:
    review = review_order_notional(
        requested_notional=Decimal("50.01"),
        portfolio_value=Decimal("500.00"),
        increases_exposure=True,
    )

    assert review.decision is RiskDecision.REJECT
    assert review.maximum_percent == Decimal(10)


def test_reentry_is_reviewed_as_an_independent_order() -> None:
    first = review_order_notional(
        requested_notional=Decimal(50),
        portfolio_value=Decimal(500),
        increases_exposure=True,
    )
    second = review_order_notional(
        requested_notional=Decimal(48),
        portfolio_value=Decimal(480),
        increases_exposure=True,
    )

    assert first.decision is RiskDecision.ALLOW
    assert second.decision is RiskDecision.ALLOW
    assert second.maximum_notional == Decimal("48.00")


def test_supports_a_separate_limit_for_another_account_policy() -> None:
    review = review_order_notional(
        requested_notional=Decimal(25),
        portfolio_value=Decimal(1000),
        increases_exposure=True,
        maximum_percent=Decimal("2.5"),
    )

    assert review.decision is RiskDecision.ALLOW
    assert review.maximum_notional == Decimal("25.00")


def test_does_not_block_an_exposure_reducing_exit() -> None:
    review = review_order_notional(
        requested_notional=Decimal(500),
        portfolio_value=Decimal(500),
        increases_exposure=False,
    )

    assert review.decision is RiskDecision.ALLOW


@pytest.mark.parametrize(
    "context",
    [
        PortfolioRiskContext(kill_switch_active=True),
        PortfolioRiskContext(total_exposure=Decimal(150)),
        PortfolioRiskContext(symbol_exposure=Decimal(100)),
        PortfolioRiskContext(daily_pnl=Decimal(-10)),
        PortfolioRiskContext(entries_today=3),
        PortfolioRiskContext(symbol_entries_today=2),
    ],
)
def test_account_level_guards_reject_new_exposure(context: PortfolioRiskContext) -> None:
    review = review_order_notional(
        requested_notional=Decimal(1),
        portfolio_value=Decimal(500),
        increases_exposure=True,
        context=context,
    )
    assert review.decision is RiskDecision.REJECT


@pytest.mark.parametrize(
    ("requested", "portfolio"),
    [(Decimal("-0.01"), Decimal(500)), (Decimal(1), Decimal(0))],
)
def test_invalid_financial_inputs_fail_closed(requested: Decimal, portfolio: Decimal) -> None:
    with pytest.raises(ValueError):
        review_order_notional(
            requested_notional=requested,
            portfolio_value=portfolio,
            increases_exposure=True,
        )
