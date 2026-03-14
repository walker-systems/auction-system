const activeAuctions = {};
const toastTimers = {};
let clockInterval;
let isDemoRunning = false;

let globalAllAuctions = [];
let currentPage = 1;
const ITEMS_PER_PAGE = 12;

let globalActiveCount = 0;
let globalTotalBids = 0;
let bidTimestamps = [];
let telemetryChart;

document.addEventListener('DOMContentLoaded', () => {
    localStorage.removeItem('logsUnlocked');
    loadStorefront();

    const ctx = document.getElementById('telemetryChart');
    if (ctx) {
        telemetryChart = new Chart(ctx.getContext('2d'), {
            type: 'line',
            data: {
                labels: Array(30).fill(''),
                datasets: [
                    {
                        label: 'P99 Latency (ms)',
                        data: Array(30).fill(0),
                        borderColor: 'rgb(79, 70, 229)',
                        backgroundColor: 'rgba(79, 70, 229, 0.1)',
                        yAxisID: 'y',
                        fill: true,
                        tension: 0.4,
                        pointRadius: 0
                    },
                    {
                        label: 'Throughput (TPS)',
                        data: Array(30).fill(0),
                        borderColor: 'rgb(34, 197, 94)',
                        borderDash: [5, 5],
                        yAxisID: 'y1',
                        tension: 0.4,
                        pointRadius: 0
                    }
                ]
            },
            options: {
                responsive: true, maintainAspectRatio: false, animation: false,
                scales: {
                    y: { type: 'linear', display: true, position: 'left', min: 0, suggestedMax: 60, title: { display: true, text: 'Latency (ms)' } },
                    y1: { type: 'linear', display: true, position: 'right', min: 0, suggestedMax: 200, grid: { drawOnChartArea: false }, title: { display: true, text: 'Network TPS' } },
                    x: { display: false }
                },
                plugins: { legend: { position: 'top' } }
            }
        });
    }
});

function calculateNextBid(currentPrice) {
    let increment = 0.50;
    if (currentPrice >= 1500) increment = 50.00;
    else if (currentPrice >= 750) increment = 25.00;
    else if (currentPrice >= 250) increment = 10.00;
    else if (currentPrice >= 75) increment = 5.00;
    else if (currentPrice >= 25) increment = 1.00;
    return (currentPrice + increment).toFixed(2);
}

function unlockLogsButton() {
    const btn = document.getElementById('view-logs-btn');
    if (btn) {
        btn.disabled = false;
        btn.classList.remove('bg-gray-400', 'text-gray-200', 'cursor-not-allowed');
        btn.classList.add('bg-gray-800', 'hover:bg-gray-900', 'text-green-400', 'hover:-translate-y-1');
    }
}

function renderPage(page) {
    currentPage = page;
    const startIndex = (page - 1) * ITEMS_PER_PAGE;
    const endIndex = startIndex + ITEMS_PER_PAGE;
    const pageItems = globalAllAuctions.slice(startIndex, endIndex);

    const tbody = document.getElementById('auctions-tbody');
    if (!tbody) return;
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

            const elP99 = document.getElementById('stat-p99');
            if (elP99) elP99.innerText = p99.toFixed(2);

            const elTps = document.getElementById('stat-tps');
            if (elTps) elTps.innerText = tps.toLocaleString();

            const elBids = document.getElementById('stat-bids');
            if (elBids) elBids.innerText = globalTotalBids.toLocaleString();

            const elActive = document.getElementById('stat-active');
            if (elActive) elActive.innerText = globalActiveCount.toLocaleString();

            if (telemetryChart) {
                telemetryChart.data.datasets[0].data.shift();
                telemetryChart.data.datasets[0].data.push(p99);
                telemetryChart.data.datasets[1].data.shift();
                telemetryChart.data.datasets[1].data.push(tps);
                telemetryChart.update();
            }
        })
        .catch(err => console.warn("Waiting for telemetry..."));
}, 1000);
