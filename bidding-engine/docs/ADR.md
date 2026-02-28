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
A unit test was developed to simulate a race condition starting with a baseline state (State A: price = \$100, version = 1). 
A new bidder (User C) attempts to bid \$115, operating under the assumption that State A is the current state.

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
