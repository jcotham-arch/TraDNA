# TraDNA agent training roadmap

## Product goal

Learn the user's repeatable trading behavior without confusing historical
correlation with a live-trading mandate. The first product surface is a compact
Trading DNA profile centered on symbol experience, entry location, exit quality,
risk, and evidence strength.

## Evidence pipeline

1. Ingest broker events read-only and retain immutable payload hashes.
2. Reconstruct versioned stock and option trade episodes.
3. Join each entry and exit to point-in-time market data. Never use candles that
   were unavailable when the decision would have been made.
4. Derive features such as distance from session VWAP, EMA alignment, relative
   volume, entry/exit efficiency, favorable excursion, and adverse excursion.
5. Aggregate outcomes by symbol and setup with sample size and confidence.
6. Freeze recommendations prospectively, then score them only after outcomes.

## VWAP interpretation

VWAP is a session-relative location feature, not an instruction by itself. The
initial profile uses three mutually exclusive entry buckets:

- below VWAP: more than 0.25% below reconstructed VWAP;
- near VWAP: within 0.25% on either side; and
- above VWAP: more than 0.25% above reconstructed VWAP.

Each bucket reports historical trade count, profitable rate, average return,
and confidence. Future versions should split opening-drive, pullback, reclaim,
and late-session contexts rather than treating every above-VWAP entry alike.

## Activation gates

The following stages are intentionally separate:

1. **Historical coaching** — read-only analysis of completed trades.
2. **Prospective journal** — recommendations are saved before outcomes and
   evaluated without submitting orders.
3. **Paper shadowing** — proposed orders are simulated with slippage, fees, and
   deterministic risk limits.
4. **Approval-only execution** — every order requires explicit user approval and
   passes an independent risk gate.
5. **Limited automation** — considered only after documented paper and approval
   criteria are met, with small notional caps and a kill switch.

Funding the Agentic account does not advance these stages. No stage may infer
permission to place, modify, exercise, or cancel an order from account funding,
training completion, a model score, or a recommendation.

## Agentic-account risk rule: per-trade entry size

Every order in the dedicated Agentic account that opens or adds exposure is
capped at **10% of current portfolio
value at review time**. The deterministic risk gate calculates the dollar cap,
rounds it down to cents, and rejects—not resizes—any proposal above it. The model
cannot waive or reinterpret the cap.

At a $500 portfolio value, the maximum is $50. This is a maximum, not a minimum;
smaller orders remain permissible. Other brokerage accounts may use a different
explicit policy and must not inherit this Agentic-account setting implicitly.

Dollar-cost averaging and re-entry are allowed only as separately reviewed
orders. Each order receives a fresh calculation against the then-current
portfolio value. Exposure-reducing exits are not blocked by this entry-size rule,
because preventing an exit could increase risk.

This is a per-order limit, not a total-position, daily-loss, or order-frequency
limit. Those protections require separate rules before any automated activation.

## Current increment

The Agent Lab now replaces several overlapping summary cards in its primary flow
with a single Trading DNA card. It ranks symbol experience and compares historical
below/near/above-VWAP entry outcomes. Deep counterfactual and coaching engines
remain available, but the main view prioritizes the user's highest-signal evidence.

Top-level Android navigation is reduced from five destinations to three: Home,
Review, and Agent. The former DNA summary is consolidated into Agent's Trading
DNA profile, while the separate Lab destination is removed as a duplicate of the
Agent training workspace. The analysis engines remain available for continued
integration; only redundant top-level navigation is removed.

## Operating model

TraDNA is intended to become four connected systems with strict boundaries:

1. **Observe** — read Robinhood history and current account state without changing
   it; collect point-in-time market data for the user's preferred trading universe.
2. **Learn** — reconstruct trades, measure entries and exits against VWAP and
   other technical context, and build confidence-weighted personal patterns.
3. **Shadow** — freeze recommendations before outcomes, execute them only in the
   $5,000 paper ledger, and compare the agent with the user's real trades over the
   same dates using percentage and risk-normalized metrics.
4. **Execute** — only after explicit activation, send a reviewed proposal through
   a deterministic risk service that is independent of the model.

The Android app remains the dashboard, journal, approval surface, and offline
cache. A backend worker must own continuous market observation; Android cannot be
trusted to run an uninterrupted market-hours loop in the background.

