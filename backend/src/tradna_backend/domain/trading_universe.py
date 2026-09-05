UNIVERSE_VERSION = "personal-universe-v2"
AGENTIC_ELIGIBLE_SYMBOLS = frozenset({"RXT", "ONDS", "OKLO", "QBTS", "QUBT", "RGTI"})


def is_agentic_eligible(symbol: str) -> bool:
    return symbol.strip().upper() in AGENTIC_ELIGIBLE_SYMBOLS
