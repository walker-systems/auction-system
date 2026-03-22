const activeAuctions = {};
const toastTimers = {};
let clockInterval;
let telemetryInterval;
let isDemoRunning = false;

let globalAllAuctions = [];
let currentPage = 1;
let ITEMS_PER_PAGE = 12;

let globalActiveCount = 0;
let globalTotalBids = 0;
let bidTimestamps = [];

// Sorting State
let sortField = 'endsAtMs';
let sortAsc = true;

document.addEventListener('DOMContentLoaded', () => {
    localStorage.removeItem('logsUnlocked');

    if (!sessionStorage.getItem('system_just_reset')) {
        console.log("Initial load: Auto-triggering system reset...");
        sessionStorage.setItem('system_just_reset', 'true');
        resetSystem();
        return;
    } else {
        console.log("Waking up from reset: Loading storefront...");
        sessionStorage.removeItem('system_just_reset');
        loadStorefront();
    }

    const ctx = document.getElementById('telemetryChart');
    if (ctx) {
        Chart.defaults.color = '#4b5563';
        Chart.defaults.font.family = 'monospace';
        Chart.defaults.borderColor = '#1f2937';

        telemetryChart = new Chart(ctx.getContext('2d'), {
            type: 'line',
            data: {
                labels: Array(30).fill(''),
                datasets: [
                    {
                        label: 'P99 Latency (ms)',
                        data: Array(30).fill(0),
                        borderColor: '#22c55e',
                        backgroundColor: 'rgba(34, 197, 94, 0.05)',
                        yAxisID: 'y',
                        fill: true,
                        tension: 0.1,
                        pointRadius: 0,
                        borderWidth: 1
                    },
                    {
                        label: 'Throughput (TPS)',
                        data: Array(30).fill(0),
                        borderColor: '#ff4444', // 👈 FIX: Neon Red/Orange!
                        backgroundColor: 'rgba(255, 68, 68, 0.05)',
                        borderDash: [2, 2],
                        yAxisID: 'y1',
                        fill: true,
                        tension: 0.1,
                        pointRadius: 0,
                        borderWidth: 1
                    }
                ]
            },
            options: {
                responsive: true, maintainAspectRatio: false, animation: false,
                scales: {
                    y: { type: 'linear', display: true, position: 'left', min: 0, suggestedMax: 60, title: { display: false } },
                    y1: { type: 'linear', display: true, position: 'right', min: 0, suggestedMax: 200, grid: { drawOnChartArea: false }, title: { display: false } },
                    x: { display: false }
                },
                plugins: { legend: { position: 'top', labels: { boxWidth: 10 } }, tooltip: { enabled: false } }
            }
        });
    }
});

function updateItemsPerPage(val) {
    ITEMS_PER_PAGE = parseInt(val);
    renderPage(1);
}

function handleSort(field) {
    if (sortField === field) {
        sortAsc = !sortAsc;
    } else {
        sortField = field;
        sortAsc = true;
    }
    updateSortIcons();
    sortAndRender();
}

function updateSortIcons() {
    const fields = ['id', 'itemId', 'currentPrice', 'version', 'endsAtMs'];
    fields.forEach(f => {
        const el = document.getElementById(`sort-${f}`);
        if (el) {
            if (f === sortField) {
                el.innerText = sortAsc ? '↑' : '↓';
                el.className = "text-green-500 ml-1 font-bold";
            } else {
                el.innerText = '↕';
                el.className = "text-gray-800 group-hover:text-gray-500 ml-1 transition-colors";
            }
        }
    });
}

function sortAndRender() {
    globalAllAuctions.sort((a, b) => {
        if (a.userInvolved && !b.userInvolved) return -1;
        if (!a.userInvolved && b.userInvolved) return 1;

        let valA = a[sortField];
        let valB = b[sortField];

        if (sortField === 'id') {
            valA = parseInt(valA.replace('auc-', ''));
            valB = parseInt(valB.replace('auc-', ''));
            return sortAsc ? (valA - valB) : (valB - valA);
        }

        if (sortField === 'itemId') {
            return sortAsc ? valA.localeCompare(valB) : valB.localeCompare(valA);
        }
        return sortAsc ? (valA - valB) : (valB - valA);
    });

    renderPage(1);
}

