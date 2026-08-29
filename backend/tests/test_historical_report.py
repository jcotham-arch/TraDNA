from decimal import Decimal

from tradna_backend.services.historical_report import build_robinhood_csv_report


def test_report_combines_stock_and_option_results_without_retaining_csv() -> None:
    csv_text = """Activity Date,Process Date,Settle Date,Instrument,Description,Trans Code,Quantity,Price,Amount
5/8/2026,5/8/2026,5/10/2026,AAPL,Option Expiration for AAPL 5/8/2026 Call $15.00,OEXP,1,,
5/3/2026,5/3/2026,5/5/2026,AAPL,AAPL 5/8/2026 Call $15.00,STO,1,$1.00,$99.95
5/2/2026,5/2/2026,5/4/2026,AAPL,AAPL,Sell,2,$12.00,$24.00
5/1/2026,5/1/2026,5/3/2026,AAPL,AAPL,Buy,2,$10.00,($20.00)
"""

    report = build_robinhood_csv_report(csv_text)

    assert report.activity_count == 4
    assert report.stock_episode_count == 1
    assert report.option_episode_count == 1
    assert report.stock_realized_pnl == Decimal("4.00")
    assert report.option_realized_pnl == Decimal("99.95")
    assert report.combined_realized_pnl == Decimal("103.95")
    assert len(report.source_sha256) == 64
    assert csv_text not in repr(report)
