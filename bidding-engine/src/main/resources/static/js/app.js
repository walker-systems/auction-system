// --- Global State ---
const activeAuctions = {};
let clockInterval;
let isChaosRunning = false;
const toastTimers = {};

// --- Initialization ---
document.addEventListener('DOMContentLoaded', () => {
    loadStorefront();

    // 👇 THE FIX: Check if they have ever clicked Start Chaos in the past
    if (localStorage.getItem('logsUnlocked') === 'true') {
        unlockLogsButton();
    }
});

// 👇 THE FIX: The function to turn the button back on
function unlockLogsButton() {
    const btn = document.getElementById('view-logs-btn');
    if (btn) {
        btn.disabled = false;
        // Remove the gray disabled styling
        btn.classList.remove('bg-gray-400', 'text-gray-200', 'cursor-not-allowed');
        // Add the cool hacker styling back
        btn.classList.add('bg-gray-800', 'hover:bg-gray-900', 'text-green-400', 'hover:-translate-y-1');
    }
}
