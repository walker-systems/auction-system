local key = KEYS[1]                             -- ID of bucket
local token_refill_rate = tonumber(ARGV[1])     -- Tokens refilled per second
local token_capacity = tonumber(ARGV[2])        -- Max per bucket
local time_now = tonumber(ARGV[3])              -- In seconds (Unix epoch)
local tokens_requested = tonumber(ARGV[4])      -- For this request (usually 1)

-- Calculate tokens owed
local current_info = redis.call("HMGET", key, "current_tokens", "time_of_last_refill")
local current_tokens = tonumber(current_info[1])
local time_of_last_refill = tonumber(current_info[2])

if current_tokens == nil then
    current_tokens = token_capacity
    time_of_last_refill = time_now
end

local time_delta_seconds = math.max(0, time_now - time_of_last_refill)
local tokens_owed = time_delta_seconds * token_refill_rate

-- Replenish current balance with owed tokens
current_tokens = math.min(token_capacity, current_tokens + tokens_owed)

-- Process the request
local can_afford_request = false

--
if current_tokens >= tokens_requested then
    can_afford_request = true
    current_tokens = current_tokens - tokens_requested
    redis.call("HMSET", key, "current_tokens", current_tokens, "time_of_last_refill", time_now)
    redis.call("EXPIRE", key, 3600)
else
    can_afford_request = false
end

return { can_afford_request and 1 or 0, current_tokens }
