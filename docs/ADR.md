# Architecture Decision Records (ADR)

## ADR-001: Use of Reactive Redis over RDBMS

### Context
Auctions experience sudden traffic spikes (10k+ TPS) in the final seconds. Standard relational databases (like Postgres) struggle to handle this concurrency without heavy scaling.

### Decision
**Spring Data Redis Reactive** will be used as the primary database for active auctions.

### Consequences
* **Pros:** Very fast read/writes (<5ms) and built-in support for atomic operations.
* **Cons:** All active auction data must fit in server RAM.
* **Mitigation:** Redis AOF (Append Only File) is enabled to back up data to the disk.

---

## ADR-002: Java 25 & Records

### Context
Domain objects in this bidding system mostly hold data, rather than complex business logic.

### Decision
**Java 25 Records** will be used for all domain models.

### Consequences
* **Pros:** Immutable and thread-safe by default.
* **Cons:** Cannot use standard JPA (Hibernate) lazy loading. This is acceptable since Redis is used instead of JPA.

---

## ADR-003: Adoption of ReactiveRedisTemplate & JSON Serialization

### Context
Standard Spring Data reactive repositories do not support Redis. Additionally, the default JSON serializer is deprecated.

### Decision
1. Database access methods (Repository pattern) are manually written using `ReactiveRedisTemplate`.
2. The newer `RedisSerializer.json()` is used for serialization.

### Consequences
* **Pros:** Fully non-blocking I/O.
* **Cons:** Requires writing custom code for standard database actions (`save`, `find`, `delete`).

---

## ADR-004: Using Mono.defer() for Reactive Database Mocking

### Context
When unit testing code that retries a failed database update, standard mock objects return the exact same data on every retry. This fails to simulate a real database where the data might change between retries.

### Decision
`Mono.defer()` is utilized when mocking reactive streams that need to simulate changing states. 

### Consequences
* **Pros:** Tests correctly simulate the database changing during a retry loop.
* **Cons:** N/A

---

## ADR-005: Decoupling Sentinel from Bidding Engine's Redis

### Context
Currently, the AI (Sentinel) and the main bidding system share one Redis database. Heavy AI data processing could slow down the core bidding system.

### Decision
*(Planned)* The Sentinel's state management will be moved to its own separate database. Redis will only be shared for real-time messaging (Pub/Sub).

### Consequences
* **Pros:** Prevents the AI from slowing down user bids.
* **Cons:** Requires setting up and managing more database infrastructure.

---

## ADR-006: Providing the Sentinel a Feedback Loop

### Context
The AI currently evaluates bids without any way to learn from its mistakes (e.g., flagging a real user as a bot).

### Decision
*(Planned)* AI decisions will be saved to an audit log. Admins will be allowed to correct mistakes, and this corrected data will be used to retrain the model.

### Consequences
* **Pros:** The AI gets more accurate over time.
* **Cons:** Requires building an admin interface and a data feedback pipeline.

---

## ADR-007: Upgrading to Event Sourcing for Rollbacks

### Context
When a fraudulent bid is canceled, the database is currently overwritten. This deletes the record of who the previous valid bidder was.

### Decision
*(Planned)* The architecture will transition to Event Sourcing. Instead of just saving the "current bid," every action (bids, rejections) will be saved as an append-only log of events.

### Consequences
* **Pros:** Makes it easy and accurate to undo fraudulent bids. Provides excellent historical data for training the AI.
* **Cons:** Requires a major architectural rewrite.

---

## ADR-008: Deepening AI Functionality

### Context
Using a standard LLM via Spring AI is too slow and expensive for evaluating high-speed numerical data (like reaction times and bid amounts).

### Decision
*(Planned)* The general LLM will be replaced with a specialized Machine Learning model (`CatBoost`) running on a Python `FastAPI` server.

### Consequences
* **Pros:** Much faster processing and lower costs.
* **Cons:** Adds Python to the tech stack, necessitating the management of a multi-language (polyglot) architecture.

---

## ADR-009: Decoupled ML Pipeline via Synthetic Data

### Context
Building a complex ML model and connecting a new Python server to the existing Java backend at the same time is risky. It can cause massive integration bugs at the end of the sprint.

### Decision
The network connection will be built and tested first using temporary, fake data and a simple AI model. Once the Java and Python servers communicate perfectly, the real, complex ML model will be swapped in.

### Consequences
* **Pros:** Proves the network plumbing works early. Keeps the Java and Python code strictly decoupled.
* **Cons:** Requires writing throwaway scripts to generate the fake data.

---

## ADR-010: Containerization Strategy for Python ML Microservice

### Context
Java applications often use automated Buildpacks (like `mvn spring-boot:build-image`) to create containers without manual setup. However, Python Machine Learning libraries (like CatBoost) require specific operating system dependencies. Automated tools often fail to guess and install these low-level requirements correctly.

### Decision
A standard `Dockerfile` will be used to containerize the Python ML microservice instead of automated Buildpacks.

### Consequences
* **Pros:** Guarantees the production container exactly matches the local training environment.
* **Cons:** Requires manual file maintenance and differs from the Java project's container strategy.