## Simplified app experience

### Home

- account/data freshness and reconciliation status;
- current portfolio and open-position summary;
- top actionable insight; and
- import/sync and data-integrity access as secondary actions.

### Review

- user's actual reconstructed trades;
- entry, exit, VWAP, excursion, and counterfactual review; and
- fair comparison of user, agent-paper, and eventually combined performance.

### Agent

- Trading DNA: symbol and setup evidence;
- current recommendations with confidence and reasons;
- prospective prediction journal;
- $5,000 Paper Sandbox; and
- readiness and safety-gate status.

## Live shadow protocol

The paper competition must be prospective and reproducible:

- define the watch universe before the session;
- save every evaluated recommendation, including WAIT and AVOID decisions;
- use only data available at the recommendation timestamp;
- simulate fills with spread/slippage and applicable fees;
- enforce the 10% per-entry cap and all additional portfolio limits;
- mark positions from a timestamped market-data source;
- execute simulated stops and targets without hindsight;
- ingest the user's Robinhood fills read-only after they occur; and
- compare equal date windows and percentage returns rather than raw dollars.

The scoreboard will report return, realized and unrealized P&L, win rate,
expectancy, profit factor, maximum drawdown, exposure, average winner/loss,
VWAP-relative entry quality, exit efficiency, and results by symbol/setup.

### Initial personal trading universe (`personal-universe-v2`)

- Technology infrastructure: RXT
- Aerospace, defense, and autonomy: ONDS
- Advanced energy: OKLO
- Quantum computing: QBTS, QUBT, RGTI

NVDA and SPCX remain available as historical learning and comparison data but are
explicitly ineligible for Agentic paper or real-money entries because one whole
share exceeds the intended 10% sizing rule for the $500 pilot account.

The universe is explicit and versioned. Historical evidence may change how a
symbol is scored, but the model cannot add an unrelated ticker to the eligible
universe by itself. Universe changes require a deliberate user-approved version.

## Realistic delivery timeline from 2026-08-29

Calendar dates are targets, while evidence gates determine promotion.

### Stage 0 — Monday instrumentation (2026-08-31 to 2026-09-04)

Target: produce valid prospective observations during live market hours.

- connect the paper ledger to live mark-to-market prices;
- define and persist the initial watch universe;
- timestamp and freeze all recommendations;
- implement simulated entries, exits, stops, targets, and slippage; and
- show the first Agent-versus-User daily report.

The current local sandbox is not yet sufficient for unattended Monday operation:
it imports saved recommendations at their frozen price but does not continuously
scan, refresh prices, or close positions.

### Stage 1 — reliable paper product (2026-08-31 to 2026-09-11)

- move secrets and continuous work out of the APK and into the backend;
- persist append-only recommendations, paper orders, fills, and marks;
- add market-session recovery, idempotency, stale-data rejection, and audit logs;
- complete fair User-versus-Agent comparison; and
- run daily reconciliation and failure alerts.

Exit gate: no missing/duplicate paper fills in five consecutive market sessions,
no use of future data, and reproducible daily P&L from the event log.

### Stage 2 — accelerated supervised readiness (go/no-go on 2026-09-04)

Use paper mode first to validate plumbing rather than to prove the entire strategy.
Use the existing historical dataset to seed the stock universe and personalized
setup priors immediately. Use live observations from 2026-08-31 through
2026-09-04 to reconcile every recommendation, simulated fill, stop/target, P&L
mark, and comparison record.
Continue paper shadowing after the real-money pilot begins so every real decision
retains a paper control.

Minimum gate before any real-money proposal:

- no missing or duplicate paper orders/fills across five consecutive sessions;
- no future-data leakage or stale-price acceptance;
- no unresolved safety or reconciliation defects;
- every recommendation and outcome auditable from point-in-time inputs; and
- deterministic account, order, exposure, and loss controls tested independently
  from the recommendation model.

This gate validates that the mechanism behaves correctly. Strategy evidence then
comes from the combination of ongoing paper shadowing and the deliberately small,
supervised real-money pilot.

### Stage 3 — supervised real-money pilot (earliest target 2026-09-07;
fallback target 2026-09-14)

