from __future__ import annotations

import asyncio
import sys
import unittest
from datetime import UTC, datetime
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from tradna_backend.market_data.alpaca import AlpacaStockMarketData
from tradna_backend.market_data.massive import MassiveBasicStockMarketData


class MarketDataAdapterTests(unittest.TestCase):
    def test_alpaca_adapter_labels_provider_and_keeps_credentials_in_headers(self) -> None:
        def handler(request: httpx.Request) -> httpx.Response:
            self.assertEqual("alpaca-key", request.headers["APCA-API-KEY-ID"])
            self.assertEqual("alpaca-secret", request.headers["APCA-API-SECRET-KEY"])
            self.assertNotIn("alpaca-key", str(request.url))
            return httpx.Response(
                200,
                json={
                    "bars": [
                        {
                            "t": "2026-08-28T15:00:00Z",
                            "o": 10,
                            "h": 12,
                            "l": 9,
                            "c": 11,
                            "v": 1000,
                        }
                    ]
                },
            )

        async def run() -> None:
            async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
                provider = AlpacaStockMarketData("alpaca-key", "alpaca-secret", client)
                bars = await provider.get_stock_bars(
                    "TEST",
                    datetime(2026, 8, 28, tzinfo=UTC),
                    datetime(2026, 8, 29, tzinfo=UTC),
                    "1Hour",
                )
                self.assertEqual("alpaca_iex", bars[0].provider)
                self.assertIsNotNone(bars[0].timestamp.tzinfo)

        asyncio.run(run())

    def test_massive_basic_is_always_labeled_end_of_day(self) -> None:
        def handler(request: httpx.Request) -> httpx.Response:
            self.assertEqual("Bearer massive-key", request.headers["Authorization"])
            self.assertNotIn("massive-key", str(request.url))
            return httpx.Response(
                200,
                json={
                    "results": [{"t": 1787929200000, "o": 10, "h": 12, "l": 9, "c": 11, "v": 1000}]
                },
            )

        async def run() -> None:
            async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
                provider = MassiveBasicStockMarketData("massive-key", client)
                bars = await provider.get_stock_bars(
                    "TEST",
                    datetime(2026, 8, 28, tzinfo=UTC),
                    datetime(2026, 8, 29, tzinfo=UTC),
                    "1Day",
                )
                self.assertEqual("massive_stocks_basic", bars[0].provider)
                self.assertEqual("end_of_day", bars[0].source_delay)
                self.assertEqual(UTC, bars[0].timestamp.tzinfo)

        asyncio.run(run())
