function loadStorefront() {
    fetch('/api/auctions')
        .then(response => response.json())
        .then(auctions => {
            if (auctions.length === 0) {
                console.log("Database still seeding... retrying in 1 second.");
                setTimeout(loadStorefront, 1000);
                return;
            }

            auctions.forEach(auction => {
                let endMs;
                const rawTime = auction.endsAt;
                if (typeof rawTime === 'number') endMs = rawTime * 1000;
                else if (typeof rawTime === 'string' && !rawTime.includes('-')) endMs = parseFloat(rawTime) * 1000;
                else endMs = new Date(rawTime).getTime();
                auction.endsAtMs = endMs;
            });

            auctions.sort((a, b) => a.endsAtMs - b.endsAtMs);

            globalAllAuctions = auctions;
            globalActiveCount = auctions.length;
            globalTotalBids = 0;

            auctions.forEach(auction => {
                globalTotalBids += auction.version || 0;
                activeAuctions[auction.id] = {
                    version: auction.version || 0,
                    endsAt: auction.endsAtMs,
                    currentPrice: auction.currentPrice,
                    highBidder: auction.highBidder || 'System',
                    timerEl: null,
                    btnEl: null
                };
            });

            renderPage(1);
            connectToGlobalStream();

            if (clockInterval) clearInterval(clockInterval);
            updateClocks();
            clockInterval = setInterval(updateClocks, 1000);

            if (window.expirationInterval) clearInterval(window.expirationInterval);
            window.expirationInterval = setInterval(sweepExpiredAuctions, 1000);
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
        showCardToast("system", "System Reset Initialized! Rebooting Dashboard...", "bg-yellow-600");
        setTimeout(() => {
            window.location.reload(true);
        }, 1500);
    });
}

function placeBid(auctionId) {
    const username = document.getElementById(`username-${auctionId}`).value;
    const bidInput = document.getElementById(`bid-amount-${auctionId}`);

    // 1. Grab the current requested amount
    const amount = parseFloat(bidInput.value);

    // 🚀 2. OPTIMISTIC UPDATE: Instantly bump the box to the next increment so you can spam it!
    bidInput.value = calculateNextBid(amount);

    const payload = {
        bidderId: username,
        maxBid: amount,
        telemetry: {
            ipAddress: "127.0.0.1",
            userAgent: navigator.userAgent,
            reactionTimeMs: Math.floor(Math.random() * (300 - 100 + 1) + 100)
        }
    };

    // ⚡ 3. VISUAL FEEDBACK: Flash the button green instantly so the user feels the speed
    const btn = document.getElementById(`btn-${auctionId}`);
    if (btn) {
        btn.classList.add('bg-green-500');
        btn.classList.remove('bg-blue-600');
        setTimeout(() => {
            btn.classList.add('bg-blue-600');
            btn.classList.remove('bg-green-500');
        }, 150);
    }

    // 4. Fire the network request asynchronously in the background
    fetch(`/api/auctions/${auctionId}/max-bids`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    }).then(async response => {
        if (!response.ok) {
            const errorData = await response.json();
            const errorMessage = errorData.detail || errorData.error || errorData.message || "Outbid!";
            showCardToast(auctionId, errorMessage, "bg-red-600");

            // If the backend rejected it because a bot was faster, fix our box to the true min bid
            const minBidMatch = errorMessage.match(/at least \$([0-9]+\.?[0-9]*)/);
            if (minBidMatch && minBidMatch[1] && bidInput) {
                bidInput.value = parseFloat(minBidMatch[1]).toFixed(2);
            }
        } else {
            if (activeAuctions[auctionId] && username === 'demo_user') {
                activeAuctions[auctionId].myMaxBid = amount;
            }
            showCardToast(auctionId, "Accepted!", "bg-green-600");
        }
    }).catch(err => {
        showCardToast(auctionId, "Network Error", "bg-red-600");
    });
}
