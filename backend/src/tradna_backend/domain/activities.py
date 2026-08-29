from __future__ import annotations

import csv
from dataclasses import dataclass
from datetime import date
from decimal import Decimal, InvalidOperation
from io import StringIO


@dataclass(frozen=True, slots=True)
class RobinhoodActivity:
    activity_date: str
    process_date: str
    settle_date: str
    instrument: str
    description: str
    trans_code: str
    quantity: str
    price: str
    amount: str


REQUIRED_HEADERS = (
    "Activity Date",
    "Process Date",
    "Settle Date",
    "Instrument",
    "Description",
    "Trans Code",
    "Quantity",
    "Price",
    "Amount",
)


def parse_robinhood_csv(csv_text: str) -> list[RobinhoodActivity]:
    reader = csv.DictReader(StringIO(csv_text.lstrip("\ufeff")))
    headers = tuple(reader.fieldnames or ())
    missing = [header for header in REQUIRED_HEADERS if header not in headers]
    if missing:
        raise ValueError(f"Robinhood CSV is missing required headers: {', '.join(missing)}")

    activities: list[RobinhoodActivity] = []
    for row in reader:
        activity_date = (row.get("Activity Date") or "").strip()
        trans_code = (row.get("Trans Code") or "").strip()
        if not _valid_date(activity_date) or not trans_code:
            continue
        activities.append(
            RobinhoodActivity(
                activity_date=activity_date,
                process_date=(row.get("Process Date") or "").strip(),
                settle_date=(row.get("Settle Date") or "").strip(),
                instrument=(row.get("Instrument") or "").strip(),
                description=(row.get("Description") or "").strip(),
                trans_code=trans_code,
                quantity=(row.get("Quantity") or "").strip(),
                price=(row.get("Price") or "").strip(),
                amount=(row.get("Amount") or "").strip(),
            )
        )
    return activities


def parse_number(value: str) -> Decimal | None:
    try:
        return Decimal(value.replace(",", "").strip())
    except (InvalidOperation, AttributeError):
        return None


def parse_money(value: str) -> Decimal | None:
    if not value or not value.strip():
        return None
    trimmed = value.strip()
    negative = trimmed.startswith("(") and trimmed.endswith(")")
    cleaned = trimmed.replace("$", "").replace(",", "").replace("(", "").replace(")", "").strip()
    try:
        result = Decimal(cleaned)
    except InvalidOperation:
        return None
    return -result if negative else result


def _valid_date(value: str) -> bool:
    try:
        month, day, year = (int(piece) for piece in value.split("/"))
        date(year, month, day)
        return True
    except (TypeError, ValueError):
        return False
