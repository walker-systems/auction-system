const activeAuctions = {};
let clockInterval;
let telemetryInterval;
let isDemoRunning = false;
const toastTimers = {};

let globalAllAuctions = [];
let currentPage = 1;
const ITEMS_PER_PAGE = 12;

let globalActiveCount = 0;
let globalTotalBids = 0;
let bidTimestamps = [];

document.addEventListener('DOMContentLoaded', () => {
    localStorage.removeItem('logsUnlocked');
    loadStorefront();

    if (telemetryInterval) clearInterval(telemetryInterval);
    telemetryInterval = setInterval(updateTelemetryUI, 50);
});

function unlockLogsButton() {
    const btn = document.getElementById('view-logs-btn');
    if (btn) {
        btn.disabled = false;
        btn.classList.remove('bg-gray-400', 'text-gray-200', 'cursor-not-allowed');
        btn.classList.add('bg-gray-800', 'hover:bg-gray-900', 'text-green-400', 'hover:-translate-y-1');
    }
}

function updateTelemetryUI() {
    const now = Date.now();

    while (bidTimestamps.length > 0 && now - bidTimestamps[0] > 1000) {
        bidTimestamps.shift();
    }

    document.getElementById('stat-active').innerText = globalActiveCount.toLocaleString();
    document.getElementById('stat-bids').innerText = globalTotalBids.toLocaleString();
    document.getElementById('stat-tps').innerText = bidTimestamps.length.toLocaleString();
}
function calculateNextBid(currentPrice) {
    const price = parseFloat(currentPrice);
    let increment = 50.00;

    if (price < 10.00) increment = 0.50;
    else if (price < 50.00) increment = 1.00;
    else if (price < 100.00) increment = 5.00;
    else if (price < 500.00) increment = 10.00;
    else if (price < 1000.00) increment = 25.00;

    return (price + increment).toFixed(2);
}
function renderPage(pageNumber) {
    currentPage = pageNumber;
    const tbody = document.getElementById('auction-tbody');
    tbody.innerHTML = '';

    for (const key in activeAuctions) {
        activeAuctions[key].timerEl = null;
        activeAuctions[key].btnEl = null;
    }

    const startIndex = (pageNumber - 1) * ITEMS_PER_PAGE;
    const endIndex = Math.min(startIndex + ITEMS_PER_PAGE, globalAllAuctions.length);

    for (let i = startIndex; i < endIndex; i++) {
        const auction = globalAllAuctions[i];
        createAuctionRow(auction, tbody);

        if (activeAuctions[auction.id]) {
            activeAuctions[auction.id].timerEl = document.getElementById(`timer-${auction.id}`);
            activeAuctions[auction.id].btnEl = document.getElementById(`btn-${auction.id}`);
        }
    }

    const totalPages = Math.ceil(globalAllAuctions.length / ITEMS_PER_PAGE) || 1;
    const currPageDisplay = document.getElementById('current-page-display');
    const totPageDisplay = document.getElementById('total-pages-display');

    if (currPageDisplay) currPageDisplay.innerText = currentPage;
    if (totPageDisplay) totPageDisplay.innerText = totalPages;

    const prevBtn = document.getElementById('prev-page-btn');
    const nextBtn = document.getElementById('next-page-btn');

    if (prevBtn) prevBtn.disabled = currentPage === 1;
    if (nextBtn) nextBtn.disabled = currentPage === totalPages;
}

function changePage(direction) {
    const totalPages = Math.ceil(globalAllAuctions.length / ITEMS_PER_PAGE) || 1;
    let newPage = currentPage + direction;

    if (newPage >= 1 && newPage <= totalPages) {
        renderPage(newPage);
    }
}

function sweepExpiredAuctions() {
    if (!globalAllAuctions || globalAllAuctions.length === 0) return;

    const now = Date.now();
    const initialLength = globalAllAuctions.length;

    globalAllAuctions = globalAllAuctions.filter(a => a.endsAtMs > now);

    if (globalAllAuctions.length !== initialLength) {
        globalActiveCount = globalAllAuctions.length;
        renderPage(currentPage);
    }
}
