from __future__ import annotations

import asyncio
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from tradna_backend.brokers.robinhood_agentic import (
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
