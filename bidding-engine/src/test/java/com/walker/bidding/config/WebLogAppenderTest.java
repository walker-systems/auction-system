package com.walker.bidding.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class WebLogAppenderTest {

    @Test
    void testAppend_validLog_isPushedToStreamAsynchronously() {
        LogStreamService mockStreamService = mock(LogStreamService.class);
        WebLogAppender appender = new WebLogAppender(mockStreamService);

        ILoggingEvent mockEvent = mock(ILoggingEvent.class);
        when(mockEvent.getLoggerName()).thenReturn("com.walker.bidding.auction.AuctionService");
        when(mockEvent.getThreadName()).thenReturn("main");
        when(mockEvent.getLevel()).thenReturn(Level.INFO);
        when(mockEvent.getFormattedMessage()).thenReturn("System booted.");

        appender.append(mockEvent);

        verify(mockStreamService, timeout(1000)).pushLog(contains("System booted."));
    }

    @Test
    void testAppend_invalidLogger_isFilteredOut() {
        LogStreamService mockStreamService = mock(LogStreamService.class);
        WebLogAppender appender = new WebLogAppender(mockStreamService);

        ILoggingEvent mockEvent = mock(ILoggingEvent.class);

        // Simulating a noisy Spring internal log
        when(mockEvent.getLoggerName()).thenReturn("org.springframework.web.reactive.DispatcherHandler");

        appender.append(mockEvent);

        // Verify the stream service was never called, even after waiting 500ms
        verify(mockStreamService, after(500).never()).pushLog(anyString());
    }
}
