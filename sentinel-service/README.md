# Sentinel Service ⏲

> A high-performance, distributed rate limiting microservice built with Spring Boot WebFlux and Redis.

![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.2-green?style=flat-square)
![Redis](https://img.shields.io/badge/Redis-Distributed-red?style=flat-square)
![Coverage](https://img.shields.io/badge/Coverage-95%25-brightgreen?style=flat-square)
![Docker](https://img.shields.io/badge/Docker-Ready-blue?style=flat-square)

## ☰ Overview

Sentinel is designed to protect downstream services from being overwhelmed with traffic. Originally designed to accompany
the "Bidding Engine". 

**Key Features**
* **Distributed State:** Limits/usage are shared across multiple instances via Redis. 
* **Atomic Operations:** Lua scripts prevent race conditions.
* **Reactive Stack:** Built with WebFlux to leverage non-blocking performance. 
* **Ready to Run:** Fully dockerized. 

## 🐳 Quick Start

1. Create `compose.yaml`:

```yaml
services:
  sentinel:
    image: justinwalkerhub/sentinel-service:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATA_REDIS_HOST=redis
    depends_on:
      - redis

  redis:
    image: redis:alpine
    ports:
      - "6379:6379"
```

2. Run it:

```docker compose up```

## 👩🏻‍💻 Development Setup

**Prerequisites**
* JDK 25+
* Docker (for Redis)

1. Start Redis

```docker run -d -p 6379:6379 --name sentinel-redis redis:alpine```

2. Run the App 

```./mvnw spring-boot:run```

## 🔌 API Usage

**Check Rate Limit**

**GET** ```/check```

Returns ```200 OK``` if allowed, or ```429 Too Many Requests``` if blocked. 

**Parameters**
* ```capacity``` (int): Max Tokens in the bucket. 
* ```rate``` (int): Refill rate (tokens per second).
* ```cost``` (int): Cost of this request. 
* **Header** ```X-User-ID```: Unique identifier for the user. 

**Example Request:**
```bash
curl -v "http://localhost:8080/check?capacity=10&rate=1" \ 
-H "X-User-ID: test_user"
```

**Response (JSON):**
```json
{
    "allowed": true
}
```

## 📘 Documentation 
Interactive API documentation (Swagger UI) is available when the app is running:

[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

## 📐 Architecture

**The Token Bucket Algorithm**
1. **State:** Each user (X-User-ID) has a "Bucket" stored on Redis. 
2. **Refill:** Tokens are added based on time elapsed since last request. 
3. **Consume:** If the bucket has enough tokens, the request is allowed (tokens decrement). 
4. **Reject:** If user doesn't have enough tokens, request is denied. 

All of this happens inside a single Lua script to ensure atomicity. 

```lua
-- Simplified Lua Logic

local time_delta_seconds = math.max(0, time_now - time_of_last_refill)
local tokens_owed = time_delta_seconds * token_refill_rate
current_tokens = math.min(token_capacity, current_tokens + tokens_owed)

-- Code omitted

if current_tokens >= tokens_requested then
can_afford_request = true
current_tokens = current_tokens - tokens_requested
redis.call("HMSET", key, "current_tokens", current_tokens, "time_of_last_refill", time_now)
redis.call("EXPIRE", key, 3600)
else
can_afford_request = false
end

return { can_afford_request and 1 or 0, current_tokens }
```

## ☸️ Kubernetes Deployment

Deploy to a local cluster (Minikube, Docker Desktop) or cloud provider:

```bash
# 1. Apply configurations
kubectl apply -f k8s/

# 2. Check status
kubectl get pods

# 3. Access the service (Docker Desktop/Localhost)
curl http://localhost/check
```

## ☁️ Cloud Deployment (AWS)

The Sentinel Service is designed to be cloud-agnostic but is currently deployed on **AWS EC2** for production demonstration.

**Infrastructure Architecture:**
* **Compute:** AWS EC2 Instance (Ubuntu Linux 24.04 LTS).
* **Networking:** Configured VPC Security Groups to allow public HTTP traffic (Port 80) while restricting SSH (Port 22) to specific IPs.
* **Deployment:** Containerized deployment using Docker via SSH tunnel.

**Deployment Command:**
Once connected to the remote AWS instance via SSH, the service is deployed with a single command pulling from the public registry:

```bash
# Runs on the standard HTTP port (80) exposed to the public internet
docker run -d \
  -p 80:8080 \
  --name sentinel \
  --network sentinel-net \
  -e SPRING_DATA_REDIS_HOST=redis \
  justinwalkerhub/sentinel-service:latest
```
---

### 👤 Justin Walker

* 🌐 [GitHub Profile](https://github.com/walker-systems)
* 💼 [LinkedIn](https://www.linkedin.com/in/justin-castillo-69351198/)

### 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

<p align="center">
  <img src="https://img.shields.io/badge/Made%20with-Java%20%26%20Redis-blue?style=for-the-badge" alt="Made with Love">
</p>
