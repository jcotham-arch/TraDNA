from __future__ import annotations

from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal

from tradna_backend.domain.trades import TradeEpisode, TradeStatus


@dataclass(frozen=True, slots=True)
class StockReconciliation:
    episode_count: int
    completed_count: int
    open_or_partial_count: int
    completed_realized_pnl: Decimal
    all_matched_realized_pnl: Decimal


def reconcile_stock_trades(trades: list[TradeEpisode]) -> StockReconciliation:
    completed = [trade for trade in trades if trade.status == TradeStatus.CLOSED]
    completed_pnl = sum((trade.realized_pnl for trade in completed), Decimal(0))
    all_matched_pnl = sum((trade.realized_pnl for trade in trades), Decimal(0))
    return StockReconciliation(
        episode_count=len(trades),
        completed_count=len(completed),
        open_or_partial_count=len(trades) - len(completed),
        completed_realized_pnl=_currency(completed_pnl),
        all_matched_realized_pnl=_currency(all_matched_pnl),
    )


def _currency(value: Decimal) -> Decimal:
    return value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
