from __future__ import annotations

import asyncio
import sys
import unittest
from decimal import Decimal
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from tradna_backend.brokers.robinhood_agentic import (
    READ_ONLY_TOOLS,
    RobinhoodAgenticNotConfigured,
    RobinhoodAgenticReadOnly,
)


class RobinhoodBoundaryTests(unittest.TestCase):
    def test_read_only_adapter_fails_closed_before_configuration(self) -> None:
        adapter = RobinhoodAgenticReadOnly()
        with self.assertRaises(RobinhoodAgenticNotConfigured):
            asyncio.run(adapter.list_equity_positions())

    def test_adapter_exposes_no_order_placement_method(self) -> None:
        adapter = RobinhoodAgenticReadOnly()
        self.assertFalse(hasattr(adapter, "place_equity_order"))
        self.assertFalse(hasattr(adapter, "place_option_order"))

    def test_only_read_tools_are_allowlisted(self) -> None:
        self.assertFalse(
            any("place" in name or "cancel" in name or "review" in name for name in READ_ONLY_TOOLS)
        )

    def test_adapter_normalizes_quotes_and_positions(self) -> None:
        class Caller:
            async def call_tool(self, name, arguments):
                if name == "get_equity_positions":
                    return {
                        "positions": [
                            {
                                "id": "p1",
                                "symbol": "rxt",
                                "quantity": "2",
                                "average_cost": "3.50",
                            }
                        ]
                    }
                if name == "get_equity_quotes":
                    return {
                        "quotes": [
                            {
                                "symbol": "rxt",
                                "price": "4.25",
                                "bid": "4.24",
                                "ask": "4.26",
                            }
                        ]
                    }
                return {"orders": []}

        adapter = RobinhoodAgenticReadOnly(Caller())
        positions = asyncio.run(adapter.list_equity_positions())
        quotes = asyncio.run(adapter.get_equity_quotes(("RXT",)))
        self.assertEqual(Decimal(2), positions[0].quantity)
        self.assertEqual(Decimal("4.25"), quotes[0].mark_price)
