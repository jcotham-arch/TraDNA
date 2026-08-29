from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from decimal import Decimal

from tradna_backend.domain.activities import RobinhoodActivity, parse_money, parse_number
from tradna_backend.domain.trades import StockExecution, TradeEpisode, TradeStatus

EPSILON = Decimal("0.000001")


@dataclass(slots=True)
class _Lot:
    quantity: Decimal
    cost_per_share: Decimal


@dataclass(slots=True)
class _Episode:
    symbol: str
    sequence_number: int
    open_date: str
    close_date: str | None = None
    total_shares_bought: Decimal = Decimal(0)
    total_shares_sold: Decimal = Decimal(0)
    total_buy_cost: Decimal = Decimal(0)
    total_sell_proceeds: Decimal = Decimal(0)
    realized_pnl: Decimal = Decimal(0)
    executions: list[StockExecution] = field(default_factory=list)
    lots: list[_Lot] = field(default_factory=list)


def reconstruct_stock_trades(activities: list[RobinhoodActivity]) -> list[TradeEpisode]:
    chronological = [
        activity
        for activity in reversed(activities)
        if activity.instrument and activity.trans_code.strip().upper() in {"BUY", "SELL"}
    ]
    current: dict[str, _Episode] = {}
    sequences: dict[str, int] = {}
    completed: list[TradeEpisode] = []

    for activity in chronological:
        symbol = activity.instrument.strip()
        side = activity.trans_code.strip().upper()
        quantity = parse_number(activity.quantity)
        if quantity is None or quantity <= 0:
            continue
        stated_price = abs(parse_money(activity.price) or Decimal(0))
        amount = parse_money(activity.amount)

        if side == "BUY":
            episode = current.get(symbol)
            if episode is None:
                next_sequence = sequences.get(symbol, 0) + 1
                sequences[symbol] = next_sequence
                episode = _Episode(symbol, next_sequence, activity.activity_date)
                current[symbol] = episode
            buy_cash = (
                abs(amount)
                if amount is not None and amount < 0
                else quantity * stated_price
                if stated_price > 0
                else Decimal(0)
            )
            effective_price = buy_cash / quantity if quantity > EPSILON else stated_price
            episode.total_shares_bought += quantity
            episode.total_buy_cost += buy_cash
            episode.lots.append(_Lot(quantity, effective_price))
            episode.executions.append(
                StockExecution(
                    activity.activity_date,
                    symbol,
                    "BUY",
                    quantity,
                    stated_price,
                    buy_cash,
                    activity,
                )
            )
            continue

        episode = current.get(symbol)
        if episode is None:
            continue
        available = sum((lot.quantity for lot in episode.lots), Decimal(0))
        sell_quantity = min(quantity, available)
        if sell_quantity <= EPSILON:
            continue
        full_sell_cash = (
            amount
            if amount is not None and amount > 0
            else quantity * stated_price
            if stated_price > 0
            else Decimal(0)
        )
        proceeds_per_share = full_sell_cash / quantity if quantity > EPSILON else stated_price
        recognized_proceeds = proceeds_per_share * sell_quantity
        quantity_to_match = sell_quantity
        matched_cost_basis = Decimal(0)
        while quantity_to_match > EPSILON and episode.lots:
            lot = episode.lots[0]
            matched = min(lot.quantity, quantity_to_match)
            matched_cost_basis += matched * lot.cost_per_share
            lot.quantity -= matched
            quantity_to_match -= matched
            if lot.quantity <= EPSILON:
                episode.lots.pop(0)
        episode.total_shares_sold += sell_quantity
        episode.total_sell_proceeds += recognized_proceeds
        episode.realized_pnl += recognized_proceeds - matched_cost_basis
        episode.executions.append(
            StockExecution(
                activity.activity_date,
                symbol,
                "SELL",
                sell_quantity,
                stated_price,
                recognized_proceeds,
                activity,
            )
        )
        remaining = sum((lot.quantity for lot in episode.lots), Decimal(0))
        if remaining <= EPSILON:
            episode.close_date = activity.activity_date
            completed.append(_to_domain(episode))
            del current[symbol]

    completed.extend(_to_domain(episode) for episode in current.values())
    return sorted(
        completed,
        key=lambda trade: (_date_key(trade.open_date), trade.sequence_number),
        reverse=True,
    )


def _to_domain(episode: _Episode) -> TradeEpisode:
    remaining = sum((lot.quantity for lot in episode.lots), Decimal(0))
    average_entry = (
        episode.total_buy_cost / episode.total_shares_bought
        if episode.total_shares_bought > EPSILON
        else Decimal(0)
    )
    average_exit = (
        episode.total_sell_proceeds / episode.total_shares_sold
        if episode.total_shares_sold > EPSILON
        else None
    )
    status = (
        TradeStatus.CLOSED
        if remaining <= EPSILON
        else TradeStatus.PARTIAL
        if episode.total_shares_sold > EPSILON
        else TradeStatus.OPEN
    )
    return TradeEpisode(
        id=f"{episode.symbol}-{episode.sequence_number}",
        symbol=episode.symbol,
        sequence_number=episode.sequence_number,
        open_date=episode.open_date,
        close_date=episode.close_date,
        total_shares_bought=episode.total_shares_bought,
        total_shares_sold=episode.total_shares_sold,
        remaining_shares=remaining,
        total_buy_cost=episode.total_buy_cost,
        total_sell_proceeds=episode.total_sell_proceeds,
        average_entry_price=average_entry,
        average_exit_price=average_exit,
        realized_pnl=episode.realized_pnl,
        status=status,
        executions=tuple(episode.executions),
    )


def _date_key(value: str) -> date:
    try:
        month, day, year = (int(piece) for piece in value.split("/"))
        return date(year, month, day)
    except (TypeError, ValueError):
        return date.min
