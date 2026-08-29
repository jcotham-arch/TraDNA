from dataclasses import replace

from fastapi.testclient import TestClient

from tradna_backend.api import main

CSV = """Activity Date,Process Date,Settle Date,Instrument,Description,Trans Code,Quantity,Price,Amount
5/2/2026,5/2/2026,5/4/2026,AAPL,AAPL,Sell,2,$12.00,$24.00
5/1/2026,5/1/2026,5/3/2026,AAPL,AAPL,Buy,2,$10.00,($20.00)
"""
TOKEN = "test-token-that-is-at-least-32-characters"


def test_import_requires_configured_authentication(monkeypatch) -> None:
    monkeypatch.setattr(main, "settings", replace(main.settings, client_api_token=""))
    response = TestClient(main.app).post(
        "/v1/imports/robinhood-csv/analyze", content=CSV, headers={"content-type": "text/csv"}
    )
    assert response.status_code == 503


def test_import_rejects_invalid_token(monkeypatch) -> None:
    monkeypatch.setattr(main, "settings", replace(main.settings, client_api_token=TOKEN))
    response = TestClient(main.app).post(
        "/v1/imports/robinhood-csv/analyze",
        content=CSV,
        headers={"content-type": "text/csv", "authorization": "Bearer wrong"},
    )
    assert response.status_code == 401


def test_authenticated_import_returns_summary_only(monkeypatch) -> None:
    monkeypatch.setattr(main, "settings", replace(main.settings, client_api_token=TOKEN))
    response = TestClient(main.app).post(
        "/v1/imports/robinhood-csv/analyze",
        content=CSV,
        headers={"content-type": "text/csv", "authorization": f"Bearer {TOKEN}"},
    )
    assert response.status_code == 200
    payload = response.json()
    assert payload["activity_count"] == 2
    assert payload["stock_episode_count"] == 1
    assert payload["combined_realized_pnl"] == "4.00"
    assert "stock_episodes" not in payload
