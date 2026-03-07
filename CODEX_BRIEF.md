# CODEX_BRIEF — Auction System (Monorepo)

## One-liner
A real-time auction system with a Java bidding engine, a Java sentinel service, and a Python ML scoring service.

## Repo layout
- /bidding-engine        (Spring Boot, Reactive Redis, UI demo)
- /sentinel-service      (Spring Boot services: rate limiting, stream monitor, etc.)
- /sentinel-ml           (FastAPI + CatBoost model inference)
- /docs                  (architecture, sprint plans, evidence)

## Current sprint
See: docs/SPRINT_3.md  
Rule: implement ONE ticket per branch. Keep diffs minimal. No drive-by refactors.

## System behavior (today)
- Bids enter via bidding-engine (AuctionController → AuctionService → AuctionRepository)
- Auction state is stored in Redis
- Updates are published via Redis pub/sub and streamed to clients via SSE
- Fraud detection can trigger rollback flows

## Sprint 3 target architecture (this week)
We are implementing Proxy Bidding and production-minded Redis patterns.

### Redis key conventions (Sprint 3)
- active auction index:
    - SET: active_auctions  (members are auctionIds)

- per-auction state:
    - HASH: auction:<auctionId>:state
        - fields: active, endsAtEpochMs, version, currentPrice, leader, updatedAtEpochMs (optional)

- proxy bidding:
    - ZSET: auction:<auctionId>:max_bids
        - member = bidderId
        - score  = maxBid

- pub/sub updates:
    - channel: auction:updates:<auctionId>

## API contracts (Sprint 3)
### Place / update a max bid (proxy bid)
POST /api/auctions/{auctionId}/max-bids
Body:
{
"bidderId": "user123",
"maxBid": 200.00,
"telemetry": { "ipAddress": "...", "userAgent": "...", "reactionTimeMs": 123 },
"requestId": "optional"
}

Rules:
- max bids are replaceable but must INCREASE (no decreases)
- visible price = min(highestMax, secondHighestMax + increment(currentPrice))

## Definition of done for a ticket
- Implements requirements in docs/SPRINT_3.md
- Updates tests if applicable
- Updates docs/evidence if applicable
- Provides: files changed + how to test

## Safety rails for Codex
- Do NOT refactor unrelated files
- Keep scope to the ticket only
- If unsure, ask clarifying questions before editing
- Prefer small, reviewable commits
