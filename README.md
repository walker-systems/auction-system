<div align="center">
  <img src="docs/exports/BIDSTREAM_logo_no_bg_001.png" alt="Bidstream Logo" width="600"/>
</div>

<div align="center">
  <video src="docs/exports/Hero_MP4_4_log_open_close.mp4" autoplay loop muted playsinline width="100%"></video>
</div>

# BIDSTREAM.dev

Bidstream is a reactive, high-frequency trading sandbox designed to solve complex distributed systems challenges. It demonstrates how to handle massive traffic spikes, mitigate race conditions via atomic state management, and run live machine learning fraud detection—all without degrading latency or crashing the main event loop.

[![Bidstream Architecture Walkthrough](https://cdn.loom.com/sessions/thumbnails/0cd99f26a7a44051b5c202e6cfc240a9-with-play.gif)](https://www.loom.com/share/0cd99f26a7a44051b5c202e6cfc240a9)
*(Click above for a 3-minute technical walkthrough of the architecture and code).*

---

## 🏗️ System Architecture

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
