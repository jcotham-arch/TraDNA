from dataclasses import replace
from datetime import UTC, datetime
from decimal import Decimal

from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool

from tradna_backend.api import main
from tradna_backend.db.base import Base
from tradna_backend.db.models import BrokerQuoteRecord

TOKEN = "test-token-that-is-at-least-32-characters"
NOW = datetime(2026, 9, 8, 14, 30, tzinfo=UTC)


def test_live_status_is_authenticated_and_execution_disabled(monkeypatch) -> None:
    engine = create_engine(
        "sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )
    Base.metadata.create_all(engine)
    with Session(engine) as db:
        db.add(
            BrokerQuoteRecord(
                provider="robinhood_mcp",
                symbol="RXT",
                observed_at=NOW,
                received_at=NOW,
                mark_price=Decimal("4.25"),
                bid_price=Decimal("4.24"),
                ask_price=Decimal("4.26"),
                previous_close=Decimal("4.10"),
            )
        )
        db.commit()

    def override_db():
        with Session(engine) as db:
            yield db

    monkeypatch.setattr(main, "settings", replace(main.settings, client_api_token=TOKEN))
    main.app.dependency_overrides[main.get_db] = override_db
    try:
        response = TestClient(main.app).get(
            "/v1/live/status", headers={"authorization": f"Bearer {TOKEN}"}
        )
    finally:
        main.app.dependency_overrides.clear()

    assert response.status_code == 200
    payload = response.json()
    assert payload["connection"] == "ready_for_oauth"
    assert payload["execution"] == "disabled"
    assert payload["quotes"][0]["symbol"] == "RXT"
