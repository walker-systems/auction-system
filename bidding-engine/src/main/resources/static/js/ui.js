function createAuctionRow(auction, tbody) {
    const tr = document.createElement('tr');
    tr.id = `row-${auction.id}`;

    tr.className = auction.userInvolved
        ? "bg-green-900 bg-opacity-20 border-l-2 border-green-500 h-[60px] transition-colors duration-150"
        : "terminal-row transition-colors duration-150 h-[60px]";

    const watchingBadge = auction.userInvolved
        ? `<span class="ml-2 text-[9px] bg-green-900 text-black px-1">WATCHING</span>`
        : '';

    const priceFormatted = auction.currentPrice.toLocaleString('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 2});
    const idNum = auction.id.replace('auc-', '');

    tr.innerHTML = `
        <td class="px-4 py-2 whitespace-nowrap text-gray-600 text-xs">
            ${idNum}
        </td>
        <td class="px-4 py-2 whitespace-nowrap">
            <div class="text-green-400 text-sm flex items-center">
                ${auction.itemId}
                ${watchingBadge}
            </div>
            <div class="text-[10px] text-gray-600 mt-0.5">LDR: <span id="bidder-${auction.id}" class="text-gray-400">${auction.highBidder || 'SYS'}</span></div>
        </td>
        <td class="px-4 py-2 whitespace-nowrap">
            <div class="flex flex-col justify-center">
                <div id="price-${auction.id}" class="text-lg text-green-500 transition-colors duration-150">
                    $${priceFormatted}
                </div>
                <div class="h-3 mt-0.5 text-left">
                    <div id="max-badge-${auction.id}" class="hidden text-[9px] px-1 bg-gray-800 text-gray-300"></div>
                </div>
            </div>
        </td>
        <td class="px-4 py-2 whitespace-nowrap text-center">
            <span id="bids-${auction.id}" class="text-xs text-gray-500">[${auction.version || 0}]</span>
        </td>
        <td class="px-4 py-2 whitespace-nowrap text-right">
            <span id="timer-${auction.id}" class="inline-block w-20 text-right text-sm text-gray-500 transition-colors duration-150">--:--:--</span>
        </td>
        <td class="px-4 py-2 whitespace-nowrap text-center">
            <div class="flex items-center justify-center space-x-1">
                <div class="relative">
                    <span class="absolute inset-y-0 left-0 flex items-center pl-1 text-gray-600 text-xs">$</span>
                    <input type="number" id="bid-amount-${auction.id}" class="pl-4 pr-1 py-0.5 w-20 text-xs bg-black border border-gray-700 text-green-500 focus:outline-none focus:border-green-500" value="${calculateNextBid(auction.currentPrice)}" step="0.01">
                </div>
                <input type="text" id="username-${auction.id}" class="py-0.5 px-1 w-12 text-xs bg-black border border-gray-700 text-green-500 focus:outline-none focus:border-green-500 text-center" value="You">
                <button id="btn-${auction.id}" onclick="placeBid('${auction.id}')" class="bg-[#050505] border border-gray-600 hover:border-green-500 hover:text-green-400 text-gray-500 text-xs py-0.5 px-2 transition-colors duration-150">
                    EXE
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

    toast.className = `custom-toast bg-black border ${colorClass} px-2 py-0.5 text-[10px] transform transition-all duration-150 absolute z-50`;
    toast.innerText = message;
    void toast.offsetWidth;

    toastTimers[auctionId].fadeTimer = setTimeout(() => {
        toast.style.opacity = '0';
    }, 1500);

    toastTimers[auctionId].removeTimer = setTimeout(() => {
        if (toast && toast.parentNode) {
            toast.parentNode.removeChild(toast);
        }
    }, 1700);
}
