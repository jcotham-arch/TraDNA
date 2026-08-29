from __future__ import annotations

import sys
import unittest
from decimal import Decimal
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from tradna_backend.domain.activities import RobinhoodActivity
from tradna_backend.domain.trades import TradeStatus
from tradna_backend.services.stock_reconstruction import reconstruct_stock_trades


def activity(date: str, symbol: str, code: str, quantity: str, price: str, amount: str):
    return RobinhoodActivity(
        date,
        date,
        date,
        symbol,
        f"{symbol} transaction",
        code,
        quantity,
        price,
        amount,
    )


class StockReconstructionTests(unittest.TestCase):
    def test_fifo_partial_exit_matches_android_contract(self) -> None:
        trades = reconstruct_stock_trades(
            [
                activity("1/4/2026", "XYZ", "Sell", "6", "$15.00", "$90.00"),
                activity("1/3/2026", "XYZ", "Buy", "5", "$12.00", "($60.00)"),
                activity("1/2/2026", "XYZ", "Buy", "5", "$10.00", "($50.00)"),
            ]
        )
        trade = trades[0]
        self.assertEqual(TradeStatus.PARTIAL, trade.status)
        self.assertEqual(Decimal(4), trade.remaining_shares)
        self.assertEqual(Decimal(28), trade.realized_pnl)
        self.assertEqual(Decimal(11), trade.average_entry_price)
        self.assertEqual(Decimal(15), trade.average_exit_price)

    def test_exit_then_reentry_creates_independent_episodes(self) -> None:
        trades = reconstruct_stock_trades(
            [
                activity("2/4/2026", "ABC", "Sell", "2", "$25", "$50"),
                activity("2/3/2026", "ABC", "Buy", "2", "$20", "($40)"),
                activity("2/2/2026", "ABC", "Sell", "3", "$12", "$36"),
                activity("2/1/2026", "ABC", "Buy", "3", "$10", "($30)"),
            ]
        )
        self.assertEqual(2, len(trades))
        self.assertEqual([2, 1], [trade.sequence_number for trade in trades])
        self.assertEqual([Decimal(10), Decimal(6)], [trade.realized_pnl for trade in trades])

    def test_sell_without_open_position_is_ignored(self) -> None:
        trades = reconstruct_stock_trades([activity("3/1/2026", "NONE", "Sell", "1", "$10", "$10")])
        self.assertEqual([], trades)


if __name__ == "__main__":
    unittest.main()
