let isSeeding = true;

function loadStorefront() {
    const loadingText = document.getElementById('loading-text');

    fetch('/api/admin/seeding-status')
        .then(response => {
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return response.json();
        })
        .then(isBackendSeeding => {
            if (isBackendSeeding === true) {
                if (loadingText) loadingText.innerText = "Seeding Redis Database... Please wait.";
                setTimeout(loadStorefront, 1000);
                return;
            }

            fetch('/api/auctions')
                .then(response => {
                    if (!response.ok) throw new Error(`HTTP ${response.status}`);
                    return response.json();
                })
                .then(auctions => {
                    if (auctions.length === 0) {
                        const loadingText = document.getElementById('loading-text');
                        if (loadingText) loadingText.innerText = "Waking up server... generating fresh auctions.";
                        resetSystem();
                        return;
                    }
                    const overlay = document.getElementById('loading-overlay');
                    if (overlay) {
                        overlay.classList.add('opacity-0', 'pointer-events-none');
                        setTimeout(() => overlay.remove(), 500);
                    }
                    auctions.forEach(auction => {
                        let endMs = Date.now() + 86400000;
                        if (auction.endsAt) {
                            if (typeof auction.endsAt === 'number') {
                                endMs = auction.endsAt > 99999999999 ? auction.endsAt : auction.endsAt * 1000;
                            } else if (typeof auction.endsAt === 'string') {
                                if (auction.endsAt.includes('-')) {
                                    endMs = new Date(auction.endsAt).getTime();
                                } else {
                                    const parsed = parseFloat(auction.endsAt);
                                    endMs = parsed > 99999999999 ? parsed : parsed * 1000;
                                }
                            }
                        }
                        auction.endsAtMs = isNaN(endMs) ? Date.now() + 86400000 : endMs;
                        auction.userInvolved = false;
                    });

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

                    updateSortIcons();
                    sortAndRender();

                    if (typeof connectToGlobalStream === 'function') connectToGlobalStream();
                    if (typeof clockInterval !== 'undefined') clearInterval(clockInterval);
                    clockInterval = setInterval(tickTimers, 1000);
                })
                .catch(err => {
                    console.error("Failed to fetch auctions:", err);
                    if (loadingText) loadingText.innerText = "Reconnecting to streams...";
                    setTimeout(loadStorefront, 2000);
                });
        })
        .catch(err => {
            console.error("Failed to check seeding status:", err);
            if (loadingText) loadingText.innerText = "Booting server... please wait.";
            setTimeout(loadStorefront, 2000);
        });
}

function toggleDemo() {
    const btn = document.getElementById('demo-btn');
    if (typeof unlockLogsButton === 'function') unlockLogsButton();

    if (!isDemoRunning) {
        fetch('/api/admin/start-bots', { method: 'POST' });
        isDemoRunning = true;
        btn.innerHTML = 'Stop Demo';
        btn.classList.replace('bg-blue-600', 'bg-red-600');
        btn.classList.replace('hover:bg-blue-700', 'hover:bg-red-700');
    } else {
        fetch('/api/admin/stop-bots', { method: 'POST' });
        isDemoRunning = false;
        btn.innerHTML = 'Start Demo';
        btn.classList.replace('bg-red-600', 'bg-blue-600');
        btn.classList.replace('hover:bg-red-700', 'hover:bg-blue-700');
    }
}

function resetSystem() {
    const body = document.querySelector('body');
    body.insertAdjacentHTML('beforeend', `
        <div id="loading-overlay" class="fixed inset-0 bg-gray-900 bg-opacity-90 z-[100] flex flex-col items-center justify-center transition-opacity duration-500">
            <div class="animate-spin rounded-full h-24 w-24 border-t-4 border-b-4 border-red-500 mb-6"></div>
            <h2 class="text-3xl font-black text-white tracking-widest mb-2">SYSTEM <span class="text-red-500">RESET</span></h2>
            <p class="text-red-300 font-mono text-sm animate-pulse" id="loading-text">Flushing Redis and Rebuilding World...</p>
        </div>
    `);

    fetch('/api/admin/reset', { method: 'POST' }).then(() => {
        setTimeout(() => { window.location.reload(true); }, 1500);
    });
}

function placeBid(auctionId) {
    const username = document.getElementById(`username-${auctionId}`).value;
    const bidInput = document.getElementById(`bid-amount-${auctionId}`);
    const amount = parseFloat(bidInput.value);

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

    const btn = document.getElementById(`btn-${auctionId}`);
    if (btn) {
        btn.classList.add('bg-green-500');
        btn.classList.remove('bg-blue-600');
        setTimeout(() => {
            btn.classList.add('bg-blue-600');
            btn.classList.remove('bg-green-500');
        }, 150);
    }

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
            if (activeAuctions[auctionId] && username === 'You') {
                activeAuctions[auctionId].myMaxBid = amount;
            }

            const targetAuction = globalAllAuctions.find(a => a.id === auctionId);
            if (targetAuction && !targetAuction.userInvolved) {
                targetAuction.userInvolved = true;
                sortAndRender();
            }

            showCardToast(auctionId, "ACCEPTED", "border-green-500 text-green-400");
        }
    }).catch(err => {
        showCardToast(auctionId, errorMessage, "border-gray-500 text-gray-400");
    });
}

function tickTimers(skipRender = false) {
    const now = Date.now();
    if (globalAllAuctions && globalAllAuctions.length > 0) {
        const originalLength = globalAllAuctions.length;
        globalAllAuctions = globalAllAuctions.filter(a => (a.endsAtMs + 4000) > now);
        if (globalAllAuctions.length < originalLength && !skipRender && typeof renderPage === 'function') {
            renderPage(currentPage);
        }
    }
    globalActiveCount = globalAllAuctions.length;

    for (const id in activeAuctions) {
        const auction = activeAuctions[id];
        const timerEl = auction.timerEl || document.getElementById(`timer-${id}`);
        if (!timerEl) continue;
        if (!auction.timerEl) auction.timerEl = timerEl;

        const diff = auction.endsAt - now;
        if (diff <= 0) {
            timerEl.innerText = "EXPIRED";
            timerEl.className = "inline-block w-20 text-right text-gray-700";
            continue;
        }

        const hours = Math.floor(diff / (1000 * 60 * 60));
        const minutes = Math.floor((diff / 1000 / 60) % 60);
        const seconds = Math.floor((diff / 1000) % 60);

        let timeStr = "";
        if (hours > 0) timeStr += `${hours.toString().padStart(2, '0')}:`;
        timeStr += `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;

        if (diff < 15000) {
            timerEl.innerText = timeStr;
            timerEl.className = "inline-block w-20 text-right text-green-300 font-bold";
        } else {
            timerEl.innerText = timeStr;
            timerEl.className = "inline-block w-20 text-right text-gray-500";
        }
    }
}
