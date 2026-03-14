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
            <span id="bids-${auction.id}" class="inline-flex items-center justify-center px-3 py-1 text-sm font-bold leading-none text-blue-800 bg-blue-100 rounded-full">${auction.version || 0}</span>
        </td>
        <td class="px-6 py-4 whitespace-nowrap text-right">
            <span id="timer-${auction.id}" class="text-lg font-mono text-gray-700 font-bold transition-colors duration-300">Loading...</span>
        </td>
        <td class="px-6 py-4 whitespace-nowrap text-center">
            <div class="flex items-center justify-center space-x-2">
                <div class="relative">
                    <span class="absolute inset-y-0 left-0 flex items-center pl-2 text-gray-500">$</span>
                    <input type="number" id="bid-amount-${auction.id}" class="pl-6 pr-2 py-1 w-24 text-sm border border-gray-300 rounded focus:ring-blue-500 focus:border-blue-500" value="${calculateNextBid(auction.currentPrice)}" step="0.01">
                </div>
                <input type="text" id="username-${auction.id}" class="py-1 px-2 w-24 text-sm border border-gray-300 rounded focus:ring-blue-500 focus:border-blue-500" value="You">
                <button id="btn-${auction.id}" onclick="placeBid('${auction.id}')" class="bg-blue-600 hover:bg-blue-700 text-white font-bold py-1 px-3 rounded shadow-sm transition-all duration-150 transform hover:-translate-y-0.5 relative overflow-hidden">
                    Bid
                </button>
            </div>
            <div id="toast-area-${auction.id}" class="relative h-0 flex justify-center"></div>
        </td>
    `;
    tbody.appendChild(tr);
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
    }, 1500);

    toastTimers[auctionId].removeTimer = setTimeout(() => {
        if (toast && toast.parentNode) {
            toast.parentNode.removeChild(toast);
        }
    }, 1800);
}
