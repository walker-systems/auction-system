let globalEventSource = null;

function connectToGlobalStream() {
    if (globalEventSource) return;

    globalEventSource = new EventSource(`/api/auctions/stream/all`);

    globalEventSource.onmessage = function(event) {
        const updatedAuction = JSON.parse(event.data);
        const auctionId = updatedAuction.id;
        const auctionState = activeAuctions[auctionId];

        if (!isDemoRunning && updatedAuction.highBidder && updatedAuction.highBidder.startsWith('Bot-')) {
            isDemoRunning = true;

            const btn = document.getElementById('demo-btn');
            if (btn) {
                btn.innerHTML = 'Stop Demo (Running Globally)';
                btn.classList.replace('bg-blue-600', 'bg-purple-600');
                btn.classList.replace('hover:bg-blue-700', 'hover:bg-purple-700');
            }

            if (typeof unlockLogsButton === 'function') unlockLogsButton();

            showCardToast(auctionId, "🚀 DEMO INITIATED BY ANOTHER USER", "bg-purple-600 text-white font-bold border-purple-400");
        }

        if (auctionState && updatedAuction.version > auctionState.version) {
            globalTotalBids++;
            bidTimestamps.push(Date.now());
        }

        let endMs;
        const rawTime = updatedAuction.endsAt;
        if (typeof rawTime === 'number') endMs = rawTime * 1000;
        else if (typeof rawTime === 'string' && !rawTime.includes('-')) endMs = parseFloat(rawTime) * 1000;
        else endMs = new Date(rawTime).getTime();

        const rowElement = document.getElementById(`row-${auctionId}`);
        const priceElement = document.getElementById(`price-${auctionId}`);
        const bidderElement = document.getElementById(`bidder-${auctionId}`);

        if (!auctionState || !priceElement || !rowElement) return;

        const userElement = document.getElementById(`username-${auctionId}`);
        const currentUser = userElement ? userElement.value.trim() : "";

        const priceIncreased = updatedAuction.currentPrice > auctionState.currentPrice;
        const versionIncreased = updatedAuction.version > auctionState.version;

        if (priceIncreased || versionIncreased) {
            rowElement.classList.add('bg-green-900', 'bg-opacity-20');
            rowElement.classList.remove('terminal-row');

            if (priceIncreased) {
                const formattedNewPrice = updatedAuction.currentPrice.toLocaleString('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 2});
                priceElement.innerText = `$${formattedNewPrice}`;
                priceElement.classList.add('text-green-300');
                priceElement.classList.remove('text-green-500');
            }

            setTimeout(() => {
                if (!auctionState.userInvolved) {
                    rowElement.classList.remove('bg-green-900', 'bg-opacity-20');
                    rowElement.classList.add('terminal-row');
                }
                priceElement.classList.remove('text-green-300');
                priceElement.classList.add('text-green-500');
            }, 150);
        }
        auctionState.endsAt = endMs;
        auctionState.highBidder = updatedAuction.highBidder || 'System';
        auctionState.version = updatedAuction.version || 0;
        auctionState.currentPrice = updatedAuction.currentPrice;

        priceElement.innerText = `$${updatedAuction.currentPrice.toFixed(2)}`;
        bidderElement.innerText = updatedAuction.highBidder || 'System';

        const globalItem = globalAllAuctions.find(a => a.id === auctionId);
        if (globalItem) {
            globalItem.endsAt = updatedAuction.endsAt;
            globalItem.highBidder = updatedAuction.highBidder;
            globalItem.version = updatedAuction.version;
            globalItem.currentPrice = updatedAuction.currentPrice;
        }

        if (auctionState.endsAt && endMs > auctionState.endsAt + 1000) {
            const timerEl = document.getElementById(`timer-${auctionId}`);
            if(timerEl) {
                timerEl.classList.add('text-yellow-500', 'scale-110');
                setTimeout(() => timerEl.classList.remove('text-yellow-500', 'scale-110'), 500);
            }
        }

        const bidsElement = document.getElementById(`bids-${auctionId}`);
        if (bidsElement && versionIncreased) {
            bidsElement.innerText = auctionState.version;
            bidsElement.classList.add('bg-blue-200', 'scale-110');
            setTimeout(() => bidsElement.classList.remove('bg-blue-200', 'scale-110'), 300);
        }

        const badgeElement = document.getElementById(`max-badge-${auctionId}`);
        const myMax = auctionState.myMaxBid;

        if (badgeElement && myMax) {
            badgeElement.classList.remove('hidden');
            if (updatedAuction.highBidder === currentUser && currentUser !== "") {
                badgeElement.innerText = `WIN (MAX: $${myMax.toFixed(2)})`;
                badgeElement.className = "mt-1 text-[9px] px-1 bg-green-900 text-black rounded-sm inline-block";
            } else {
                badgeElement.innerText = `OUTBID (MAX: $${myMax.toFixed(2)})`;
                badgeElement.className = "mt-1 text-[9px] px-1 bg-gray-800 text-gray-400 border border-gray-600 rounded-sm inline-block";
            }
        }

        if (priceIncreased) {
            const bidInput = document.getElementById(`bid-amount-${auctionId}`);
            const requiredMinBid = parseFloat(calculateNextBid(updatedAuction.currentPrice));

            if(bidInput && parseFloat(bidInput.value) < requiredMinBid) {
                bidInput.value = requiredMinBid.toFixed(2);
            }
        }
    };

    globalEventSource.onerror = function() {
        console.error("Global stream connection lost. Attempting to reconnect...");
        globalEventSource.close();
        globalEventSource = null;
        setTimeout(connectToGlobalStream, 2000);
    };
}
