from datetime import UTC, datetime, timedelta
from decimal import Decimal

from tradna_backend.services.paper_shadow import (
    FrozenRecommendation,
    PaperLedger,
    ShadowDecision,
    apply_recommendation,
    mark_positions,
)

NOW = datetime(2026, 8, 31, 14, tzinfo=UTC)


def recommendation(**changes):
    values = {
        "id": "r1",
        "symbol": "RXT",
        "decision": ShadowDecision.FAVORABLE,
        "created_at": NOW,
        "price": Decimal(100),
        "stop_price": Decimal(95),
        "target_price": Decimal(110),
    }
    values.update(changes)
    return FrozenRecommendation(**values)


def test_entry_uses_ten_percent_of_five_thousand() -> None:
    ledger, event = apply_recommendation(PaperLedger(), recommendation(), observed_at=NOW)
    assert event.event_type == "FILLED"
    assert ledger.cash == Decimal("4500.00")
    assert ledger.positions[0].market_value == Decimal("500.00")


def test_rejects_stale_and_duplicate_recommendations() -> None:
    stale, event = apply_recommendation(
        PaperLedger(), recommendation(created_at=NOW - timedelta(hours=1)), observed_at=NOW
    )
    assert event.reason == "stale" and not stale.positions
    ledger, _ = apply_recommendation(PaperLedger(), recommendation(), observed_at=NOW)
    same, duplicate = apply_recommendation(ledger, recommendation(), observed_at=NOW)
    assert duplicate.reason == "duplicate" and same == ledger


def test_target_closes_and_realizes_pnl() -> None:
    ledger, _ = apply_recommendation(PaperLedger(), recommendation(), observed_at=NOW)
    closed, events = mark_positions(
        ledger, symbol="RXT", price=Decimal(110), observed_at=NOW + timedelta(minutes=5)
    )
    assert not closed.positions
    assert closed.realized_pnl == Decimal("50.0")
    assert events[0].reason == "target"


def test_rejects_high_price_symbols_removed_by_user_policy() -> None:
    ledger, event = apply_recommendation(
        PaperLedger(), recommendation(symbol="NVDA"), observed_at=NOW
    )
    assert not ledger.positions
    assert event.reason == "ineligible-symbol"
