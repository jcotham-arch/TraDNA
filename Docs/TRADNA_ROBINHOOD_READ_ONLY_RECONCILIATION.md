# Robinhood read-only reconciliation

Observed on 2026-08-29 through the official Robinhood Trading MCP. This report
contains aggregates only. Account numbers, order identifiers, tax lots, and raw
broker payloads are intentionally excluded from source control.

## Safety boundary

- Discovery and reconciliation used read-only account, position, order-history,
  and realized-P&L tools.
- No order was placed, reviewed, modified, exercised, or cancelled.
- No watchlist or scanner state was changed.
- The dedicated Agentic account had no orders, positions, or realized trades.
- Historical activity was present in the default brokerage account.

## Discovered read capabilities

The connected MCP can supply:

- brokerage-account metadata;
- open equity and option positions;
- paginated equity and option order history, including fills and cancellations;
- per-symbol open equity tax lots;
- aggregate realized P&L by asset class and time window; and
- paginated per-trade realized P&L.

The backend integration should expose only these reads through the
`ReadOnlyBroker` boundary. Mutation tools must remain outside the adapter.

## Aggregate reconciliation

| Asset class | TraDNA CSV reconstruction | Robinhood realized P&L | Difference |
|---|---:|---:|---:|
| Equity | $7,106.72 | $7,118.61 | +$11.89 |
| Options | -$333.88 | -$330.00 | +$3.88 |
| Combined | $6,772.84 | $6,788.61 | +$15.77 |

Robinhood reported 60 equity and 4 option realizing trades. Its per-trade feed
returned 72 records totaling $6,801.10. Eight records had no instrument symbol
and totaled $12.49; removing those records reconciles the per-trade feed exactly
to Robinhood's $6,788.61 aggregate.

The remaining $15.77 difference is therefore between Robinhood's cost-basis
accounting and TraDNA's CSV reconstruction, not an MCP pagination error. It
splits into an $11.89 equity difference and a $3.88 option difference.

## Coverage observations

- Equity order history returned 443 orders across three pages, from 2026-06-23
  through 2026-08-28. Of these, 197 had fills.
- Option order history returned 13 orders, including 6 filled orders.
- The realized-trade feed covered 2026-07-02 through 2026-08-27 and required no
  additional page.
- Three equity positions and no open option positions were present at the time
  of observation.
- One open equity sell order was observed and deliberately left untouched.

## Next reconciliation increment

Persist read results as append-only, payload-hashed broker events before deriving
episodes. Match Robinhood realizing trades to reconstructed closes by asset
class, symbol/contract, quantity, and close timestamp. Keep unmatched or
symbol-less events explicit rather than folding them into another asset class.
Use Robinhood's aggregate P&L as a broker comparison value, not as a replacement
for versioned TraDNA reconstruction or as a tax document.
