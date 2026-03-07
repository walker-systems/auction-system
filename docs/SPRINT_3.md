# Sprint 3 — Plan (Auction System)

## Goal
Ship a production-minded proxy bidding engine with deterministic rollback, real-time SSE updates, and evidence (tests + metrics + docs).

## Working rules
- One ticket per branch: sprint3/<TICKET-ID>-<slug>
- Squash merge each ticket into main (via PR)
- Keep diffs minimal; avoid refactors

## Execution order (recommended)
1) DATA-101 Active Auction Index (Replace KEYS scan)
2) RT-201 Per-Auction Pub/Sub Channels
3) RT-202 SSE Stream Endpoint
4) RT-203 SSE Coalescing (~100ms)
5) PB-302 Bid Increment Table
6) PB-301 Max Bid Endpoint
7) PB-303 Lua Proxy Bid Engine (HASH + ZSET, atomic)
8) PB-304 Fraud Rollback via ZSET recompute
9) OBS-401 Actuator + Micrometer
10) OBS-402 Custom Counters
11) OBS-403 Live Stats (logs or /stats)
12) QA-501 TESTING.md + docs/evidence/
13) QA-502 Integration Tests (proxy bidding + rollback + SSE smoke)
14) DOC-602 ARCHITECTURE + DESIGN_DECISIONS refresh
15) DOC-601 README refresh

## Tickets (definitions of done)

### DATA-101 — Active Auction Index (P0) — Est 1.0h
- Remove Redis KEYS usage for listing auctions
- Maintain SET active_auctions
- findAll uses SMEMBERS + fetch per id

### RT-201 — Per-auction Pub/Sub (P0) — Est 0.5h
- Replace global channel with auction:updates:<auctionId>
- Ensure publish + subscribe both updated

### RT-202 — SSE Stream Endpoint (P0) — Est 1.0h
- Add endpoint /api/auctions/{id}/stream producing SSE
- Streams updates for that auction id

### RT-203 — SSE Coalescing (P1) — Est 0.5h
- Coalesce stream (e.g., sample latest every ~100ms)

### PB-302 — Increment Table (P1) — Est 0.5h
- Implement tiered increment function

### PB-301 — Max Bid Endpoint (P1) — Est 1.0h
- POST /api/auctions/{id}/max-bids
- Validates request and calls Lua engine

### PB-303 — Atomic Proxy Bidding Lua Engine (P1) — Est 4.0h
- Uses HASH state + ZSET max bids
- Enforces increase-only replaceable max bids
- Computes top2, visible price, updates state, increments version
- Returns updated state for response + publish update

### PB-304 — Deterministic Rollback via ZSET recompute (P1) — Est 1.0h
- ZREM fraudulent user from max_bids
- Recompute leader + visible price deterministically
- Update state + publish update

### OBS-401/402/403 — Observability (P1) — Est 2.5h total
- Add actuator endpoints
- Add counters: bids total, collisions, rollbacks
- Provide live stats via periodic logs or /stats endpoint

### QA-501/502 — Testing & Evidence (P0/P1) — Est 3.5h total
- docs/TESTING.md + docs/evidence folder
- Integration tests for Lua proxy bidding + rollback
- SSE smoke test

### DOC-601/602 — Docs refresh (P0) — Est 2.5h total
- Update architecture + decisions docs to match implementation
- README includes demo steps and evidence links

## Evidence locations
- docs/TESTING.md
- docs/evidence/
    - tests-pass.png
    - sse-sample.txt
    - metrics-sample.txt or png
