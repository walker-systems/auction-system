function loadStorefront() {
    fetch('/api/auctions')
        .then(response => response.json())
        .then(auctions => {
            const grid = document.getElementById('auction-grid');
            grid.innerHTML = '';

            auctions.forEach(auction => {
                createAuctionCard(auction, grid);

                let endMs;
                const rawTime = auction.endsAt;

                if (typeof rawTime === 'number') {
                    endMs = rawTime * 1000;
                } else if (typeof rawTime === 'string' && !rawTime.includes('-')) {
                    endMs = parseFloat(rawTime) * 1000;
                } else {
                    endMs = new Date(rawTime).getTime();
                }

                activeAuctions[auction.id] = {
                    endsAt: endMs,
                    currentPrice: auction.currentPrice,
                    highBidder: auction.highBidder || 'System',
                    timerEl: document.getElementById(`timer-${auction.id}`),
                    btnEl: document.getElementById(`btn-${auction.id}`)
                };

                // 👇 THE FIX: REMOVE connectToLiveStream(auction.id) from inside this loop!
            });

            // 👇 THE FIX: Add ONE global stream connection AFTER the loop finishes
            connectToGlobalStream();

            if (clockInterval) clearInterval(clockInterval);
            updateClocks();
            clockInterval = setInterval(updateClocks, 1000);        })
        .catch(err => console.error("Error fetching storefront:", err));
}

function toggleChaos() {
    const btn = document.getElementById('chaos-btn');
    const banner = document.getElementById('chaos-banner');

    // 👇 THE FIX: Permanently unlock the logs the moment this is clicked!
    localStorage.setItem('logsUnlocked', 'true');
    if (typeof unlockLogsButton === 'function') unlockLogsButton();

    if (!isChaosRunning) {
        fetch('/api/admin/start-bots', { method: 'POST' });
        isChaosRunning = true;
        btn.innerHTML = '🛑 Stop Chaos';
        btn.classList.replace('bg-purple-600', 'bg-red-600');
        btn.classList.replace('hover:bg-purple-700', 'hover:bg-red-700');
        banner.classList.remove('hidden');
    } else {
        fetch('/api/admin/stop-bots', { method: 'POST' });
        isChaosRunning = false;
        btn.innerHTML = '🤖 Start Chaos';
        btn.classList.replace('bg-red-600', 'bg-purple-600');
        btn.classList.replace('hover:bg-red-700', 'hover:bg-purple-700');
        banner.classList.add('hidden');
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
    })
        .then(async response => {
            if (!response.ok) {
                const errorData = await response.json();
                const errorMessage = errorData.detail || errorData.error || errorData.message || "Outbid! You must beat the leader's hidden max.";

                showCardToast(auctionId, errorMessage, "bg-red-600");

                const minBidMatch = errorMessage.match(/at least \$([0-9]+\.?[0-9]*)/);
                if (minBidMatch && minBidMatch[1] && bidInput) {
                    bidInput.value = parseFloat(minBidMatch[1]).toFixed(2);
                }
            } else {
                if (activeAuctions[auctionId] && username === 'demo_user') {
                    activeAuctions[auctionId].myMaxBid = parseFloat(amount);
                }
                showCardToast(auctionId, "Bid Accepted!", "bg-green-600");
                if (bidInput) {
                    bidInput.value = (parseFloat(amount) + 10).toFixed(2);
                }
            }
        })
        .catch(err => {
            showCardToast(auctionId, "Network Error", "bg-red-600");
        });
}
