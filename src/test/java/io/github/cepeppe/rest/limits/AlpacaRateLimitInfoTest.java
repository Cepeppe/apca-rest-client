package io.github.cepeppe.rest.limits;

import io.github.cepeppe.rest.limits.AlpacaRateLimitInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AlpacaRateLimitInfoTest {

    @Test
    @DisplayName("isDepleted() true quando remaining <= 0; false altrimenti")
    void isDepleted() {
        var future = Instant.now().plusSeconds(10);
        assertTrue(new AlpacaRateLimitInfo(200, 0, future).isDepleted());
        assertTrue(new AlpacaRateLimitInfo(200, -1, future).isDepleted());
        assertFalse(new AlpacaRateLimitInfo(200, 1, future).isDepleted());
    }

    @Test
    @DisplayName("timeUntilReset(): >0 se futuro, ZERO se passato/uguale")
    void timeUntilReset() {
        var future = Instant.now().plusMillis(150);
        var past   = Instant.now().minusSeconds(1);
        assertTrue(new AlpacaRateLimitInfo(200, 1, future).timeUntilReset().compareTo(Duration.ZERO) > 0);
        assertEquals(Duration.ZERO, new AlpacaRateLimitInfo(200, 1, past).timeUntilReset());
    }
}
