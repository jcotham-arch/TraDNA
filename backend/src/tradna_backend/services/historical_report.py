from __future__ import annotations

import hashlib
from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal

from tradna_backend.domain.activities import parse_robinhood_csv
from tradna_backend.domain.options import OptionTradeEpisode
from tradna_backend.domain.trades import TradeEpisode
from tradna_backend.services.option_reconstruction import (
    parse_option_activities,
    reconstruct_option_trades,
)
from tradna_backend.services.reconciliation import reconcile_stock_trades
from tradna_backend.services.stock_reconstruction import reconstruct_stock_trades

CENT = Decimal("0.01")
REPORT_VERSION = "historical-report-v1"


@dataclass(frozen=True, slots=True)
class HistoricalTradingReport:
    report_version: str
    source_type: str
    source_sha256: str
    activity_count: int
    stock_episode_count: int
    completed_stock_episode_count: int
    open_or_partial_stock_episode_count: int
    option_episode_count: int
    completed_option_episode_count: int
    open_or_partial_option_episode_count: int
    stock_realized_pnl: Decimal
    option_realized_pnl: Decimal
    combined_realized_pnl: Decimal
    stock_episodes: tuple[TradeEpisode, ...]
    option_episodes: tuple[OptionTradeEpisode, ...]


def build_robinhood_csv_report(csv_text: str) -> HistoricalTradingReport:
    """Reconstruct one deterministic report without retaining the source document."""
    activities = parse_robinhood_csv(csv_text)
    stock_episodes = reconstruct_stock_trades(activities)
    stock_reconciliation = reconcile_stock_trades(stock_episodes)
    option_episodes = reconstruct_option_trades(parse_option_activities(activities))

    completed_options = tuple(episode for episode in option_episodes if episode.is_closed)
    option_realized = _cents(
        sum((episode.realized_pnl for episode in completed_options), Decimal(0))
    )
    stock_realized = stock_reconciliation.completed_realized_pnl

    return HistoricalTradingReport(
        report_version=REPORT_VERSION,
        source_type="robinhood_csv",
        source_sha256=hashlib.sha256(csv_text.encode("utf-8")).hexdigest(),
        activity_count=len(activities),
        stock_episode_count=stock_reconciliation.episode_count,
        completed_stock_episode_count=stock_reconciliation.completed_count,
        open_or_partial_stock_episode_count=stock_reconciliation.open_or_partial_count,
        option_episode_count=len(option_episodes),
        completed_option_episode_count=len(completed_options),
        open_or_partial_option_episode_count=len(option_episodes) - len(completed_options),
        stock_realized_pnl=stock_realized,
        option_realized_pnl=option_realized,
        combined_realized_pnl=_cents(stock_realized + option_realized),
        stock_episodes=tuple(stock_episodes),
        option_episodes=tuple(option_episodes),
    )


def _cents(value: Decimal) -> Decimal:
    return value.quantize(CENT, rounding=ROUND_HALF_UP)
