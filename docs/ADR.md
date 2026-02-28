# Architecture Decision Records (ADR)

## ADR-001: Use of Reactive Redis over RDBMS

### Context
We expect burst traffic (10k+ TPS) during the final seconds of an auction ("sniping"). Traditional RDBMS (Postgres) blocking I/O would require massive connection pools and vertical scaling.

### Decision
We will use **Spring Data Redis Reactive** as the primary source of truth for active auctions.

### Consequences
* **Pros:** Ultra-low latency (<5ms). Native support for Atomic operations (Lua).
* **Cons:** Dataset must fit in RAM.
* **Mitigation:** We enable Redis AOF (Append Only File) for durability.

---

## ADR-002: Java 25 & Records

### Context
Domain objects in bidding systems are data-heavy and behavior-light.

### Decision
We use **Java 25 Records** for all domain models.

### Consequences
* **Pros:** Immutability by default (thread-safe). Compact constructors for validation.
* **Cons:** Cannot use JPA (Hibernate) lazy loading (not an issue since we use Redis).

---

## ADR-003: Adoption of ReactiveRedisTemplate & JSON Serialization

### Context
I initially attempted to use `ReactiveCrudRepository` (the standard Spring Data interface). However, Spring Data Redis does not support reactive repositories for Redis (only blocking). This caused `InvalidDataAccessApiUsageException` at startup.

Additionally, standard JSON serializers (`Jackson2JsonRedisSerializer`) are deprecated in Spring Data Redis 4.0 in favor of `RedisSerializer` API.

### Decision
1.  **Manual Repositories (no Interface):** Manually implement the Repository pattern using `ReactiveRedisTemplate`. This provides fine-grained control over serialization and atomic operations (CAS).
2.  **Serialization:** We use `RedisSerializer.json()` instead of the deprecated classes.

### Consequences
* **Positive:** Full non-blocking I/O support. No compilation warnings.
* **Negative:** Must write all code for `save`, `find`, and `delete` methods (no auto-generated queries).

---

## ADR-004: Using Mono.defer() for Stateful Mocking in Reactive Retry Tests

### Context
A unit test was developed to simulate a race condition starting with a baseline state (State A: price = $100, version = 1).
A new bidder (User C) attempts to bid $115, operating under the assumption that State A is the current state.

The Lua script inside `AuctionRepository` (executed via Redis Template) acts as an optimistic lock.
It only permits an update if the current database version matches the proposed version minus one.
The proposed version is calculated by grabbing the database version at the moment the bid is initiated and adding one to it.

The test simulates a scenario where another bidder (User B) successfully submits a bid during the window between
User C accessing the database version (State A, version 1) to create a proposal (version 2), and the moment User C actually attempts
to save this new bid.

This creates a version mismatch. Because User B already updated the database version to 2, the database version
no longer equals User C's proposed version minus one (2 != 2 - 1). Consequently, User C's save attempt is rejected by the Lua script.
The .retryWhen operator then activates, causing the system to execute findById again,
retrieve the correct current version (2), create a valid new proposal (version 3), and successfully submit it.

Attempting to mimic this state change with standard Mockito chaining (`.thenReturn(Mono.just(stateA)).thenReturn(Mono.just(stateB))`)
results in an `AssertionFailedError`.

This failure occurs because Project Reactor constructs the reactive pipeline at assembly time.
The root method, `auctionRepository.findById()`, is invoked only once during construction. When `.retryWhen()` triggers,
it does not rebuild the pipeline; it merely resubscribes to the original source. Because the mocked source was a `Mono.just()` created at assembly time,
it repeatedly emits the outdated stateA on every resubscription, failing to accurately simulate a dynamic database.

### Decision
When testing reactive streams where the source data must change across multiple subscriptions
(e.g., simulating database updates during a retry loop), it is required to use `Mono.defer()` in conjunction with an `AtomicInteger` to track the number of subscriptions.

`Mono.defer()` ensures the mock's logic is evaluated dynamically at subscription time rather than statically at assembly time.
This allows the mocked response to change across retries, accurately reflecting the behavior of a real database.

### Consequences
* **Positive:** Unit tests accurately reflect the "cold" nature of real database queries, successfully validating that the retry loop
  fetches fresh data and applies business logic correctly upon recovering from a collision.
* **Negative:** N/A

---

## ADR-005: Decoupling Sentinel from Bidding Engine's Redis

### Context
Currently, the Sentinel Service and the core Bidding Engine share the same Redis instance for both Pub/Sub messaging and state management. As the system scales, the security evaluation workload could bottleneck the primary transaction database.

### Decision
*(Planned)* We will decouple the Sentinel Service's state management from the Bidding Engine's Redis instance. Redis will remain the shared message broker (Pub/Sub) for real-time events, but Sentinel will maintain its own isolated datastore for tracking user reputation and historical analysis.

### Consequences
* **Positive:** Prevents noisy-neighbor issues where heavy AI/Sentinel queries degrade core bidding performance. Enforces strict microservice boundaries.
* **Negative:** Increases infrastructure footprint and deployment complexity.

---

## ADR-006: Providing the Sentinel a Feedback Loop

### Context
The current Sentinel AI evaluates bids in isolation without a mechanism to learn from false positives (e.g., mistakenly flagging an aggressive human bidder as a bot).

### Decision
*(Planned)* Implement an asynchronous feedback loop. Store AI decisions in a persistent audit log, allowing human administrators (or automated validation rules) to flag incorrect rollbacks. This data will be fed back into the model to dynamically adjust its scoring weights.

### Consequences
* **Positive:** Model accuracy will improve over time; false positive rates will decrease.
* **Negative:** Requires building an admin review UI and an asynchronous data pipeline to process the feedback.

---

## ADR-007: Upgrading to Event Sourcing to Save Previous Bidder

### Context
When the Sentinel triggers a rollback on a fraudulent bid, we currently overwrite the state. This loses the context of who the *previous* legitimate bidder was, making it difficult to accurately restore the auction to its exact prior state.

### Decision
*(Planned)* Transition the core architecture from a purely state-based CRUD model to **Event Sourcing**. Instead of storing just the "current highest bid," we will append every bid, rejection, and rollback as an immutable event in a ledger. The current state will be a projection derived from this event stream.

### Consequences
* **Positive:** Flawless state reconstruction during rollbacks. Provides the exact time-series data required for training future Machine Learning models.
* **Negative:** Significant architectural rewrite. Event eventual consistency models will need to be carefully managed to maintain the sub-millisecond UI requirements.

---

## ADR-008: Deepening AI Functionality

### Context
Our Sprint 1 implementation utilizes generalized LLM endpoints via Spring AI to detect anomalous behavior. While effective for a baseline, it is not optimized for high-throughput, sub-millisecond tabular data evaluation.

### Decision
*(Planned)* Deepen the AI functionality by transitioning from a generalized LLM to a specialized Machine Learning pipeline. We will utilize `CatBoost` for rapid classification of tabular telemetry (reaction times, bid deltas, IP reputation) and deploy it via a high-performance Python `FastAPI` sidecar.

### Consequences
* **Positive:** Drastically reduced latency and lower operational costs compared to external LLM API calls.
* **Negative:** Introduces polyglot architecture (Java + Python) and requires dedicated MLOps infrastructure for model training and deployment.
