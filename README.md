<div align="center">
  <img src="docs/exports/BIDSTREAM_logo_no_bg_001.png" alt="Bidstream Logo" width="600" />
</div>

<div align="center">
  <img src="https://github.com/user-attachments/assets/13a9dbec-5468-4c49-8461-fdde9f90fa28" alt="Bidstream Demo" width="100%">
</div>

Bidstream is a reactive, high-frequency trading platform designed to solve distributed systems challenges. It demonstrates how to handle massive traffic spikes, mitigate race conditions via atomic state management, display continuously updating visuals representing real-time data, and run live machine learning fraud detection without degrading performance.

<br>

<div align="center">
  <a href="https://www.loom.com/share/0cd99f26a7a44051b5c202e6cfc240a9" target="_blank">
    <img src="https://cdn.loom.com/sessions/thumbnails/0cd99f26a7a44051b5c202e6cfc240a9-da1fb20a159afb4b-full-play.gif" alt="Building a High-Frequency Trading Terminal" width="800">
  </a>
  <br>
  <p><em>(Click above for a 3-minute technical walkthrough of the architecture and code.)</em></p>
</div>
---

## Architecture

The ecosystem relies on an event-driven, decoupled architecture.

```mermaid
graph TD
    %% Styling Definitions
    classDef client fill:#1f2937,stroke:#4ade80,stroke-width:2px,color:#f3f4f6
    classDef engine fill:#1e40af,stroke:#60a5fa,stroke-width:2px,color:#eff6ff
    classDef redis fill:#991b1b,stroke:#f87171,stroke-width:2px,color:#fef2f2
    classDef python fill:#065f46,stroke:#34d399,stroke-width:2px,color:#ecfdf5

    %% Nodes
    Client["💻 Client Browser<br/>(React / Vanilla JS)"]:::client
    Engine["⚙️ Bidding Engine<br/>(Java Spring WebFlux)"]:::engine
    Redis[("🗄️ Redis Cache<br/>(State / Lua / PubSub)")]:::redis
    Sentinel["🧠 Sentinel ML<br/>(Python CatBoost)"]:::python

    %% Connections
    Client -- "1. HTTP POST (Execute Bid)" --> Engine
    Engine -- "2. Evaluate Atomic Lua Script" --> Redis
    Engine -. "3. Async Micro-batch (WebClient)" .-> Sentinel
    Redis -- "4. Pub/Sub (State Change Notification)" --> Engine
    Engine -- "5. Server-Sent Events (Live Logs & Prices)" --> Client
    Sentinel -. "6. Fraud Alert (Redis Stream)" .-> Redis
```

## Key Features

### 1. DDoS Mitigation at the Cache Layer

To protect the main Java JVM from wasting CPU cycles on dead or malicious traffic, 
the perimeter is secured by a custom token bucket rate limiter.

<div align="center">
  <img src="https://github.com/user-attachments/assets/a52332db-6084-45f5-b7ed-c66bea9458e5" alt="Rate Limiter Demo" width="100%">
</div>

Every incoming request is evaluated in-memory within Redis. 
Malicious IPs attempting to flood the application are dropped instantly at the cache layer, 
maintaining stability for legitimate users.

### 2. Atomic Transactions and Race Condition Prevention

In a highly concurrent system, two users bidding at the exact same millisecond can cause a double-spend or 
"time of check to time of use" (TOCTOU) vulnerability.

<div align="center">
  <img src="https://github.com/user-attachments/assets/26fd5bb3-7585-4122-9561-84875e7682f3" alt="Bid Collision Demo" width="100%">
</div>

To solve this, the Java server does not evaluate the math. 
Instead, it delegates the bid to an atomic Redis Lua script. This guarantees strict consistency and optimistic locking.

```mermaid
sequenceDiagram
    participant C as Client (User/Bot)
    participant J as Java Service
    participant R as Redis (Lua Script)

    C->>J: POST /bid ($15.00, Expected Version: 4)
    J->>R: EVAL update_auction.lua
    activate R
    note over R: Transaction Locked
    R->>R: 1. Check if Expired (TIME)
    R->>R: 2. Verify Current Version == 4
    R->>R: 3. Update Price to $15.00
    R->>R: 4. Increment Version to 5
    R-->>J: Return Success (New Version: 5)
    deactivate R
    J-->>C: 200 OK (Bid Accepted)
```

### 3. Non-Blocking I/O and Real-Time Data Streaming

Built entirely on Spring WebFlux, the application uses a small pool of non-blocking event loop threads. 
When Redis commits a state change, it publishes a notification that Java pushes directly to the browser via 
Server-Sent Events (SSE).

<div align="center">
  <img src="https://github.com/user-attachments/assets/256a9771-e4ee-46ce-ad45-688fcb814d25" alt="Log Waterfall Demo" width="100%">
</div>

To prevent the browser's rendering engine from choking on hundreds of log lines per second, the frontend leverages a detached `DocumentFragment` to batch DOM mutations in memory before repainting the screen, keeping the framerate smooth.

### 4. Decoupled AI Fraud Detection

Running heavy machine learning models on the main server thread destroys P99 latency. 
Therefore, fraud detection is entirely decoupled.

A separate Sentinel microservice collects live bids in micro-batches and sends them to a Python CatBoost model. 
If a bot is detected, an alert is pushed to a Redis Stream. 
The Java engine reads the stream, reverts the bad bid to the correct price, 
and bans the user asynchronously without interrupting the flow of ongoing auctions.

## Quick Start

The fastest way to run the entire distributed system locally is via Docker Compose.

```bash
# 1. Clone the repository
git clone https://github.com/walker-systems/auction-system.git
cd auction-system

# 2. Start the infrastructure
docker compose up -d

# 3. Access the dashboard
# Open your browser and navigate to: http://localhost:8080
```

## Local Development Setup

If you wish to run the microservices independently for development:

### 1. Start Redis

```bash
docker run -d -p 6379:6379 --name bidstream-redis redis:7.2-alpine
```

### 2. Start the Machine Learning API (Python)

```bash
cd sentinel-ml
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --port 8000
```

### 3. Start the Java Services

```bash
# Terminal 1: Bidding Engine
cd bidding-engine
./mvnw spring-boot:run

# Terminal 2: Sentinel Service
cd sentinel-service
./mvnw spring-boot:run
```

## Documentation

The backend services auto-generate OpenAPI (Swagger) documentation. Once the application is running, you can explore the endpoints and schema definitions interactively:

- [Bidding Engine API](http://localhost:8080/swagger-ui.html)
- [Sentinel Service API](http://localhost:8081/swagger-ui.html)
- [Sentinel ML API](http://localhost:8000/docs)

<div align="center">
<p><strong>Created by <a href="https://walker-systems.github.io/">Justin Walker</a></strong></p>

  <img src="https://img.shields.io/badge/Spring_Boot-green?style=for-the-badge" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/WebFlux-blue?style=for-the-badge" alt="WebFlux" />
  <img src="https://img.shields.io/badge/Redis-red?style=for-the-badge" alt="Redis" />
  <img src="https://img.shields.io/badge/Python-yellow?style=for-the-badge" alt="Python" />
  <img src="https://img.shields.io/badge/Docker-blue?style=for-the-badge" alt="Docker" />
</div>
