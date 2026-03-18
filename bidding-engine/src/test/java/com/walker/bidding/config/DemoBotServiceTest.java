package com.walker.bidding.config;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.Duration;

class DemoBotServiceTest {

    @Test
    void testAutoShutoff_FiresAfter10Minutes() {
        StepVerifier.withVirtualTime(() -> Mono.delay(Duration.ofMinutes(10))
                .map(_ -> "SHUTDOWN"))
                .expectSubscription()
                .expectNoEvent(Duration.ofMinutes(9).plusSeconds(59))

                .thenAwait(Duration.ofSeconds(1))

                .expectNext("SHUTDOWN")
                .verifyComplete();
    }
}
