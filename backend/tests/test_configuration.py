from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from tradna_backend.config import Settings


class ConfigurationTests(unittest.TestCase):
    def test_provider_configuration_fails_closed(self) -> None:
        settings = Settings("test", "postgresql://unused", "", "", "", "")
        with self.assertRaises(RuntimeError):
            settings.require_alpaca()
        with self.assertRaises(RuntimeError):
            settings.require_massive()
        with self.assertRaises(RuntimeError):
            settings.require_client_auth()


if __name__ == "__main__":
    unittest.main()
