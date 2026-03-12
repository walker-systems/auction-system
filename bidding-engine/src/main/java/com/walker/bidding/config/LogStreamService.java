package com.walker.bidding.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;

@Service
public class LogStreamService {

    private final Sinks.Many<String> sink = Sinks.many().replay().limit(300);

    @PostConstruct
    public void init() {
        sink.tryEmitNext("✅ SYSTEM: LogStream Native DVR Initialized.");

        // Internal heartbeat generator to keep the stream hot and shatter browser buffers
        Flux.interval(Duration.ofMillis(500))
                .subscribe(tick -> sink.tryEmitNext("HEARTBEAT"));
    }

    public void pushLog(String log) {
        if (log != null) {
            sink.tryEmitNext(log.trim());
        }
    }

    public Flux<String> getLogStream() {
        return sink.asFlux();
    }
}
