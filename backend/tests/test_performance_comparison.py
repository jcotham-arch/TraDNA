from decimal import Decimal

from tradna_backend.services.performance_comparison import (
    PerformanceSample,
    compare_agent_with_user,
)


def test_compares_percentage_returns_instead_of_raw_dollars() -> None:
    agent = PerformanceSample("agent", Decimal(5000), Decimal(5100), Decimal(100), 2, 2, Decimal(1))
    user = PerformanceSample("user", Decimal(50000), Decimal(50500), Decimal(500), 3, 2, Decimal(2))
    result = compare_agent_with_user(agent, user)
    assert result.return_edge_percent == Decimal("1.00")
    assert result.lower_drawdown == "agent"
