import hmac
from decimal import Decimal

from fastapi import Depends, FastAPI, Header, HTTPException, Request, status
from pydantic import BaseModel

from tradna_backend import __version__
from tradna_backend.config import Settings
from tradna_backend.services.historical_report import build_robinhood_csv_report

settings = Settings.from_environment()
app = FastAPI(
    title="TraDNA API",
    version=__version__,
    description="Evidence-based trading coaching backend. No live execution endpoints.",
)
MAX_CSV_BYTES = 10 * 1024 * 1024


class HistoricalReportSummary(BaseModel):
    report_version: str
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


def require_client_token(authorization: str | None = Header(default=None)) -> None:
    try:
        settings.require_client_auth()
    except RuntimeError as error:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Client authentication is not configured.",
        ) from error
    prefix = "Bearer "
    supplied = (
        authorization[len(prefix) :] if authorization and authorization.startswith(prefix) else ""
    )
    if not supplied or not hmac.compare_digest(supplied, settings.client_api_token):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid client credentials.",
            headers={"WWW-Authenticate": "Bearer"},
        )


@app.get("/v1/health", tags=["system"])
def health() -> dict[str, str]:
    return {
        "status": "ok",
        "version": __version__,
        "environment": settings.environment,
        "execution": "disabled",
    }


@app.post(
    "/v1/imports/robinhood-csv/analyze",
    response_model=HistoricalReportSummary,
    tags=["imports"],
    dependencies=[Depends(require_client_token)],
)
async def analyze_robinhood_csv(request: Request) -> HistoricalReportSummary:
    content_type = request.headers.get("content-type", "").split(";", maxsplit=1)[0].strip()
    if content_type not in {"text/csv", "text/plain", "application/csv"}:
        raise HTTPException(status_code=415, detail="Send the CSV as a text/csv request body.")
    raw = await request.body()
    if not raw or len(raw) > MAX_CSV_BYTES:
        raise HTTPException(status_code=413, detail="CSV must be between 1 byte and 10 MiB.")
    try:
        csv_text = raw.decode("utf-8-sig")
        report = build_robinhood_csv_report(csv_text)
    except (UnicodeDecodeError, ValueError) as error:
        raise HTTPException(status_code=422, detail="The Robinhood CSV is invalid.") from error
    return HistoricalReportSummary(
        report_version=report.report_version,
        source_sha256=report.source_sha256,
        activity_count=report.activity_count,
        stock_episode_count=report.stock_episode_count,
        completed_stock_episode_count=report.completed_stock_episode_count,
        open_or_partial_stock_episode_count=report.open_or_partial_stock_episode_count,
        option_episode_count=report.option_episode_count,
        completed_option_episode_count=report.completed_option_episode_count,
        open_or_partial_option_episode_count=report.open_or_partial_option_episode_count,
        stock_realized_pnl=report.stock_realized_pnl,
        option_realized_pnl=report.option_realized_pnl,
        combined_realized_pnl=report.combined_realized_pnl,
    )
