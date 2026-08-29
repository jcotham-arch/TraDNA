class RobinhoodAgenticNotConfigured(RuntimeError):
    pass


class RobinhoodAgenticReadOnly:
    """Credential boundary for the official Robinhood Agentic MCP.

    This adapter deliberately exposes no placement methods. Authentication and
    tool transport will be implemented only after the user configures a
    dedicated Agentic Account.
    """

    async def list_equity_positions(self):
        raise RobinhoodAgenticNotConfigured(
            "Robinhood Agentic read-only connection has not been configured."
        )

    async def list_equity_orders(self):
        raise RobinhoodAgenticNotConfigured(
            "Robinhood Agentic read-only connection has not been configured."
        )

    async def list_option_orders(self):
        raise RobinhoodAgenticNotConfigured(
            "Robinhood Agentic read-only connection has not been configured."
        )
