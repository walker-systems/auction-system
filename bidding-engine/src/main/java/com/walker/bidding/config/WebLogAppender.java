package com.walker.bidding.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class WebLogAppender extends AppenderBase<ILoggingEvent> implements ApplicationListener<ApplicationReadyEvent> {

    private final LogStreamService logStreamService;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final ExecutorService loggingExecutor = Executors.newSingleThreadExecutor();

    public WebLogAppender(LogStreamService logStreamService) {
        this.logStreamService = logStreamService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        this.setContext(rootLogger.getLoggerContext());
        this.start();
        rootLogger.addAppender(this);
        logStreamService.pushLog("✅ SYSTEM: Logback Interceptor Attached Successfully.");
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (logStreamService != null && eventObject.getLoggerName() != null && eventObject.getLoggerName().startsWith("com.walker.bidding")) {
            String time = LocalTime.now().format(TIME_FORMATTER);
            String formattedLog = String.format("%s [%s] %-5s - %s",
                    time,
                    eventObject.getThreadName(),
                    eventObject.getLevel(),
                    eventObject.getFormattedMessage()
            );

            loggingExecutor.submit(() -> {
                try {
                    logStreamService.pushLog(formattedLog);
                } catch (Exception e) {
                    // Fail silently so a broken UI connection doesn't crash the engine
                }
            });
        }
    }

    // Clean up the thread pool on shutdown
    @Override
    public void stop() {
        loggingExecutor.shutdown();
        super.stop();
    }
}
