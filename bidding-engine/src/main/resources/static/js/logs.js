let isTerminalOpen = false;
let logEventSource = null;
let logBuffer = [];

function toggleTerminal() {
    const btn = document.getElementById('view-logs-btn');

    if (!isTerminalOpen && btn && btn.disabled) {
        console.warn("Terminal is locked until Demo starts.");
        return;
    }

    const terminal = document.getElementById('terminal-container');
    isTerminalOpen = !isTerminalOpen;

    if (isTerminalOpen) {
        terminal.classList.remove('translate-y-full');
        startLogStream();
    } else {
        terminal.classList.add('translate-y-full');
        if (logEventSource) logEventSource.close();
    }
}

function startLogStream() {
    if (logEventSource) logEventSource.close();

    const cacheBuster = new Date().getTime();
    logEventSource = new EventSource(`/api/admin/logs/stream?t=${cacheBuster}`);

    logEventSource.onmessage = function(event) {
        const rawLog = event.data;
        if (!rawLog || rawLog === "HEARTBEAT" || rawLog.startsWith("IGNORE_ME")) return;

        logBuffer.push(rawLog);
    };
}

setInterval(() => {
    if (!isTerminalOpen || logBuffer.length === 0) return;

    const outputContainer = document.getElementById('terminal-output');
    const fragment = document.createDocumentFragment();

    const logsToRender = logBuffer.splice(0, 100);

    logsToRender.forEach(rawLog => {
        const logDiv = document.createElement('div');
        logDiv.className = "whitespace-pre-wrap font-mono text-sm mb-1";

        if (rawLog.includes("ERROR") || rawLog.includes("🚫")) logDiv.classList.add("text-red-400", "font-bold");
        else if (rawLog.includes("WARN") || rawLog.includes("⚠️") || rawLog.includes("⏪")) logDiv.classList.add("text-yellow-400");
        else if (rawLog.includes("✅") || rawLog.includes("🎉")) logDiv.classList.add("text-green-400");
        else if (rawLog.includes("🤖") || rawLog.includes("📈")) logDiv.classList.add("text-purple-400");
        else logDiv.classList.add("text-gray-300");

        logDiv.innerText = rawLog;
        fragment.appendChild(logDiv);
    });

    outputContainer.appendChild(fragment);

    while (outputContainer.children.length > 200) {
        outputContainer.removeChild(outputContainer.firstChild);
    }

    outputContainer.scrollTop = outputContainer.scrollHeight;
}, 200);
