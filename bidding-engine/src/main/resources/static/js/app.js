const activeAuctions = {};
let clockInterval;
let telemetryInterval;
let isDemoRunning = false;
const toastTimers = {};

let globalActiveCount = 0;
let globalTotalBids = 0;
let bidTimestamps = []; // Tracks exact millisecond of every bid for TPS calculation

document.addEventListener('DOMContentLoaded', () => {
    localStorage.removeItem('logsUnlocked');
    loadStorefront();

    if (telemetryInterval) clearInterval(telemetryInterval);
    telemetryInterval = setInterval(updateTelemetryUI, 1000);
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
    bidTimestamps = bidTimestamps.filter(timestamp => now - timestamp <= 1000);

    document.getElementById('stat-active').innerText = globalActiveCount.toLocaleString();
    document.getElementById('stat-bids').innerText = globalTotalBids.toLocaleString();
    document.getElementById('stat-tps').innerText = bidTimestamps.length.toLocaleString();
}

function calculateNextBid(currentPrice) {
    let increment = 50.00;

    if (currentPrice < 10.00) increment = 0.50;
    else if (currentPrice < 50.00) increment = 1.00;
    else if (currentPrice < 100.00) increment = 5.00;
    else if (currentPrice < 500.00) increment = 10.00;
    else if (currentPrice < 1000.00) increment = 25.00;

    return (currentPrice + increment).toFixed(2);
}
