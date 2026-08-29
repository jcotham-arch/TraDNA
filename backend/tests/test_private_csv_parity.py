from __future__ import annotations

import os
import sys
import unittest
from decimal import Decimal
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from tradna_backend.domain.activities import parse_robinhood_csv
from tradna_backend.services.reconciliation import reconcile_stock_trades
from tradna_backend.services.stock_reconstruction import reconstruct_stock_trades


class PrivateCsvParityTests(unittest.TestCase):
    def test_private_robinhood_csv_matches_validated_android_totals(self) -> None:
        csv_path = os.getenv("TRADNA_GOLDEN_CSV")
        if not csv_path:
            self.skipTest("TRADNA_GOLDEN_CSV is not configured")

        activities = parse_robinhood_csv(Path(csv_path).read_text(encoding="utf-8-sig"))
        reconciliation = reconcile_stock_trades(reconstruct_stock_trades(activities))

        self.assertEqual(283, len(activities))
        self.assertEqual(59, reconciliation.episode_count)
        self.assertEqual(57, reconciliation.completed_count)
        self.assertEqual(2, reconciliation.open_or_partial_count)
        self.assertEqual(Decimal("7106.72"), reconciliation.completed_realized_pnl)


if __name__ == "__main__":
    unittest.main()
