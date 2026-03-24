local auction_key = KEYS[1]
local zset_key = KEYS[2]

local new_price = tonumber(ARGV[1])
local bidder_id = ARGV[2]
local expected_version = tonumber(ARGV[3])
local new_ends_at = tonumber(ARGV[4])

-- 1. Fetch the JSON String
local auction_json_str = redis.call('GET', auction_key)
if not auction_json_str then
    return -1
end

local auction = cjson.decode(auction_json_str)

-- 2. Check Expiration
local time_array = redis.call("TIME")
local time_now = tonumber(time_array[1]) + (tonumber(time_array[2]) / 1000000)

if time_now >= tonumber(auction.endsAt) then
    return -2
end

-- 3. Check Optimistic Lock (Version)
local current_version = tonumber(auction.version)
if current_version ~= expected_version then
    return -1
end

-- 4. Update the JSON fields
local new_version = current_version + 1
auction.currentPrice = new_price
auction.highBidder = bidder_id
auction.version = new_version
auction.endsAt = new_ends_at

redis.call('SET', auction_key, cjson.encode(auction))
redis.call('ZADD', zset_key, new_price, bidder_id)

return new_version
