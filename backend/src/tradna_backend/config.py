from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class Settings:
    environment: str
    database_url: str
    alpaca_api_key: str
    alpaca_secret_key: str
    massive_api_key: str
    client_api_token: str
    robinhood_mcp_url: str
    sync_interval_seconds: int

    @classmethod
    def from_environment(cls) -> Settings:
        return cls(
            environment=os.getenv("TRADNA_ENVIRONMENT", "development"),
            database_url=os.getenv(
                "TRADNA_DATABASE_URL",
                "postgresql+psycopg://tradna:change-me@localhost:5432/tradna",
            ),
            alpaca_api_key=os.getenv("TRADNA_ALPACA_API_KEY", ""),
            alpaca_secret_key=os.getenv("TRADNA_ALPACA_SECRET_KEY", ""),
            massive_api_key=os.getenv("TRADNA_MASSIVE_API_KEY", ""),
            client_api_token=os.getenv("TRADNA_CLIENT_API_TOKEN", ""),
            robinhood_mcp_url=os.getenv(
                "TRADNA_ROBINHOOD_MCP_URL", "https://agent.robinhood.com/mcp/trading"
            ),
            sync_interval_seconds=int(os.getenv("TRADNA_SYNC_INTERVAL_SECONDS", "60")),
        )

    def require_alpaca(self) -> None:
        if not self.alpaca_api_key or not self.alpaca_secret_key:
            raise RuntimeError("Alpaca backend credentials are not configured.")

    def require_massive(self) -> None:
        if not self.massive_api_key:
            raise RuntimeError("Massive backend credentials are not configured.")

    def require_client_auth(self) -> None:
        if len(self.client_api_token) < 32:
            raise RuntimeError("A client API token of at least 32 characters is required.")

    def validate_worker(self) -> None:
        if not self.robinhood_mcp_url.startswith("https://"):
            raise RuntimeError("Robinhood MCP must use HTTPS.")
        if self.sync_interval_seconds < 15:
            raise RuntimeError("Sync interval cannot be less than 15 seconds.")
