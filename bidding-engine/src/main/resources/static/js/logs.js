let isTerminalOpen = false;
let logEventSource = null;

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
        if (logEventSource) {
            logEventSource.close();
        }
    }
}

function startLogStream() {
    if (logEventSource) {
        logEventSource.close();
    }

    const cacheBuster = new Date().getTime();
    logEventSource = new EventSource(`/api/admin/logs/stream?t=${cacheBuster}`);
    const outputContainer = document.getElementById('terminal-output');

    logEventSource.onmessage = function(event) {
        const rawLog = event.data;

        if (rawLog !== "HEARTBEAT") {
            console.log("SSE Payload Received:", rawLog);
        }

        if (!rawLog || rawLog === "HEARTBEAT" || rawLog.startsWith("IGNORE_ME")) return;

        const logDiv = document.createElement('div');
        logDiv.className = "whitespace-pre-wrap font-mono";

        if (rawLog.includes("ERROR") || rawLog.includes("🚫")) {
            logDiv.classList.add("text-red-400", "font-bold");
        } else if (rawLog.includes("WARN") || rawLog.includes("⚠️") || rawLog.includes("⏪")) {
            logDiv.classList.add("text-yellow-400");
        } else if (rawLog.includes("✅") || rawLog.includes("🎉") || rawLog.includes("🌱")) {
            logDiv.classList.add("text-green-400");
        } else if (rawLog.includes("🤖") || rawLog.includes("📈") || rawLog.includes("📡")) {
            logDiv.classList.add("text-purple-400");
        } else {
            logDiv.classList.add("text-gray-300");
        }

        logDiv.innerText = rawLog;
        outputContainer.appendChild(logDiv);

        if (outputContainer.children.length > 300) {
            outputContainer.removeChild(outputContainer.children[1]);
        }

        outputContainer.scrollTop = outputContainer.scrollHeight;
    };

    logEventSource.onerror = function() {
        console.error("Lost connection to log stream.");
        logEventSource.close();
    };
}
