---
hide:
  - navigation
  - path
---

<style>
  .md-content__inner > h1:first-of-type {
    display: none;
  }
</style>

<div align="center">
  <p style="letter-spacing: 0.08em; text-transform: uppercase; color: #9ca3af; margin-bottom: 0.35rem;">
    Document
  </p>
  <h1 style="margin-top: 0;">Architecture Decisions</h1>
  <p style="max-width: 760px; margin: 0 auto 1.25rem auto; color: #b6bdc8;">
    Notes on major design choices and tradeoffs.
  </p>
</div>

---

## ADR-001: Reactive Redis for Active Auction State

### Context
Active auction state can experience sharp increases in write concurrency, especially near auction close. 
That path needs low-latency reads, writes, and atomic operations, while long-term persistence can be handled separately.

### Decision
Redis, accessed through Spring Data Redis Reactive, is used as the primary store for active auction state.

### Consequences
- **Pros:** Fast reads and writes, atomic primitives, and a natural fit for low-latency reactive workflows.
- **Cons:** Active state must fit in memory.
- **Mitigation:** Redis AOF is enabled to improve crash recovery by persisting write commands that can be replayed on restart.

---

## ADR-002: Java Records for Domain Models

### Context
Most domain types in the system are data carriers rather than behavior-heavy objects.

### Decision
Java records are used for domain models.

### Consequences
- **Pros:** Concise, immutable by default, readable.
- **Cons:** They do not align with patterns built around mutable JPA entities.
- **Rationale:** The system uses Redis rather than JPA, so that tradeoff is acceptable.

---

## ADR-003: ReactiveRedisTemplate with JSON Serialization

### Context
This system needs direct, non-blocking access to Redis data structures and operations beyond simple CRUD, including sets, sorted sets, and Lua-backed atomic updates. Older (deprecated) 
Jackson-based serializer choices are also not a good foundation for a new codebase.

### Decision
Redis access is written with `ReactiveRedisTemplate`, and serialization uses `RedisSerializer.json()`.

### Consequences
- **Pros:** Fully non-blocking access and explicit control over persistence behavior.
- **Cons:** Standard repository operations must be implemented manually.

---

## ADR-004: `Mono.defer()` for Stateful Reactive Tests

### Context
Retry logic is difficult to test when mocks always return the same state on every invocation.

### Decision
`Mono.defer()` is used in tests that need to simulate state changing between retries.

### Consequences
- **Pros:** Tests reflect the behavior of a real datastore more closely.
- **Cons:** Adds complexity.

---

## ADR-005: Separate Sentinel State from Core Auction State

### Context
The bidding engine and Sentinel currently share Redis infrastructure. Analysis workloads should not be able to interfere with the core bid path.

### Decision
Sentinel state is planned to move to separate backing infrastructure. Shared Redis should remain limited to messaging and coordination where cross-service communication is required.

### Consequences
- **Pros:** Reduces the risk that analysis activity will impact auction latency.
- **Cons:** Adds complexity.

---

## ADR-006: Feedback Loop for Fraud Decisions

### Context
Fraud decisions are only useful in the long term if mistakes can be reviewed and incorporated into future model updates.

### Decision
A feedback loop is planned in which fraud outcomes are written to an audit trail, corrected when necessary, and reused during retraining.

### Consequences
- **Pros:** Improves model quality over time.
- **Cons:** Requires an additional pipeline/interface.

---

## ADR-007: Event Sourcing for Reliable Rollbacks

### Context
Overwriting the current bid state makes fraud reversals harder because the system loses the full sequence of prior accepted actions.

### Decision
An eventual transition to event sourcing is planned. Bids, reversals, and enforcement actions would be recorded as append-only events rather than only as the current snapshot.

### Consequences
- **Pros:** Makes rollback logic more accurate and improves historical traceability.
- **Cons:** Requires a substantial architectural shift.

---

## ADR-008: Specialized ML Service for Fraud Scoring

### Context
General-purpose LLM integration is poorly suited to fast numerical fraud evaluation. The workload is structured, repetitive, and latency-sensitive.

