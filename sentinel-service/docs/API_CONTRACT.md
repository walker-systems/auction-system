# API Contract: Sentinel Rate Limiter

## 1. Overview
The Sentinel Rate Limiter Service acts as an internal barrier between authorization and the Bidding Engine. 
It enforces request quotas using the IETF Draft Standard for Rate Limit Headers, which provides 
compatibility with automated clients and prevents "Thundering Herd" scenarios through 
delta-second reset windows (as opposed to absolute timestamps). The Rate Limiter is built as a distributed system to 
facilitate monitoring requests across different servers. 

## 2. Request Identification
Every request must identify the client so that the correct bucket is checked/updated.

| Header | Description                        | Requirement                            |
| :--- |:-----------------------------------|:---------------------------------------|
| `Authorization` | Bearer Token or API Key            | Required for authenticated users.      |
| `X-Forwarded-For` | Client IP address (originating IP) | Required for unauthenticated limiting. |

**Note:** If `X-Forwarded-For` is missing, the system will fall back to the TCP connection IP.

## 3. Response Headers 
Sentinel communicates quota status via the `RateLimit` header field.

### 3.1 The "RateLimit" Header
Provides the state of the user's bucket.

`RateLimit: limit=10, remaining=0, reset=60`

* **limit:** The maximum number of requests allowed in the current window.
* **remaining:** The number of requests the user can still make.
* **reset:** The number of seconds until the quota resets (delta-seconds).

### 3.2 The "RateLimit-Policy" Header
Announces the static rules being enforced, allowing clients to adapt their behavior.

`RateLimit-Policy: 10;w=60`

* **10:** The quota amount.
* **w=60:** The window size in seconds (example: 10 requests per 60 seconds).

## 4. Status Codes & Error Handling

### 4.1 HTTP 429: Too Many Requests
Returned when `remaining` reaches 0. The client cannot retry until the `reset` time has passed.

**JSON Error Body:**
```json
{
  "error": "QUOTA_EXCEEDED",
  "message": "Rate limit exceeded. Please wait 60 seconds.",
  "status": 429,
  "retry_after": 60,
  "policy": "10;w=60"
}
