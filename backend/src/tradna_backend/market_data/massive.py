from __future__ import annotations

from datetime import UTC, datetime
from decimal import Decimal

import httpx

from .base import Candle


class MassiveBasicStockMarketData:
    """End-of-day stock history adapter; never represents Basic data as live."""

    def __init__(self, api_key: str, client: httpx.AsyncClient) -> None:
        if not api_key:
            raise ValueError("Massive credentials are required by the backend provider.")
        self._api_key = api_key
        self._client = client

    async def get_stock_bars(
        self,
        symbol: str,
        start: datetime,
        end: datetime,
        timeframe: str,
    ) -> list[Candle]:
        multiplier, timespan = _massive_timeframe(timeframe)
        response = await self._client.get(
            f"https://api.massive.com/v2/aggs/ticker/{symbol}/range/"
            f"{multiplier}/{timespan}/{start.date().isoformat()}/{end.date().isoformat()}",
            params={"adjusted": "true", "sort": "asc", "limit": 50_000},
            headers={"Authorization": f"Bearer {self._api_key}"},
        )
        response.raise_for_status()
        return [
            Candle(
                timestamp=datetime.fromtimestamp(item["t"] / 1000, tz=UTC),
                open=Decimal(str(item["o"])),
                high=Decimal(str(item["h"])),
                low=Decimal(str(item["l"])),
                close=Decimal(str(item["c"])),
                volume=Decimal(str(item["v"])),
                provider="massive_stocks_basic",
                source_delay="end_of_day",
            )
            for item in response.json().get("results", [])
        ]


def _massive_timeframe(timeframe: str) -> tuple[int, str]:
    mapping = {
        "1Min": (1, "minute"),
        "5Min": (5, "minute"),
        "15Min": (15, "minute"),
        "1Hour": (1, "hour"),
        "1Day": (1, "day"),
    }
    try:
        return mapping[timeframe]
    except KeyError as error:
        raise ValueError(f"Unsupported Massive timeframe: {timeframe}") from error