function calculateNextBid(currentPrice) {
    let increment = 0.50;
    if (currentPrice >= 1000.00) increment = 50.00;
    else if (currentPrice >= 500.00) increment = 25.00;
    else if (currentPrice >= 100.00) increment = 10.00;
    else if (currentPrice >= 50.00) increment = 5.00;
    else if (currentPrice >= 10.00) increment = 1.00;
    return (currentPrice + increment).toFixed(2);
}

function unlockLogsButton() {
    const btn = document.getElementById('view-logs-btn');
    if (btn) {
        btn.disabled = false;
        btn.classList.remove('text-gray-600', 'cursor-not-allowed');
        btn.classList.add('hover:border-green-500', 'hover:text-green-400');
    }
}

function renderPage(page) {
    currentPage = page;
    const startIndex = (page - 1) * ITEMS_PER_PAGE;
    const endIndex = startIndex + ITEMS_PER_PAGE;
    const pageItems = globalAllAuctions.slice(startIndex, endIndex);

    const tbody = document.getElementById('auctions-tbody');
    if (!tbody) return;

    tbody.style.transition = 'opacity 0.1s ease';
    tbody.style.opacity = '0.5';

    setTimeout(() => {
        tbody.innerHTML = '';

        pageItems.forEach(auction => {
            if (typeof createAuctionRow === 'function') {
                createAuctionRow(auction, tbody);
            }
        });

        pageItems.forEach(auction => {
            if (activeAuctions[auction.id]) {
                activeAuctions[auction.id].timerEl = document.getElementById(`timer-${auction.id}`);
                activeAuctions[auction.id].btnEl = document.getElementById(`btn-${auction.id}`);
            }
        });

        const totalPages = Math.ceil(globalAllAuctions.length / ITEMS_PER_PAGE) || 1;
        const currPageDisplay = document.getElementById('current-page-display');
        const totPageDisplay = document.getElementById('total-pages-display');

        if (currPageDisplay) currPageDisplay.innerText = currentPage;
        if (totPageDisplay) totPageDisplay.innerText = totalPages;

        const prevBtn = document.getElementById('prev-page-btn');
        const nextBtn = document.getElementById('next-page-btn');

        if (prevBtn) prevBtn.disabled = currentPage === 1;
        if (nextBtn) nextBtn.disabled = currentPage === totalPages;

        if (typeof tickTimers === 'function') tickTimers(true);

        tbody.style.opacity = '1';
    }, 100);
}

function changePage(direction) {
    const totalPages = Math.ceil(globalAllAuctions.length / ITEMS_PER_PAGE) || 1;
    let newPage = currentPage + direction;
    if (newPage >= 1 && newPage <= totalPages) {
        renderPage(newPage);
    }
}

setInterval(() => {
    fetch('/api/admin/telemetry')
        .then(r => r.json())
        .then(data => {
            const p99 = data.p99LatencyMs || 0.00;
            const now = Date.now();

            while (bidTimestamps.length > 0 && now - bidTimestamps[0] > 1000) {
                bidTimestamps.shift();
            }
            const tps = bidTimestamps.length;

            const uniqueBidders = new Set();
            for (const id in activeAuctions) {
                const bidder = activeAuctions[id].highBidder;
                if (bidder && bidder !== 'System' && bidder !== 'SYS') {
                    uniqueBidders.add(bidder);
                }
            }

            const elP99 = document.getElementById('stat-p99');
            if (elP99) elP99.innerText = p99.toFixed(2);

            const elTps = document.getElementById('stat-tps');
            if (elTps) elTps.innerText = tps.toLocaleString();

            const elBids = document.getElementById('stat-bids');
            if (elBids) elBids.innerText = globalTotalBids.toLocaleString();

            const elActive = document.getElementById('stat-active');
            if (elActive) elActive.innerText = globalActiveCount.toLocaleString();

            const elBidders = document.getElementById('stat-bidders');
            if (elBidders) elBidders.innerText = uniqueBidders.size.toLocaleString();

            if (telemetryChart) {
                telemetryChart.data.datasets[0].data.shift();
                telemetryChart.data.datasets[0].data.push(p99);
                telemetryChart.data.datasets[1].data.shift();
                telemetryChart.data.datasets[1].data.push(tps);
                telemetryChart.update();
            }
        })
        .catch(err => console.warn("Telemetry offline"));
}, 1000);
