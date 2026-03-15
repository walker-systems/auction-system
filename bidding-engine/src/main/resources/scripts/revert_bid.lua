local auctionKey = KEYS[1]
local maxBidsKey = KEYS[2]
local fraudUser = ARGV[1]
local fallbackPrice = tonumber(ARGV[2])

local auctionJson = redis.call('GET', auctionKey)
if not auctionJson then return '{"error":"Auction not found"}' end
local auction = cjson.decode(auctionJson)

redis.call('ZREM', maxBidsKey, fraudUser)

local function get_increment(price)
    if price < 10.0 then return 0.50
    elseif price < 50.0 then return 1.00
    elseif price < 100.0 then return 5.00
    elseif price < 500.0 then return 10.00
    elseif price < 1000.0 then return 25.00
    else return 50.00 end
end

local topTwoBidders = redis.call('ZREVRANGE', maxBidsKey, 0, 1, 'WITHSCORES')
local highestBidder = 'System'
local newVisiblePrice = fallbackPrice
if #topTwoBidders > 0 then
    highestBidder = topTwoBidders[1]
    local highestMax = tonumber(topTwoBidders[2])

    if #topTwoBidders == 4 then
        local secondHighestMax = tonumber(topTwoBidders[4])
        local incrementedSecond = secondHighestMax + get_increment(secondHighestMax)

        if incrementedSecond > highestMax then
            newVisiblePrice = highestMax
        else
            newVisiblePrice = incrementedSecond
        end
    end
end

auction.highBidder = highestBidder
auction.currentPrice = newVisiblePrice
auction.version = tonumber(auction.version) + 1

auction.ipAddress = cjson.null
auction.userAgent = cjson.null
auction.reactionTimeMs = 0
auction.bidCountLastMin = 0
auction.isNewIp = 0

local updatedJson = cjson.encode(auction)
redis.call('SET', auctionKey, updatedJson)

return updatedJson
