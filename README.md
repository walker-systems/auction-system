# ⚡ Real-Time Bidding & Fraud Detection Platform

This is a live auction platform that uses AI to catch fraudulent bids in real time. It is built with Spring Boot and Redis to handle fast, continuous data streams, instantly blocking bots without slowing down the user experience.



## 🏗️ How It Works

* **Bidding Engine:** The main storefront. Built with Spring Boot WebFlux (Java 25) and Server-Sent Events (SSE) to update the UI instantly without refreshing the page.
* **Message Broker:** Redis Pub/Sub handles the real-time communication between the different services.
* **Sentinel Service:** The security monitor. It uses Spring AI to watch the live bid stream and block suspicious bot activity.

---

## 🚀 Quick Start Guide

Follow these steps to run the platform locally on your machine.

### Prerequisites
* **Docker** (must be running in the background)
* **Java 25+**
* **OpenAI API Key** (needed for the Sentinel to analyze bids)

### 1. Get the Code
```bash
git clone https://github.com/walker-systems/auction-system.git
cd auction-system
```

### 2. Start the Database
```bash
docker compose up -d
```

### 3. Start the Bidding Engine
Open a terminal in the root folder and run:
```bash
cd bidding-engine
./mvnw spring-boot:run
```

### 4. Start the AI Sentinel
Open a **new terminal window**, set your API key, and run the service:
```bash
cd auction-system/sentinel-service
export SPRING_AI_OPENAI_API_KEY='sk-your-actual-key-here'
./mvnw spring-boot:run
```

### 5. Open the Storefront
Go to **`http://localhost:8080`** in your browser.
* Click **"Start Chaos"** to unleash the demo bots.
* Watch the AI intercept and reverse fake bids in real time.

---

## 🛑 How to Stop It

When you are done testing, you can easily clean up your environment:

1. **Stop the Apps:** Press `Ctrl + C` in both terminal windows (or simply close the tabs).
2. **Stop the Database:** Run this command in the root folder to shut down Redis safely:
   ```bash
   docker compose down
   ```

---

## 🛠️ Troubleshooting

* **Port 8080 or 8081 is in use:** If the apps fail to start, another background process might be using their ports. Find and kill the process:
  ```bash
  lsof -i :8080
  kill -9 <PID>
  ```
* **Maven Wrapper (`./mvnw`) fails:** If hidden `.mvn` folders were lost during the download, regenerate the wrapper using your system's global Maven installation:
  ```bash
  mvn wrapper:wrapper
  ```
* **App crashes immediately:** Make sure you exported the `SPRING_AI_OPENAI_API_KEY` in the exact same terminal window where you are running the Sentinel service.

---
