from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from tradna_backend.config import Settings


class ConfigurationTests(unittest.TestCase):
    def test_provider_configuration_fails_closed(self) -> None:
        settings = Settings(
            "test",
            "postgresql://unused",
            "",
            "",
            "",
            "",
            "https://agent.robinhood.com/mcp/trading",
            60,
        )
        with self.assertRaises(RuntimeError):
            settings.require_alpaca()
        with self.assertRaises(RuntimeError):
            settings.require_massive()
        with self.assertRaises(RuntimeError):
            settings.require_client_auth()
        settings.validate_worker()

    def test_worker_configuration_rejects_insecure_or_aggressive_polling(self) -> None:
        base = Settings.from_environment()
        with self.assertRaises(RuntimeError):
            Settings(
                base.environment,
                base.database_url,
                base.alpaca_api_key,
                base.alpaca_secret_key,
                base.massive_api_key,
                base.client_api_token,
                "http://insecure.example/mcp",
                60,
            ).validate_worker()
        with self.assertRaises(RuntimeError):
            Settings(
                base.environment,
                base.database_url,
                base.alpaca_api_key,
                base.alpaca_secret_key,
                base.massive_api_key,
                base.client_api_token,
                "https://agent.robinhood.com/mcp/trading",
                5,
            ).validate_worker()


if __name__ == "__main__":
    unittest.main()
