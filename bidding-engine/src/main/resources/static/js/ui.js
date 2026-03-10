function createAuctionCard(auction, container) {
    const card = document.createElement('div');
    card.className = "relative overflow-hidden bg-white p-6 rounded-xl shadow-md border border-gray-200 transition-all duration-500 transform flex flex-col";
    card.id = `card-${auction.id}`;

    card.innerHTML = `
        <div class="flex-grow z-10 relative">
            <h2 class="text-xl font-bold text-gray-800 mb-2">${auction.itemId}</h2>
            
            <div class="flex justify-between items-center mb-2">
                <div class="text-3xl font-black text-green-500 transition-all duration-500 opacity-100" id="price-${auction.id}">
                    $${auction.currentPrice.toFixed(2)}
                </div>
                <div class="bg-gray-200 text-gray-700 text-xs font-bold px-3 py-1 rounded-full shadow-inner flex items-center space-x-1">
                    <span>Bids:</span>
                    <span id="bids-${auction.id}" class="transition-all duration-300 transform inline-block">${auction.version || 0}</span>
                </div>
            </div>

            <div id="timer-${auction.id}" class="text-sm font-bold text-gray-600 mb-4 transform transition-all duration-500">
                Time Left: Loading...
            </div>
            <p class="text-sm text-gray-500 mb-2">
                Highest Bidder: <span id="bidder-${auction.id}" class="font-semibold text-gray-700 transition-all duration-500 opacity-100">${auction.highBidder || 'System'}</span>
            </p>
            <div id="max-badge-${auction.id}" class="hidden mt-1 text-xs font-bold px-3 py-1 rounded-full transition-all duration-300"></div>
        </div>
        
        <div class="flex space-x-2 mt-auto pt-4 z-10 relative">
            <input type="text" id="username-${auction.id}" placeholder="Username" class="w-1/3 px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" value="demo_user">
            
            <input type="number" id="bid-amount-${auction.id}" placeholder="$$$" class="w-5/12 px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" value="${(auction.currentPrice + 10).toFixed(2)}">
            
            <button id="btn-${auction.id}" onclick="placeBid('${auction.id}')" class="w-1/4 bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded-lg transition active:scale-95">
                Bid
            </button>
        </div>
        <div id="toast-area-${auction.id}" class="absolute top-0 left-0 w-full h-full pointer-events-none flex flex-col items-center justify-center z-20"></div>
    `;
    container.appendChild(card);
}

function updateClocks() {
    const now = Date.now();

    for (const [id, data] of Object.entries(activeAuctions)) {
        const { endsAt, timerEl, btnEl, highBidder } = data;
        if (!timerEl) continue;

        const diff = endsAt - now;

        if (diff <= 0) {
            const userElement = document.getElementById(`username-${id}`);
            const currentUser = userElement ? userElement.value.trim() : "";
            const cardElement = document.getElementById(`card-${id}`);

            if (highBidder === 'System') {
                timerEl.innerText = "Time Expired";
                timerEl.className = "text-xl font-black text-gray-500 mb-4 tracking-widest transition-all duration-500";
            } else if (highBidder === currentUser && currentUser !== "") {
                timerEl.innerText = "🎉 YOU WON! 🎉";
                timerEl.className = "text-xl font-black text-green-500 mb-4 tracking-widest transition-all duration-500 scale-110";

                if (cardElement) {
                    cardElement.classList.remove('border-gray-200');
                    cardElement.classList.add('border-4', 'border-yellow-400', 'bg-yellow-50');
                }
            } else {
                // Someone else won
                timerEl.innerText = "SOLD!";
                timerEl.className = "text-xl font-black text-red-600 mb-4 tracking-widest transition-all duration-500";
            }

            if (btnEl && !btnEl.disabled) {
                btnEl.disabled = true;
                btnEl.innerText = "Closed";
                btnEl.className = "w-1/4 bg-gray-400 text-white font-bold py-2 px-4 rounded-lg cursor-not-allowed transition-all duration-500";

                document.getElementById(`username-${id}`).disabled = true;
                document.getElementById(`username-${id}`).className = "w-1/3 px-3 py-2 border rounded-lg bg-gray-100 text-gray-500 cursor-not-allowed";

                document.getElementById(`bid-amount-${id}`).disabled = true;
                document.getElementById(`bid-amount-${id}`).className = "w-5/12 px-3 py-2 border rounded-lg bg-gray-100 text-gray-500 cursor-not-allowed";

                if(cardElement && highBidder !== currentUser) {
                    cardElement.classList.remove('ring-4', 'ring-yellow-400', 'border-transparent');
                }
            }
            delete activeAuctions[id];
        } else {            const totalSeconds = Math.floor(diff / 1000);
            const hours = Math.floor(totalSeconds / 3600);
            const minutes = Math.floor((totalSeconds % 3600) / 60);
            const seconds = totalSeconds % 60;

            let timeStr = "";
            if (hours > 0) {
                timeStr += `${hours}:${minutes.toString().padStart(2, '0')}:`;
            } else {
                timeStr += `${minutes}:`;
            }
            timeStr += seconds.toString().padStart(2, '0');

            if (diff < 15000) {
                timerEl.innerText = `⏳ Closing soon: ${timeStr}`;
                timerEl.classList.add("text-red-500");
            } else {
                timerEl.innerText = `Time Left: ${timeStr}`;
                timerEl.classList.remove("text-red-500");
            }
        }
    }
}

function showCardToast(auctionId, message, colorClass) {
    const area = document.getElementById(`toast-area-${auctionId}`);
    if (!area) return;

    if (toastTimers[auctionId]) {
        clearTimeout(toastTimers[auctionId].fadeTimer);
        clearTimeout(toastTimers[auctionId].removeTimer);
    } else {
        toastTimers[auctionId] = {};
    }

    let toast = area.querySelector('.custom-toast');
    if (!toast) {
        toast = document.createElement('div');
        area.appendChild(toast);
    }

    toast.className = `custom-toast ${colorClass} text-white px-6 py-2 rounded-full shadow-2xl transform transition-all duration-300 scale-100 opacity-100 mb-2 font-bold absolute`;
    toast.innerText = message;

    void toast.offsetWidth;

    toastTimers[auctionId].fadeTimer = setTimeout(() => {
        toast.classList.remove('scale-100', 'opacity-100');
        toast.classList.add('scale-110', 'opacity-0');

        toastTimers[auctionId].removeTimer = setTimeout(() => {
            toast.remove();
            delete toastTimers[auctionId];
        }, 300);
    }, 2000);
}
