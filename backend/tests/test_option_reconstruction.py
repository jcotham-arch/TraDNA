from decimal import Decimal

from tradna_backend.domain.options import (
    OptionContract,
    OptionRight,
    OptionTradeStatus,
    OptionTransactionType,
    RawOptionActivity,
)
from tradna_backend.services.option_reconstruction import reconstruct_option_trades


def option(
    identifier: str,
    date: str,
    kind: OptionTransactionType,
    contracts: str,
    premium: str,
    fees: str = "0",
) -> RawOptionActivity:
    return RawOptionActivity(
        id=identifier,
        contract=OptionContract(
            "AAPL260821C00200000", "AAPL", "8/21/2026", Decimal(200), OptionRight.CALL
        ),
        transaction_type=kind,
        contracts=Decimal(contracts),
        premium=Decimal(premium),
        activity_date=date,
        fees=Decimal(fees),
    )


def test_long_option_close_applies_multiplier_and_fees() -> None:
    episodes = reconstruct_option_trades(
        [
            option("open", "5/1/2026", OptionTransactionType.BUY_TO_OPEN, "2", "1.25", "1"),
            option("close", "5/4/2026", OptionTransactionType.SELL_TO_CLOSE, "2", "2", "1"),
        ]
    )
    episode = episodes[0]
    assert episode.status is OptionTradeStatus.CLOSED
    assert episode.realized_pnl == Decimal("148.00")
    assert episode.average_exit_premium == Decimal(2)


def test_short_option_expiration_realizes_collected_premium() -> None:
    episode = reconstruct_option_trades(
        [
            option("open", "6/1/2026", OptionTransactionType.SELL_TO_OPEN, "1", "0.80", "0.50"),
            option("expire", "6/19/2026", OptionTransactionType.EXPIRATION, "1", "0", "0.50"),
        ]
    )[0]
    assert episode.status is OptionTradeStatus.CLOSED
    assert episode.realized_pnl == Decimal("79.00")
    assert episode.average_exit_premium == Decimal(0)


def test_partial_option_close_keeps_remaining_contracts() -> None:
    episode = reconstruct_option_trades(
        [
            option("open", "7/1/2026", OptionTransactionType.BUY_TO_OPEN, "3", "1"),
            option("partial", "7/2/2026", OptionTransactionType.SELL_TO_CLOSE, "1", "1.50"),
        ]
    )[0]
    assert episode.status is OptionTradeStatus.PARTIAL
    assert episode.net_contracts == Decimal(2)
    assert episode.realized_pnl == Decimal("50.00")
