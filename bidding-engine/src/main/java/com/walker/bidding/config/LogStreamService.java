package com.walker.bidding.config;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
public class LogStreamService {

    private final Sinks.Many<String> sink = Sinks.many().replay().limit(50);

    public void pushLog(String log) {
        sink.tryEmitNext(log);
    }

    public Flux<String> getLogStream() {
        return sink.asFlux();
    }
}
