function loadStorefront() {
    fetch('/api/auctions')
        .then(response => response.json())
        .then(auctions => {
            const tbody = document.getElementById('auction-tbody');
            tbody.innerHTML = '';

            globalActiveCount = auctions.length;

            auctions.forEach(auction => {
                createAuctionRow(auction, tbody);
                globalTotalBids += auction.version || 0;

                let endMs;
                const rawTime = auction.endsAt;
                if (typeof rawTime === 'number') endMs = rawTime * 1000;
                else if (typeof rawTime === 'string' && !rawTime.includes('-')) endMs = parseFloat(rawTime) * 1000;
                else endMs = new Date(rawTime).getTime();

                activeAuctions[auction.id] = {
                    version: auction.version || 0,
                    endsAt: endMs,
                    currentPrice: auction.currentPrice,
                    highBidder: auction.highBidder || 'System',
                    timerEl: document.getElementById(`timer-${auction.id}`),
                    btnEl: document.getElementById(`btn-${auction.id}`)
                };
            });

            connectToGlobalStream();

            if (clockInterval) clearInterval(clockInterval);
            updateClocks();
            clockInterval = setInterval(updateClocks, 1000);
        })
        .catch(err => console.error("Error fetching storefront:", err));
}

function toggleDemo() {
    const btn = document.getElementById('demo-btn');

    if (typeof unlockLogsButton === 'function') unlockLogsButton();

    if (!isDemoRunning) {
        fetch('/api/admin/start-bots', { method: 'POST' });
        isDemoRunning = true;
        btn.innerHTML = '🛑 Stop Demo';
        btn.classList.replace('bg-blue-600', 'bg-red-600');
        btn.classList.replace('hover:bg-blue-700', 'hover:bg-red-700');
    } else {
        fetch('/api/admin/stop-bots', { method: 'POST' });
        isDemoRunning = false;
        btn.innerHTML = '🚀 Start Demo';
        btn.classList.replace('bg-red-600', 'bg-blue-600');
        btn.classList.replace('hover:bg-red-700', 'hover:bg-blue-700');
    }
}

function resetSystem() {
    fetch('/api/admin/reset', { method: 'POST' }).then(() => {
        setTimeout(() => window.location.reload(), 1000);
    });
}

function placeBid(auctionId) {
    const username = document.getElementById(`username-${auctionId}`).value;
    const bidInput = document.getElementById(`bid-amount-${auctionId}`);
    const amount = bidInput.value;

    const payload = {
        bidderId: username,
        maxBid: parseFloat(amount),
        telemetry: {
            ipAddress: "127.0.0.1",
            userAgent: navigator.userAgent,
            reactionTimeMs: Math.floor(Math.random() * (300 - 100 + 1) + 100)
        }
    };

    fetch(`/api/auctions/${auctionId}/max-bids`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    }).then(async response => {
        if (!response.ok) {
            const errorData = await response.json();
            const errorMessage = errorData.detail || errorData.error || errorData.message || "Outbid!";
            showCardToast(auctionId, errorMessage, "bg-red-600");
            const minBidMatch = errorMessage.match(/at least \$([0-9]+\.?[0-9]*)/);
            if (minBidMatch && minBidMatch[1] && bidInput) {
                bidInput.value = parseFloat(minBidMatch[1]).toFixed(2);
            }
        } else {
            if (activeAuctions[auctionId] && username === 'demo_user') {
                activeAuctions[auctionId].myMaxBid = parseFloat(amount);
            }
            showCardToast(auctionId, "Accepted!", "bg-green-600");

            if (bidInput) {
                bidInput.value = calculateNextBid(parseFloat(amount));
            }
        }    }).catch(err => {
        showCardToast(auctionId, "Network Error", "bg-red-600");
    });
}
