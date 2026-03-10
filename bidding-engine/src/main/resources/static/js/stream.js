let globalEventSource = null;

function connectToGlobalStream() {
    // Ensure we never open more than 1 socket!
    if (globalEventSource) return;

    // 👇 Connect to the new Global Backend Endpoint
    globalEventSource = new EventSource(`/api/auctions/stream/all`);

    globalEventSource.onmessage = function(event) {
        const updatedAuction = JSON.parse(event.data);

        // 👇 THE MULTIPLEX ROUTER: Dynamically grab the ID from the incoming message
        const auctionId = updatedAuction.id;
        const auctionState = activeAuctions[auctionId];

        const cardElement = document.getElementById(`card-${auctionId}`);
        const priceElement = document.getElementById(`price-${auctionId}`);
        const bidderElement = document.getElementById(`bidder-${auctionId}`);

        const userElement = document.getElementById(`username-${auctionId}`);
        const currentUser = userElement ? userElement.value.trim() : "";

        // If this item isn't on our screen, just ignore it
        if (!auctionState || !priceElement || !bidderElement) return;

        if (cardElement) {
            if (updatedAuction.highBidder === currentUser && currentUser !== "") {
                cardElement.classList.add('ring-4', 'ring-yellow-400', 'border-transparent');
            } else {
                cardElement.classList.remove('ring-4', 'ring-yellow-400', 'border-transparent');
            }
        }

        let endMs;
        const rawTime = updatedAuction.endsAt;

        if (typeof rawTime === 'number') {
            endMs = rawTime * 1000;
        } else if (typeof rawTime === 'string' && !rawTime.includes('-')) {
            endMs = parseFloat(rawTime) * 1000;
        } else {
            endMs = new Date(rawTime).getTime();
        }

        if (auctionState.endsAt && endMs > auctionState.endsAt + 1000) {
            const timerEl = document.getElementById(`timer-${auctionId}`);
            timerEl.classList.remove('text-gray-600');
            timerEl.classList.add('text-green-500', 'scale-110');
            setTimeout(() => {
                timerEl.classList.remove('text-green-500', 'scale-110');
                timerEl.classList.add('text-gray-600');
            }, 500);
        }

        auctionState.endsAt = endMs;
        auctionState.highBidder = updatedAuction.highBidder || 'System';

        const bidsElement = document.getElementById(`bids-${auctionId}`);
        if (bidsElement) {
            bidsElement.innerText = updatedAuction.version || 0;
            bidsElement.classList.add('text-blue-600', 'scale-125');
            setTimeout(() => bidsElement.classList.remove('text-blue-600', 'scale-125'), 300);
        }

        if (updatedAuction.currentPrice > auctionState.currentPrice && updatedAuction.highBidder === bidderElement.innerText) {
            priceElement.classList.add('opacity-0');
            bidderElement.classList.add('opacity-0');

            setTimeout(() => {
                priceElement.innerText = `$${updatedAuction.currentPrice.toFixed(2)}`;
                bidderElement.innerText = updatedAuction.highBidder || 'System';

                priceElement.classList.add('text-red-600');
                bidderElement.classList.add('text-red-600');
                priceElement.classList.remove('opacity-0');
                bidderElement.classList.remove('opacity-0');

                setTimeout(() => {
                    priceElement.classList.remove('text-red-600');
                    priceElement.classList.add('text-green-500');
                    priceElement.classList.remove('opacity-0');

                    bidderElement.classList.remove('text-red-600');
                    bidderElement.classList.add('text-gray-700');
                    bidderElement.classList.remove('opacity-0');
                }, 400);
            }, 2000);
        }
        else if (updatedAuction.currentPrice > auctionState.currentPrice || updatedAuction.highBidder !== bidderElement.innerText) {

            if (updatedAuction.currentPrice > auctionState.currentPrice) {
                const bidInput = document.getElementById(`bid-amount-${auctionId}`);
                if(bidInput && parseFloat(bidInput.value) <= updatedAuction.currentPrice) {
                    bidInput.value = updatedAuction.currentPrice + 10;
                }
            }

            priceElement.classList.add('opacity-0');
            bidderElement.classList.add('opacity-0');

            setTimeout(() => {
                priceElement.innerText = `$${updatedAuction.currentPrice.toFixed(2)}`;
                bidderElement.innerText = updatedAuction.highBidder || 'System';

                priceElement.classList.remove('opacity-0');
                bidderElement.classList.remove('opacity-0');
            }, 400);
        }
        auctionState.currentPrice = updatedAuction.currentPrice;
    };

    globalEventSource.onerror = function() {
        console.error("Global stream connection lost. Attempting to reconnect...");
        globalEventSource.close();
        globalEventSource = null;
        setTimeout(connectToGlobalStream, 2000); // Auto-reconnect safety net
    };
}
