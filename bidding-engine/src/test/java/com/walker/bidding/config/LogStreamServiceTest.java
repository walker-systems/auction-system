package com.walker.bidding.config;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class LogStreamServiceTest {

    @Test
    void testPushLog_emitsToStreamSuccessfully() {
        LogStreamService logStreamService = new LogStreamService();

        // Use StepVerifier to subscribe to the stream, push a log, and assert it arrives
        StepVerifier.create(logStreamService.getLogStream())
                .then(() -> logStreamService.pushLog("✅ Test Log to UI!"))
                .expectNext("✅ Test Log to UI!")
                .thenCancel() // Unsubscribe after log is received
                .verify();
    }
}
