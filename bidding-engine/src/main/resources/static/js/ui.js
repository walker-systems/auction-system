function createAuctionRow(auction, tbody) {
    const tr = document.createElement('tr');
    tr.id = `row-${auction.id}`;
    tr.className = "hover:bg-gray-50 transition-colors duration-300";

    tr.innerHTML = `
        <td class="px-6 py-4 whitespace-nowrap">
            <div class="font-bold text-gray-800 font-sans text-base">${auction.itemId}</div>
            <div class="text-xs text-gray-500 mt-1">Leader: <span id="bidder-${auction.id}" class="font-semibold text-indigo-600 transition-all duration-300">${auction.highBidder || 'System'}</span></div>
        </td>
        <td class="px-6 py-4 whitespace-nowrap">
            <div id="price-${auction.id}" class="text-2xl font-black text-green-600 transition-all duration-300">
                $${auction.currentPrice.toFixed(2)}
            </div>
            <div id="max-badge-${auction.id}" class="hidden mt-1 text-[10px] font-bold px-2 py-0.5 rounded-full bg-green-100 text-green-700 border border-green-300"></div>
        </td>
        <td class="px-6 py-4 whitespace-nowrap text-center">
            <span id="bids-${auction.id}" class="inline-flex items-center justify-center px-3 py-1 text-sm font-bold leading-none text-blue-800 bg-blue-100 rounded-full transition-all duration-300">
                ${auction.version || 0}
            </span>
        </td>
        <td class="px-6 py-4 whitespace-nowrap">
            <div id="timer-${auction.id}" class="text-sm font-bold text-gray-600 transition-all duration-300">
                Loading...
            </div>
        </td>
        <td class="px-6 py-4 whitespace-nowrap text-right relative">
            <div class="flex items-center justify-end space-x-2">
                <input type="text" id="username-${auction.id}" placeholder="User" class="w-24 px-2 py-1.5 text-xs border rounded focus:ring-2 focus:ring-blue-500" value="demo_user">
                <input type="number" id="bid-amount-${auction.id}" class="w-24 px-2 py-1.5 text-sm font-bold border rounded focus:ring-2 focus:ring-blue-500" value="${calculateNextBid(auction.currentPrice)}">
                <button id="btn-${auction.id}" onclick="placeBid('${auction.id}')" class="bg-blue-600 hover:bg-blue-700 text-white font-bold py-1.5 px-4 rounded transition active:scale-95 shadow-sm">
                    BID
                </button>
            </div>
            <div id="toast-area-${auction.id}" class="absolute top-0 right-0 w-full h-full pointer-events-none flex items-center justify-end pr-8 z-20"></div>
        </td>
    `;
    tbody.appendChild(tr);
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
            const rowElement = document.getElementById(`row-${id}`);

            if (highBidder === 'System') {
                timerEl.innerText = "EXPIRED";
                timerEl.className = "text-sm font-black text-gray-400 tracking-wider";
            } else if (highBidder === currentUser && currentUser !== "") {
                timerEl.innerText = "🎉 WON 🎉";
                timerEl.className = "text-sm font-black text-green-500 tracking-wider";
                if (rowElement) rowElement.classList.add('bg-yellow-50');
            } else {
                timerEl.innerText = "SOLD";
                timerEl.className = "text-sm font-black text-red-600 tracking-wider";
            }

            if (btnEl && !btnEl.disabled) {
                btnEl.disabled = true;
                btnEl.className = "bg-gray-400 text-white font-bold py-1.5 px-4 rounded cursor-not-allowed";
                document.getElementById(`username-${id}`).disabled = true;
                document.getElementById(`bid-amount-${id}`).disabled = true;

                globalActiveCount--;
            }
            delete activeAuctions[id];
        } else {
            const totalSeconds = Math.floor(diff / 1000);
            const hours = Math.floor(totalSeconds / 3600);
            const minutes = Math.floor((totalSeconds % 3600) / 60);
            const seconds = totalSeconds % 60;

            let timeStr = "";
            if (hours > 0) timeStr += `${hours}:${minutes.toString().padStart(2, '0')}:`;
            else timeStr += `${minutes}:`;
            timeStr += seconds.toString().padStart(2, '0');

            if (diff < 15000) {
                timerEl.innerText = `⏳ ${timeStr}`;
                timerEl.classList.add("text-red-500");
            } else {
                timerEl.innerText = timeStr;
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

    toast.className = `custom-toast ${colorClass} text-white px-3 py-1 text-xs rounded shadow-lg transform transition-all duration-300 scale-100 opacity-100 font-bold absolute`;
    toast.innerText = message;
    void toast.offsetWidth;

    toastTimers[auctionId].fadeTimer = setTimeout(() => {
        toast.classList.remove('scale-100', 'opacity-100');
        toast.classList.add('scale-110', 'opacity-0');
        toastTimers[auctionId].removeTimer = setTimeout(() => {
            toast.remove();
            delete toastTimers[auctionId];
        }, 300);
    }, 1500);
}