### Decision
Fraud scoring runs through a dedicated Python `FastAPI` service backed by a specialized model such as `CatBoost`, rather than through a general LLM stack.

### Consequences
- **Pros:** Lower latency, lower cost, and a better fit for tabular inference.
- **Cons:** Introduces a polyglot architecture with Java and Python services.

---

## ADR-009: Decouple Model Development from Network Integration

### Context
Building the ML model and the Java-to-Python integration at the same time increases delivery risk.

### Decision
The service boundary is validated first with simple models and synthetic data. Once the transport path is stable, the production model can be introduced behind the same interface.

### Consequences
- **Pros:** Reduces integration risk and isolates failures earlier.
- **Cons:** Requires temporary tooling that is not part of the final product.

---

## ADR-010: Manual Dockerfile for the Python ML Service

### Context
Python ML services often depend on low-level system packages that automated container tooling does not consistently infer.

### Decision
The Python service is containerized with a hand-written Dockerfile instead of an automated buildpack flow.

### Consequences
- **Pros:** The runtime image is explicit and reproducible.
- **Cons:** Container maintenance is manual.

---

## ADR-011: Hybrid Proxy Bidding with Atomic Redis Lua Persistence

### Context
Proxy bidding and bid increments are vulnerable to race conditions when implemented as ordinary application-side read-modify-write logic.

### Decision
Proxy bidding uses a hybrid design: the Java service resolves forward-path bidding outcomes, 
while Redis Lua scripts provide atomic state persistence and rollback recomputation.

### Consequences
- **Pros:** State transitions are atomic and do not require distributed locks.
- **Cons:** Business logic is split across Java and Lua.

---

## ADR-012: Testcontainers for Redis Integration Testing

### Context
Mocking cannot fully validate Redis Lua behavior or script execution semantics.

### Decision
Integration tests run against ephemeral Redis containers using Testcontainers.

### Consequences
- **Pros:** Tests execute against a real Redis engine and real scripts.
- **Cons:** Test runs are slower and require Docker.

---

## ADR-013: Epoch Time for Cross-Boundary Comparisons

### Context
Soft-close logic extends auction end times inside Lua scripts. Passing high-level Java time objects across the Java-Lua boundary created serialization and comparison issues.

### Decision
Time values are passed as epoch seconds.

### Consequences
- **Pros:** Comparisons remain numeric and predictable across language boundaries.
- **Cons:** Requires explicit time conversion, increasing the risk of careless mistakes (with time units, for example).

---

## ADR-014: Asynchronous Fraud Detection via Redis Streams

### Context
Scoring each bid synchronously would add network latency and model overhead directly to the bid path. That cost is least acceptable at the exact moment bidding traffic is highest.

### Decision
Fraud analysis is decoupled from bid execution through Redis Streams. The Bidding Engine writes bid events to a stream, and Sentinel consumes them asynchronously for downstream scoring and enforcement.

### Consequences
- **Pros:** Bid execution remains fast, and Sentinel can scale independently of the core auction service.
- **Cons:** Fraud enforcement becomes eventually consistent. A malicious bid may be accepted briefly before later review and reversal.

---

## ADR-015: Micro-Batching for ML Inference

### Context
Submitting one HTTP request to the ML service for every individual bid creates unnecessary transport overhead and does not use the scoring service efficiently.

### Decision
Sentinel uses micro-batching with a bounded time and size window. Bid events are accumulated briefly, then submitted to the ML service as a batch.

### Consequences
- **Pros:** Reduces per-request overhead and improves inference efficiency.
- **Cons:** Adds a small delay to the fraud pipeline while the batch window fills.

---

## ADR-016: Smart Default Routing for Polyglot Development

### Context
A Java service and a Python service do not always run in the same environment during development. One may run in Docker while the other runs directly in an IDE.

### Decision
Service URLs are configured with sensible defaults and environment overrides, allowing the same codebase to run in Docker Compose or locally with minimal setup.

### Consequences
- **Pros:** Improves developer experience and reduces setup friction across environments.
- **Cons:** Developers still need to understand which effective route is active at runtime.

---

<p><strong>Created by <a href="https://walker-systems.github.io/">Justin Walker</a></strong></p>
