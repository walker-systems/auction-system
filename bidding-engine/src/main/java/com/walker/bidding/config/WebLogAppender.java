package com.walker.bidding.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WebLogAppender extends AppenderBase<ILoggingEvent> {

    private final LogStreamService logStreamService;
    private PatternLayout patternLayout;

    public WebLogAppender(LogStreamService logStreamService) {
        this.logStreamService = logStreamService;
    }

    @PostConstruct
    public void init() {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        patternLayout = new PatternLayout();
        patternLayout.setContext(lc);
        patternLayout.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level - %msg");
        patternLayout.start();

        this.setContext(lc);
        this.start();

        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(this);
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (logStreamService != null && patternLayout != null) {
            // Only broadcast logs from our application to keep the UI clean
            if (eventObject.getLoggerName().startsWith("com.walker.bidding")) {
                String formattedLog = patternLayout.doLayout(eventObject);
                logStreamService.pushLog(formattedLog);
            }
        }
    }
}
