local auction_key = KEYS[1]       -- e.g., "auctions:auc-123"
local zset_key = KEYS[2]          -- e.g., "auctions:auc-123:bids"

local new_price = tonumber(ARGV[1])
local bidder_id = ARGV[2]
local expected_version = tonumber(ARGV[3])

-- 1. Fetch the JSON String
local auction_json_str = redis.call('GET', auction_key)
if not auction_json_str then
    return -1
end

-- Decode the JSON into a Lua table
local auction = cjson.decode(auction_json_str)

-- 2. Check Optimistic Lock (Version)
local current_version = tonumber(auction.version)
if current_version ~= expected_version then
    return -1 -- Version mismatch, reject the bid
end

-- 3. Update the JSON fields
local new_version = current_version + 1
auction.currentPrice = new_price
auction.highBidder = bidder_id
auction.version = new_version

-- Save the updated JSON back to Redis
redis.call('SET', auction_key, cjson.encode(auction))

-- 4. Add to the Bid History ZSet atomically
redis.call('ZADD', zset_key, new_price, bidder_id)

return new_version
