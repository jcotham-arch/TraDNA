from __future__ import annotations

import hashlib
import re
import uuid
from collections import defaultdict
from datetime import UTC, datetime
from decimal import Decimal, InvalidOperation

from tradna_backend.domain.activities import RobinhoodActivity
from tradna_backend.domain.options import (
    OptionContract,
    OptionExecution,
    OptionRight,
    OptionTradeEpisode,
    OptionTradeStatus,
    OptionTransactionType,
    RawOptionActivity,
)

SUPPORTED_CODES = {"BTO", "STC", "STO", "BTC", "OEXP", "OASGN", "OEXER", "OEXC"}
TRANSACTION_TYPES = {
    "BTO": OptionTransactionType.BUY_TO_OPEN,
    "STC": OptionTransactionType.SELL_TO_CLOSE,
    "STO": OptionTransactionType.SELL_TO_OPEN,
    "BTC": OptionTransactionType.BUY_TO_CLOSE,
    "OEXP": OptionTransactionType.EXPIRATION,
    "OASGN": OptionTransactionType.ASSIGNMENT,
    "OEXER": OptionTransactionType.EXERCISE,
    "OEXC": OptionTransactionType.EXERCISE,
}
DESCRIPTION_PATTERN = re.compile(
    r"(?:Option\s+(?:Expiration|Assignment|Exercise)\s+for\s+)?"
    r"([A-Z0-9.\-]+)\s+(\d{1,2}/\d{1,2}/\d{4})\s+(Call|Put)\s+\$?([0-9]+(?:\.[0-9]+)?)",
    re.IGNORECASE,
)
ZERO = Decimal(0)
EPSILON = Decimal("0.000001")


def parse_option_activities(activities: list[RobinhoodActivity]) -> list[RawOptionActivity]:
    return [parsed for activity in activities if (parsed := parse_option_activity(activity))]


def parse_option_activity(activity: RobinhoodActivity) -> RawOptionActivity | None:
    code = activity.trans_code.strip().upper()
    if code not in SUPPORTED_CODES:
        return None
    match = DESCRIPTION_PATTERN.search(activity.description.strip())
    if not match:
        return None

    underlying = activity.instrument.strip().upper() or match.group(1).strip().upper()
    expiration = match.group(2).strip()
    right = OptionRight(match.group(3).strip().upper())
    strike = _decimal(match.group(4))
    contracts = _decimal(activity.quantity)
    if strike is None or contracts is None or abs(contracts) <= ZERO:
        return None
    contracts = abs(contracts)

    transaction_type = TRANSACTION_TYPES[code]
    premium = _money(activity.price)
    if premium is None:
        if transaction_type not in {
            OptionTransactionType.EXPIRATION,
            OptionTransactionType.ASSIGNMENT,
            OptionTransactionType.EXERCISE,
        }:
            return None
        premium = ZERO

    strike_text = format(strike.normalize(), "f")
    contract = OptionContract(
        symbol=f"{underlying}|{expiration}|{right.value}|{strike_text}",
        underlying_symbol=underlying,
        expiration_date=expiration,
        strike_price=strike,
        right=right,
    )
    stable_source = (
        f"{activity.activity_date}|{underlying}|{activity.description.strip()}|{code}|"
        f"{activity.quantity}|{activity.price}|{activity.amount}"
    )
    gross = contracts * premium * contract.contract_multiplier
    actual_cash = _money(activity.amount)
    fees = abs(abs(actual_cash) - gross) if actual_cash is not None else ZERO
    return RawOptionActivity(
        id=_java_name_uuid(stable_source),
        contract=contract,
        transaction_type=transaction_type,
        contracts=contracts,
        premium=abs(premium),
        activity_date=activity.activity_date,
        fees=fees,
    )


