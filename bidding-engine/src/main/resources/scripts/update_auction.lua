local proposedAuctionKey = KEYS[1]
local activeAuctionsKey = KEYS[2]
local proposedAuctionJson = ARGV[1]

local databaseAuctionJson = redis.call('GET', proposedAuctionKey)
if not databaseAuctionJson then return false end

local databaseAuction = cjson.decode(databaseAuctionJson)
local proposedAuction = cjson.decode(proposedAuctionJson)
local auctionKey = proposedAuctionKey

if tonumber(databaseAuction.version) == (tonumber(proposedAuction.version) - 1) then
    redis.call('SET', auctionKey, proposedAuctionJson)

    -- Atomically update the Active Index to prevent phantom reads
    if proposedAuction.active then
        redis.call('SADD', activeAuctionsKey, proposedAuction.id)
    else
        redis.call('SREM', activeAuctionsKey, proposedAuction.id)
    end

    return true
else
    return false
end
