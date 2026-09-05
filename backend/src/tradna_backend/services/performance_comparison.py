from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal


@dataclass(frozen=True, slots=True)
class PerformanceSample:
    label: str
    starting_value: Decimal
    ending_value: Decimal
    realized_pnl: Decimal
    closed_trades: int
    winners: int
    maximum_drawdown_percent: Decimal

    @property
    def return_percent(self) -> Decimal:
        if self.starting_value <= 0:
            raise ValueError("Starting value must be positive.")
        return (self.ending_value - self.starting_value) / self.starting_value * Decimal(100)

    @property
    def win_rate_percent(self) -> Decimal | None:
        return (
            None
            if self.closed_trades == 0
            else Decimal(self.winners) / Decimal(self.closed_trades) * Decimal(100)
        )


@dataclass(frozen=True, slots=True)
class AgentUserComparison:
    agent: PerformanceSample
    user: PerformanceSample
    return_edge_percent: Decimal
    lower_drawdown: str


def compare_agent_with_user(
    agent: PerformanceSample, user: PerformanceSample
) -> AgentUserComparison:
    return AgentUserComparison(
        agent=agent,
        user=user,
        return_edge_percent=agent.return_percent - user.return_percent,
        lower_drawdown=agent.label
        if agent.maximum_drawdown_percent < user.maximum_drawdown_percent
        else user.label
        if user.maximum_drawdown_percent < agent.maximum_drawdown_percent
        else "tie",
    )
