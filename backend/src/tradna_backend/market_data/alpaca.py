from __future__ import annotations

from datetime import datetime
from decimal import Decimal

import httpx

from .base import Candle


class AlpacaStockMarketData:
    def __init__(self, api_key: str, secret_key: str, client: httpx.AsyncClient) -> None:
        if not api_key or not secret_key:
            raise ValueError("Alpaca credentials are required by the backend provider.")
        self._api_key = api_key
        self._secret_key = secret_key
        self._client = client

    async def get_stock_bars(
        self,
        symbol: str,
        start: datetime,
        end: datetime,
        timeframe: str,
    ) -> list[Candle]:
        response = await self._client.get(
            f"https://data.alpaca.markets/v2/stocks/{symbol}/bars",
            params={
                "timeframe": timeframe,
                "start": start.isoformat(),
                "end": end.isoformat(),
                "feed": "iex",
                "adjustment": "all",
                "limit": 10_000,
            },
            headers={
                "APCA-API-KEY-ID": self._api_key,
                "APCA-API-SECRET-KEY": self._secret_key,
            },
        )
        response.raise_for_status()
        return [
            Candle(
                timestamp=datetime.fromisoformat(item["t"]),
                open=Decimal(str(item["o"])),
                high=Decimal(str(item["h"])),
                low=Decimal(str(item["l"])),
                close=Decimal(str(item["c"])),
                volume=Decimal(str(item["v"])),
                provider="alpaca_iex",
                source_delay="provider_entitlement",
            )
            for item in response.json().get("bars", [])
        ]
