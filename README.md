# BidStream: High-Frequency Trading Engine & ML Sentinel

![Java](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7.2-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.10-3776AB?style=for-the-badge&logo=python&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-0.109-009688?style=for-the-badge&logo=fastapi&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)

> **[ PLACEHOLDER: INSERT YOUR 15-SECOND LOOM GIF HERE SHOWING THE DEMO RUNNING ]**

BidStream is a reactive, horizontally scalable high-frequency trading simulation built to handle massive concurrent transaction volume. It features a distributed microservice architecture, strict P99 latency telemetry, and a dedicated Machine Learning pipeline to dynamically identify and ban malicious algorithmic trading bots in real-time.

## System Architecture

The ecosystem consists of four containerized nodes operating on a shared Docker bridge network:

1. **The Core Execution Engine (Spring WebFlux)**
    * Built on non-blocking Netty to handle massive concurrent SSE (Server-Sent Events) connections.
    * Utilizes **Optimistic Locking** and atomic Redis Lua scripts to completely eliminate race conditions and "Lost Updates" during high-density block trades.
2. **The Primary Datastore (Redis)**
    * Acts as the single source of truth for all financial instruments, execution states, and Pub/Sub event broadcasting.
3. **The ML Inference API (Python / FastAPI)**
    * A highly optimized `CatBoostClassifier` trained on synthetic telemetry data to evaluate user behavior (Reaction Time, TPS, IP Address).
4. **The Sentinel Bridge (Spring Boot)**
    * Consumes the global Redis firehose asynchronously, evaluates bids against the ML model, and injects fraudulent actors into a distributed `banned_users` Set while broadcasting transaction rollbacks.

## Key Engineering Highlights

* **Layout-Shift-Free Telemetry UI:** The frontend is decoupled into a dedicated render loop to completely eliminate DOM repaints and Core Web Vitals layout shift, even when updating 100+ active rows per second.
* **Deterministic Latency:** System load tests prove that P99 Latency remains flat (< 50ms) even as Application Throughput (TPS) scales to simulate thousands of concurrent trading bots, proving the application layer is effectively isolated from database bottlenecks.
* **Resilient Graceful Degradation:** External API calls to the ML model are wrapped in non-blocking WebClient timeouts. If the AI goes offline, the trading engine gracefully degrades to accepting trades rather than cascading into failure.

## One-Click Boot (Local Deployment)

To run the entire distributed cluster locally, simply clone the repository and utilize Docker Compose.

```bash
# 1. Clone the repository
git clone [https://github.com/walker-systems/auction-system.git](https://github.com/walker-systems/auction-system.git)
cd auction-system

# 2. Boot the cluster (Java 25, Python 3.10, Redis)
docker compose up --build