This accelerated target recognizes that TraDNA already has historical trade,
symbol, outcome, and technical-context evidence and that the dedicated account
contains only $500. Friday, 2026-09-04 is the first formal go/no-go review. If all
mechanism and safety gates pass, the approval-only pilot may start Monday,
2026-09-07. If any required control or reconciliation check is incomplete, the
fallback review and target are Friday, 2026-09-11 and Monday, 2026-09-14.
Elapsed time alone cannot activate trading. Every order requires explicit user
approval.
The pilot uses only the dedicated Agentic account and runs the same recommendation
in paper simultaneously for comparison.

Initial pilot limits:

- $50 maximum per exposure-increasing order (10% of current portfolio value);
- 30% maximum total exposure ($150 at the initial account value);
- 20% maximum exposure per symbol ($100 initially);
- 2% daily loss circuit breaker ($10 initially);
- no more than three entries per day and two entries per symbol per day;
- no model authority to submit an order without explicit approval;
- a separately enforced maximum total exposure and per-symbol exposure;
- a daily loss circuit breaker and cumulative pilot drawdown stop;
- a maximum number of entries/add-ons per symbol and per day;
- equities only until a separate options policy is validated;
- stale-data, spread/liquidity, duplicate-order, and market-session checks; and
- one-action kill switch that blocks new exposure while preserving safe exits.

Exit gate: at least 20 supervised decisions with perfect audit/reconciliation,
no risk-gate bypass, and results measured against both the parallel paper ledger
and the user's actual trading over matching periods.

### Two-week launch checklist

**Week 1 — live shadow collection (2026-08-31 to 2026-09-04)**

- continuously observe the agreed stock universe during market hours;
- freeze and retain every recommendation, including no-trade decisions;
- simulate entries, add-ons, stops, targets, and exits with costs/slippage;
- mark the $5,000 sandbox to market and produce a daily scoreboard;
- ingest the user's Robinhood fills read-only for matching-period comparison; and
- record outages, stale inputs, missing bars, and rejected simulated orders.

**Week 2 — supervised pilot or continued hardening (2026-09-07 to 2026-09-11)**

- reconcile every paper event and reproduce daily P&L from the event log;
- test restart recovery, duplicate submission prevention, and stale-data failure;
- finalize total exposure, per-symbol exposure, daily loss, drawdown, frequency,
  liquidity/spread, session, order-type, and overnight-hold policies;
- verify that the user approval artifact exactly matches the submitted proposal;
- demonstrate the kill switch; and
- if the 2026-09-04 gate passed, begin the supervised pilot while continuing the
  paper twin and daily reconciliation;
- otherwise continue hardening and issue the fallback readiness report on Friday,
  2026-09-11.

**Conditional launch — Monday, 2026-09-07 or fallback Monday, 2026-09-14**

- dedicated Agentic account only;
- explicit approval for every order and add-on;
- simultaneous paper twin for every approved or rejected proposal;
- no options, unattended order submission, or expansion of the symbol universe;
- automatic stop on any audit, reconciliation, market-data, broker, or risk-policy
  anomaly; and
- daily review before the next session is enabled.

### Stage 4 — narrowly limited automation (earliest 6-12 weeks,
October-November 2026)

Limited automation may be considered only after sustained paper and supervised
evidence. Initial scope should be one strategy family, liquid equities, market
hours, small notional exposure, and automatic shutdown on data, broker, risk, or
drawdown anomalies. Options and broad autonomous scanning remain later stages.

### Stage 5 — broader autonomous agent (3-6+ months)

Expansion depends on evidence, not elapsed time. New strategies, instruments,
position sizes, or autonomy levels each require their own paper and supervised
validation. There is no responsible date on which the agent is guaranteed to
outperform the user.

## Required controls before real orders

The 10% per-entry rule is necessary but insufficient. Before Stage 3, policy must
also define:

- maximum total exposure and maximum exposure per symbol;
- maximum number of entries or add-ons per symbol and per day;
- daily and rolling drawdown/loss limits;
- minimum liquidity and maximum spread/slippage;
- allowed symbols, asset classes, sessions, and order types;
- stop-loss/exit requirements and rules for overnight holdings;
- stale/missing market-data rejection;
- duplicate-order and retry idempotency;
- explicit approval expiry; and
- an immediate kill switch that prevents new orders while allowing safe exits.

No model score can override these controls. Promotion between stages requires an
explicit user decision and a versioned policy change.
