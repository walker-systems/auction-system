# ⚡ Bidding Engine

[![Java 25](https://img.shields.io/badge/Java-25-orange)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot 4](https://img.shields.io/badge/Spring_Boot-4.0-green)](https://spring.io/projects/spring-boot)
[![Redis Reactive](https://img.shields.io/badge/Redis-Reactive-red)](https://redis.io/)

**A High-Frequency, Event-Driven Auction System.**

The Bidding Engine is a reactive microservice designed to handle concurrent bids with sub-millisecond latency. It leverages **Redis Streams** for event propagation and **Optimistic Locking (CAS)** to ensure data integrity during high-traffic auction closings.

## 🚀 Key Features
* **Reactive Core:** Built on Spring WebFlux (Netty) for non-blocking I/O.
* **Race Condition Handling:** Uses Redis `WATCH/MULTI/EXEC` patterns to prevent double-spending.
* **AI Integration:** Emits real-time events for Price Prediction models (via MCP).
* **Vector Search:** Uses Redis Search to find similar auctions by semantic embedding.

## 🛠️ Tech Stack
| Component | Technology                                  |
| :--- |:--------------------------------------------|
| **Language** | Java 25 (Record Patterns, Reactive Streams) |
| **Framework** | Spring Boot 4.0 (WebFlux)                   |
| **Database** | Redis Stack (JSON + Search + Streams)       |
| **Testing** | Testcontainers + JUnit 5                    |

## 🏃‍♂️ Quick Start
```bash
# 1. Start Infrastructure (Redis Stack)
docker compose up -d

# 2. Run Application
./mvnw spring-boot:run
