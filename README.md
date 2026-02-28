# 🛡️ Sentinel Service (AI Security Monitor)

*Note: This is a microservice within the larger **[Real-Time Bidding & Fraud Detection Platform](https://github.com/walker-systems/auction-system)**. For the full system architecture and Docker orchestration, please visit the main repository.*

This is the background security engine of the auction platform. It silently monitors the live stream of bids and uses AI to identify and block fraudulent bot activity in real time.

## 🧠 Component Context

Instead of hardcoding hundreds of rules to catch bad actors, the Sentinel Service uses **Spring AI**. 

It subscribes to the Redis message broker and listens for every new bid. It then evaluates the context of the bid (the user's behavior, timing, and persona) to determine if it is a legitimate human or an automated bot. If it detects a bot, it instantly fires a rollback event back into Redis to reverse the transaction on the live storefront.

### ⚡ Concurrency Control (Lua Scripting)
In a live auction, multiple users (or a swarm of bots) might try to bid on the exact same item at the exact same millisecond. To prevent race conditions—where a delayed bid might accidentally overwrite a higher one due to network latency—this system manages state using **Atomic Lua Scripts** inside Redis. 

Instead of checking the current price and *then* saving the new bid (which leaves a split-second gap for data corruption), the custom Lua script executes both actions in a single, uninterruptible motion. This guarantees absolute data consistency even under extreme traffic.

**Key Technologies:** Java 25, Spring Boot WebFlux, Spring AI, Redis Pub/Sub, Redis Lua Scripting.

---

## 🚀 How to Run (Standalone)

*To run the full platform including the Storefront and Database, use the `docker-compose.yml` in the [main repository](https://github.com/walker-systems/auction-system).*

If you want to run just the Sentinel locally (requires a running Redis instance on port 6379):

```bash
# 1. Navigate to this directory
cd sentinel-service

# 2. Export your OpenAI API key (Required for the AI to analyze bids)
export SPRING_AI_OPENAI_API_KEY='sk-your-actual-key-here'

# 3. Start the application
./mvnw spring-boot:run
```

---

## 📬 Let's Connect

**Justin Walker**
* 📧 **Email:** [justinwalker.contact@gmail.com](mailto:justinwalker.contact@gmail.com)
* 💼 **LinkedIn:** [Justin Walker](https://www.linkedin.com/in/justin-walker-0403923b1/)
* 🌐 **Portfolio:** [justin-castillo.github.io](https://justin-castillo.github.io/)
