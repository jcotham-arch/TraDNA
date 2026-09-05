from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime
from decimal import Decimal
from typing import Any, Protocol

from tradna_backend.brokers.base import BrokerOrder, BrokerPosition

READ_ONLY_TOOLS = frozenset(
    {
        "get_accounts",
        "get_portfolio",
        "get_realized_pnl",
        "get_pnl_trade_history",
        "get_equity_positions",
        "get_equity_tax_lots",
        "get_equity_orders",
        "get_option_positions",
        "get_option_orders",
        "get_equity_quotes",
        "get_option_quotes",
        "get_equity_historicals",
    }
)


class RobinhoodAgenticNotConfigured(RuntimeError):
    pass


class McpToolCaller(Protocol):
    async def call_tool(self, name: str, arguments: dict[str, Any]) -> Any: ...


@dataclass(frozen=True, slots=True)
class BrokerQuote:
    symbol: str
    mark_price: Decimal
    bid_price: Decimal | None
    ask_price: Decimal | None
    previous_close: Decimal | None
    observed_at: datetime
    provider: str = "robinhood_mcp"


class RobinhoodAgenticReadOnly:
    """Read-only adapter over Robinhood's official Trading MCP.

    The injected caller owns OAuth and Streamable HTTP transport. This adapter
    refuses every tool outside its explicit read-only allowlist.
    """

    def __init__(self, caller: McpToolCaller | None = None) -> None:
        self._caller = caller

    async def list_equity_positions(self) -> list[BrokerPosition]:
        payload = await self._call("get_equity_positions", {})
        observed_at = datetime.now(UTC)
        return [
            BrokerPosition(
                external_id=str(_pick(item, "id", "instrument_id", "symbol")),
                symbol=str(_pick(item, "symbol", "ticker")).upper(),
                quantity=_decimal(_pick(item, "quantity", "shares")),
                average_cost=_optional_decimal(
                    _pick_optional(
                        item, "average_cost", "average_buy_price", "cost_basis_per_share"
                    )
                ),
                observed_at=observed_at,
            )
            for item in _items(payload, "positions", "results")
        ]

    async def list_equity_orders(self) -> list[BrokerOrder]:
        return await self._orders("get_equity_orders", "orders", "results")

    async def list_option_orders(self) -> list[BrokerOrder]:
        return await self._orders("get_option_orders", "orders", "results")

    async def get_equity_quotes(self, symbols: tuple[str, ...]) -> list[BrokerQuote]:
        if not symbols:
            return []
        payload = await self._call("get_equity_quotes", {"symbols": list(symbols)})
        fallback_time = datetime.now(UTC)
        return [
            BrokerQuote(
                symbol=str(_pick(item, "symbol", "ticker")).upper(),
                mark_price=_decimal(_pick(item, "mark_price", "last_trade_price", "price")),
                bid_price=_optional_decimal(_pick_optional(item, "bid_price", "bid")),
                ask_price=_optional_decimal(_pick_optional(item, "ask_price", "ask")),
                previous_close=_optional_decimal(
                    _pick_optional(item, "previous_close", "previous_close_price")
                ),
                observed_at=_optional_datetime(
                    _pick_optional(item, "updated_at", "observed_at", "timestamp")
                )
                or fallback_time,
            )
            for item in _items(payload, "quotes", "results")
        ]

    async def get_portfolio(self) -> dict[str, Any]:
        payload = await self._call("get_portfolio", {})
        if not isinstance(payload, dict):
            raise TypeError("Robinhood portfolio response must be an object.")
        return payload

    async def _orders(self, tool: str, *collection_keys: str) -> list[BrokerOrder]:
        payload = await self._call(tool, {})
        return [
            BrokerOrder(
                external_id=str(_pick(item, "id", "order_id")),
                symbol=str(_pick(item, "symbol", "ticker", "chain_symbol")).upper(),
                side=str(_pick(item, "side", "direction")).upper(),
                quantity=_decimal(_pick(item, "quantity", "filled_quantity", "contracts")),
                status=str(_pick(item, "status", "state")).upper(),
                submitted_at=_optional_datetime(
                    _pick_optional(item, "submitted_at", "created_at", "created_time")
                ),
                filled_at=_optional_datetime(
                    _pick_optional(item, "filled_at", "last_transaction_at", "updated_at")
                ),
                average_fill_price=_optional_decimal(
                    _pick_optional(item, "average_fill_price", "average_price", "price")
                ),
            )
            for item in _items(payload, *collection_keys)
        ]

    async def _call(self, name: str, arguments: dict[str, Any]) -> Any:
        if name not in READ_ONLY_TOOLS:
            raise PermissionError(f"Robinhood tool is not allowed by read-only policy: {name}")
        if self._caller is None:
            raise RobinhoodAgenticNotConfigured(
                "Robinhood MCP OAuth transport has not been connected."
            )
        return await self._caller.call_tool(name, arguments)


def _items(payload: Any, *keys: str) -> list[dict[str, Any]]:
    if isinstance(payload, list):
        items = payload
    elif isinstance(payload, dict):
        items = next((payload[key] for key in keys if isinstance(payload.get(key), list)), None)
        if items is None and all(not isinstance(value, list) for value in payload.values()):
            items = [payload]
    else:
        items = None
    if items is None or not all(isinstance(item, dict) for item in items):
        raise ValueError("Robinhood MCP response did not contain a recognized result list.")
    return items


def _pick(item: dict[str, Any], *keys: str) -> Any:
    value = _pick_optional(item, *keys)
    if value is None or value == "":
        raise ValueError(f"Robinhood MCP item is missing required field: {'/'.join(keys)}")
    return value


def _pick_optional(item: dict[str, Any], *keys: str) -> Any | None:
    return next((item[key] for key in keys if key in item and item[key] is not None), None)


def _decimal(value: Any) -> Decimal:
    try:
        return Decimal(str(value))
    except Exception as error:
        raise ValueError("Robinhood MCP returned an invalid decimal value.") from error


def _optional_decimal(value: Any | None) -> Decimal | None:
    return None if value in (None, "") else _decimal(value)


def _optional_datetime(value: Any | None) -> datetime | None:
    if value in (None, ""):
        return None
    parsed = datetime.fromisoformat(str(value))
    if parsed.tzinfo is None:
        raise ValueError("Robinhood MCP timestamp must include a timezone.")
    return parsed.astimezone(UTC)
