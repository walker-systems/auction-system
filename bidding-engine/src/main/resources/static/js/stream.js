let globalEventSource = null;

function connectToGlobalStream() {
    if (globalEventSource) return;

    globalEventSource = new EventSource(`/api/auctions/stream/all`);

    globalEventSource.onmessage = function(event) {
        const updatedAuction = JSON.parse(event.data);
        const auctionId = updatedAuction.id;
        const auctionState = activeAuctions[auctionId];

        if (auctionState && updatedAuction.version > auctionState.version) {
            globalTotalBids++;
            bidTimestamps.push(Date.now());
        }

        const rowElement = document.getElementById(`row-${auctionId}`);
        const priceElement = document.getElementById(`price-${auctionId}`);
        const bidderElement = document.getElementById(`bidder-${auctionId}`);
        const userElement = document.getElementById(`username-${auctionId}`);
        const currentUser = userElement ? userElement.value.trim() : "";

        if (!auctionState || !priceElement || !bidderElement) return;

        if (rowElement && updatedAuction.currentPrice > auctionState.currentPrice) {
            rowElement.classList.add('bg-green-50');
            setTimeout(() => rowElement.classList.remove('bg-green-50'), 400);
        }

        let endMs;
        const rawTime = updatedAuction.endsAt;
        if (typeof rawTime === 'number') endMs = rawTime * 1000;
        else if (typeof rawTime === 'string' && !rawTime.includes('-')) endMs = parseFloat(rawTime) * 1000;
        else endMs = new Date(rawTime).getTime();

        if (auctionState.endsAt && endMs > auctionState.endsAt + 1000) {
            const timerEl = document.getElementById(`timer-${auctionId}`);
            if(timerEl) {
                timerEl.classList.add('text-yellow-500', 'scale-110');
                setTimeout(() => timerEl.classList.remove('text-yellow-500', 'scale-110'), 500);
            }
        }

        auctionState.endsAt = endMs;
        auctionState.highBidder = updatedAuction.highBidder || 'System';
        auctionState.version = updatedAuction.version || 0;

        const bidsElement = document.getElementById(`bids-${auctionId}`);
        if (bidsElement) {
            bidsElement.innerText = auctionState.version;
            bidsElement.classList.add('bg-blue-200', 'scale-110');
            setTimeout(() => bidsElement.classList.remove('bg-blue-200', 'scale-110'), 300);
        }

        const badgeElement = document.getElementById(`max-badge-${auctionId}`);
        const myMax = auctionState.myMaxBid;

        if (badgeElement && myMax) {
            badgeElement.classList.remove('hidden');
            if (updatedAuction.highBidder === currentUser && currentUser !== "") {
                badgeElement.innerText = `Winning (Max: $${myMax.toFixed(2)})`;
                badgeElement.className = "mt-1 text-[10px] font-bold px-2 py-0.5 rounded-full bg-green-100 text-green-700 border border-green-300 inline-block";
            } else {
                badgeElement.innerText = `Outbid! (Max: $${myMax.toFixed(2)})`;
                badgeElement.className = "mt-1 text-[10px] font-bold px-2 py-0.5 rounded-full bg-red-100 text-red-700 border border-red-300 inline-block";
            }
        }

        if (updatedAuction.currentPrice > auctionState.currentPrice) {
            const bidInput = document.getElementById(`bid-amount-${auctionId}`);

            const requiredMinBid = parseFloat(calculateNextBid(updatedAuction.currentPrice));

            if(bidInput && parseFloat(bidInput.value) < requiredMinBid) {
                bidInput.value = requiredMinBid.toFixed(2);
            }

            priceElement.innerText = `$${updatedAuction.currentPrice.toFixed(2)}`;
            bidderElement.innerText = updatedAuction.highBidder || 'System';
        }
        auctionState.currentPrice = updatedAuction.currentPrice;    };

    globalEventSource.onerror = function() {
        console.error("Global stream connection lost. Attempting to reconnect...");
        globalEventSource.close();
        globalEventSource = null;
        setTimeout(connectToGlobalStream, 2000);
    };
}
