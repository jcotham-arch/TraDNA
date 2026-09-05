# Android-to-backend migration

## Golden Android baseline

The current private Robinhood export has validated aggregate expectations:

- 283 accepted brokerage activities
- 59 reconstructed stock episodes
- 57 completed stock episodes
- 2 open or partial stock episodes
- $7,106.72 completed reconstructed stock P&L under FIFO
- approximately -$333.88 reconstructed option P&L
- approximately $6,772.84 combined reconstructed trading P&L

These aggregates are test expectations, not official tax-lot statements.

## Strangler sequence

1. Port parsing and stock reconstruction.
2. Compare private CSV aggregates locally; never commit the CSV.
3. Port option reconstruction and combined reconciliation.
4. Move market-data retrieval and caching to the backend.
5. Move historical analysis and counterfactual reports.
6. Move training records, predictions, and calibration to PostgreSQL.
7. Add authenticated Android API access.
8. Switch one Android screen at a time to backend responses.
9. Retain Room for offline cache and pending journal writes.
10. Remove mobile provider secrets after backend parity.

## Robinhood credential boundary

No Robinhood credential is required for phases 1-6. When the user is ready, the
backend will implement the official Agentic MCP read-only tools first: positions,
tax lots, and order history. Placement tools remain absent until paper-trading
and risk-gate acceptance criteria are satisfied.

Read-only MCP capability discovery and the first broker-to-CSV aggregate
reconciliation were completed on 2026-08-29. See
`TRADNA_ROBINHOOD_READ_ONLY_RECONCILIATION.md` for the sanitized results and the
remaining $15.77 cost-basis/reconstruction delta.