def reconstruct_option_trades(activities: list[RawOptionActivity]) -> list[OptionTradeEpisode]:
    grouped: dict[str, list[RawOptionActivity]] = defaultdict(list)
    for activity in activities:
        if activity.contract.symbol and activity.contracts > ZERO:
            grouped[activity.contract.symbol].append(activity)

    episodes: list[OptionTradeEpisode] = []
    for contract_activities in grouped.values():
        ordered = sorted(contract_activities, key=lambda item: _date_key(item.activity_date))
        current: list[OptionExecution] = []
        position = opening_dollars = opening_contracts = ZERO
        closing_dollars = closing_contracts = realized = ZERO
        open_date = ""
        direction: str | None = None

        for activity in ordered:
            if not current:
                open_date = activity.activity_date
            current.append(
                OptionExecution(
                    id=activity.id,
                    contract=activity.contract,
                    transaction_type=activity.transaction_type,
                    contracts=activity.contracts,
                    premium=activity.premium,
                    execution_date=activity.activity_date,
                    fees=activity.fees,
                )
            )
            multiplier = activity.contract.contract_multiplier
            tx = activity.transaction_type
            if tx is OptionTransactionType.BUY_TO_OPEN:
                direction = direction or "LONG"
                position += activity.contracts
                opening_contracts += activity.contracts
                opening_dollars += activity.contracts * activity.premium * multiplier
                realized -= activity.fees
            elif tx is OptionTransactionType.SELL_TO_OPEN:
                direction = direction or "SHORT"
                position -= activity.contracts
                opening_contracts += activity.contracts
                opening_dollars += activity.contracts * activity.premium * multiplier
                realized -= activity.fees
            elif tx in {OptionTransactionType.SELL_TO_CLOSE, OptionTransactionType.BUY_TO_CLOSE}:
                close_contracts = min(activity.contracts, abs(position))
                if close_contracts > ZERO:
                    average_open = (
                        opening_dollars / opening_contracts / multiplier
                        if opening_contracts
                        else ZERO
                    )
                    difference = activity.premium - average_open
                    realized += (
                        (difference if tx is OptionTransactionType.SELL_TO_CLOSE else -difference)
                        * close_contracts
                        * multiplier
                    )
                    closing_contracts += close_contracts
                    closing_dollars += close_contracts * activity.premium * multiplier
                    position += (
                        -close_contracts
                        if tx is OptionTransactionType.SELL_TO_CLOSE
                        else close_contracts
                    )
                realized -= activity.fees
            elif tx is OptionTransactionType.EXPIRATION:
                close_contracts = abs(position)
                if close_contracts > ZERO:
                    closing_contracts += close_contracts
                    average_open = (
                        opening_dollars / opening_contracts / multiplier
                        if opening_contracts
                        else ZERO
                    )
                    realized += (
                        (average_open if direction == "SHORT" else -average_open)
                        * close_contracts
                        * multiplier
                    )
                realized -= activity.fees
                position = ZERO
            elif tx in {OptionTransactionType.ASSIGNMENT, OptionTransactionType.EXERCISE}:
                position = ZERO

            if abs(position) < EPSILON and tx in {
                OptionTransactionType.SELL_TO_CLOSE,
                OptionTransactionType.BUY_TO_CLOSE,
                OptionTransactionType.EXPIRATION,
                OptionTransactionType.ASSIGNMENT,
                OptionTransactionType.EXERCISE,
            }:
                episodes.append(
                    _build_episode(
                        current,
                        open_date,
                        activity.activity_date,
                        OptionTradeStatus.CLOSED,
                        position,
                        opening_dollars,
                        opening_contracts,
                        closing_dollars,
                        closing_contracts,
                        realized,
                    )
                )
                current = []
                position = opening_dollars = opening_contracts = ZERO
                closing_dollars = closing_contracts = realized = ZERO
                open_date = ""
                direction = None

        if current:
            status = (
                OptionTradeStatus.CLOSED
                if abs(position) < EPSILON
                else (
                    OptionTradeStatus.PARTIAL
                    if closing_contracts > ZERO
                    else OptionTradeStatus.OPEN
                )
            )
            episodes.append(
                _build_episode(
                    current,
                    open_date,
                    None,
                    status,
                    position,
                    opening_dollars,
                    opening_contracts,
                    closing_dollars,
                    closing_contracts,
                    realized,
                )
            )

    return sorted(episodes, key=lambda episode: _date_key(episode.open_date), reverse=True)


def _decimal(value: str) -> Decimal | None:
    cleaned = value.strip().replace(",", "").replace("(", "-").replace(")", "")
    cleaned = "".join(
        character for character in cleaned if character.isdigit() or character in ".-"
    )
    try:
        return Decimal(cleaned) if cleaned and cleaned != "-" else None
    except InvalidOperation:
        return None


def _money(value: str) -> Decimal | None:
    return _decimal(value) if value.strip() else None


def _build_episode(
    executions: list[OptionExecution],
    open_date: str,
    close_date: str | None,
    status: OptionTradeStatus,
    position: Decimal,
    opening_dollars: Decimal,
    opening_contracts: Decimal,
    closing_dollars: Decimal,
    closing_contracts: Decimal,
    realized: Decimal,
) -> OptionTradeEpisode:
    contract = executions[0].contract
    average_entry = (
        opening_dollars / opening_contracts / contract.contract_multiplier
        if opening_contracts > ZERO
        else executions[0].premium
    )
    average_exit = (
        closing_dollars / closing_contracts / contract.contract_multiplier
        if closing_contracts > ZERO
        else None
    )
    return OptionTradeEpisode(
        id=_java_name_uuid(f"{contract.symbol}|{open_date}|{executions[0].id}"),
        contract=contract,
        status=status,
        open_date=open_date or executions[0].execution_date,
        close_date=close_date,
        net_contracts=position,
        average_entry_premium=average_entry,
        average_exit_premium=average_exit,
        realized_pnl=realized,
        executions=tuple(executions),
    )


def _date_key(value: str) -> datetime:
    for pattern in ("%m/%d/%Y", "%Y-%m-%d", "%m/%d/%Y %H:%M:%S"):
        try:
            return datetime.strptime(value, pattern).replace(tzinfo=UTC)
        except ValueError:
            pass
    return datetime.min.replace(tzinfo=UTC)


def _java_name_uuid(value: str) -> str:
    digest = hashlib.md5(value.encode("utf-8"), usedforsecurity=False).digest()
    return str(uuid.UUID(bytes=digest, version=3))
