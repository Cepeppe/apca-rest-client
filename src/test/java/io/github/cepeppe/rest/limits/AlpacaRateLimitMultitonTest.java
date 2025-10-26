package io.github.cepeppe.rest.limits;

import io.github.cepeppe.rest.limits.AlpacaRateLimitInfo;
import io.github.cepeppe.rest.limits.AlpacaRateLimitMultiton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlpacaRateLimitMultitonTest {

    // Helpers ---------------------------------------------------------

    private static HttpHeaders headersOf(Map<String, String> oneValuePerKey) {
        Map<String, List<String>> multi = oneValuePerKey.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> List.of(e.getValue())
                ));
        return HttpHeaders.of(multi, (k, v) -> true);
    }

    private static HttpResponse<?> mockResp(Map<String, String> hdrs) {
        HttpResponse<?> resp = mock(HttpResponse.class);
        when(resp.headers()).thenReturn(headersOf(hdrs));
        return resp;
    }

    private static HttpResponse<?> respWith(int limit, int remaining, long resetEpochSeconds) {
        return mockResp(Map.of(
                "X-RateLimit-Limit", String.valueOf(limit),
                "X-RateLimit-Remaining", String.valueOf(remaining),
                "X-RateLimit-Reset", String.valueOf(resetEpochSeconds)
        ));
    }

    private static HttpResponse<?> respWithMillis(int limit, int remaining, long resetEpochMillis) {
        return mockResp(Map.of(
                "X-RateLimit-Limit", String.valueOf(limit),
                "X-RateLimit-Remaining", String.valueOf(remaining),
                "X-RateLimit-Reset", String.valueOf(resetEpochMillis)
        ));
    }

    // Tests -----------------------------------------------------------

    @Test
    @DisplayName("Singleton identity")
    void singletonIdentity() {
        var a = AlpacaRateLimitMultiton.getInstance();
        var b = AlpacaRateLimitMultiton.getInstance();
        assertSame(a, b);
    }

    @Test
    @DisplayName("Multiton: stessa chiave -> stesso Tracker; chiave diversa -> tracker diversi")
    void multitonMapping() {
        var reg = AlpacaRateLimitMultiton.getInstance();
        var t1a = reg.forAccount("A");
        var t1b = reg.forAccount("A");
        var t2  = reg.forAccount("B");
        assertSame(t1a, t1b);
        assertNotSame(t1a, t2);
    }

    @Test
    @DisplayName("forAccount valida gli ID: null/blank rifiutati")
    void forAccountValidation() {
        var reg = AlpacaRateLimitMultiton.getInstance();
        assertThrows(NullPointerException.class, () -> reg.forAccount(null));
        assertThrows(IllegalArgumentException.class, () -> reg.forAccount("   "));
    }

    @Test
    @DisplayName("First-use: nessuno snapshot -> not initialized, remaining -1, no throttle, tryConsumePermit true")
    void firstUseSemantics() {
        var t = AlpacaRateLimitMultiton.getInstance().forAccount("FIRST");
        assertFalse(t.isInitialized());
        assertEquals(-1, t.remaining());
        assertEquals(Duration.ZERO, t.timeUntilReset());
        assertEquals(Instant.EPOCH, t.resetAt());
        assertFalse(t.shouldThrottle());
        assertTrue(t.tryConsumePermit());
    }

    @Test
    @DisplayName("updateFromResponse: epoch secondi")
    void updateFromResponseSeconds() {
        var reg = AlpacaRateLimitMultiton.getInstance();
        var t = reg.forAccount("SEC");
        long reset = Instant.now().plusSeconds(25).getEpochSecond();

        t.updateFromResponse(respWith(200, 8, reset));

        var s = t.getSnapshot().orElseThrow();
        assertEquals(200, s.limit());
        assertEquals(8, s.remaining());
        assertEquals(reset, s.resetAt().getEpochSecond());
        assertTrue(t.isInitialized());
    }

    @Test
    @DisplayName("updateFromResponse: epoch millisecondi vincente rispetto a reset precedente")
    void updateFromResponseMillisWins() {
        var reg = AlpacaRateLimitMultiton.getInstance();
        var t = reg.forAccount("MS");

        Instant now = Instant.now();
        long earlierSec = now.plusSeconds(10).getEpochSecond();
        long laterMs    = now.plusSeconds(20).toEpochMilli();

        t.updateFromResponse(respWith(200, 5, earlierSec));
        t.updateFromResponse(respWithMillis(200, 3, laterMs));

        var s = t.getSnapshot().orElseThrow();
        assertEquals(3, s.remaining());
        assertEquals(Instant.ofEpochMilli(laterMs), s.resetAt());
    }

    @Test
    @DisplayName("Merging: resetAt più tardi vince; parità -> vince l'ultimo arrivato")
    void mergingPolicy() {
        var t = AlpacaRateLimitMultiton.getInstance().forAccount("MERGE");
        var now = Instant.now();

        var early = new AlpacaRateLimitInfo(200, 100, now.plusSeconds(10));
        var later = new AlpacaRateLimitInfo(200,  50, now.plusSeconds(20));
        var tie   = new AlpacaRateLimitInfo(200,  25, later.resetAt());

        t.update(early);
        assertEquals(early, t.getSnapshot().orElseThrow());

        t.update(later);
        assertEquals(later, t.getSnapshot().orElseThrow());

        t.update(tie);
        assertEquals(tie, t.getSnapshot().orElseThrow());
    }

    @Test
    @DisplayName("shouldThrottle: true solo se remaining <= 0 e reset futuro")
    void shouldThrottleRules() {
        var t = AlpacaRateLimitMultiton.getInstance().forAccount("THR");

        t.update(new AlpacaRateLimitInfo(200, 0, Instant.now().plusSeconds(5)));
        assertTrue(t.shouldThrottle());

        t.update(new AlpacaRateLimitInfo(200, 1, Instant.now().plusSeconds(5)));
        assertFalse(t.shouldThrottle());

        t.update(new AlpacaRateLimitInfo(200, 0, Instant.now().minusSeconds(1)));
        assertFalse(t.shouldThrottle());
    }

    @Test
    @DisplayName("tryConsumePermit: concorrenza -> mai oltre il budget")
    void tryConsumePermitConcurrency() throws Exception {
        var t = AlpacaRateLimitMultiton.getInstance().forAccount("CONC");
        int initial = 12;
        t.update(new AlpacaRateLimitInfo(200, initial, Instant.now().plusSeconds(20)));

        int threads = 8, attempts = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger ok = new AtomicInteger(0);

        for (int i = 0; i < attempts; i++) {
            pool.submit(() -> { if (t.tryConsumePermit()) ok.incrementAndGet(); });
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        int consumed = ok.get();
        assertTrue(consumed <= initial);
        assertEquals(initial - consumed, t.remaining());
    }

    @Test
    @DisplayName("tryConsumePermit si ferma a zero finché non passa il reset")
    void tryConsumePermitStopsAtZero() {
        var t = AlpacaRateLimitMultiton.getInstance().forAccount("ZERO");
        t.update(new AlpacaRateLimitInfo(200, 3, Instant.now().plusSeconds(10)));
        assertTrue(t.tryConsumePermit());
        assertTrue(t.tryConsumePermit());
        assertTrue(t.tryConsumePermit());
        assertEquals(0, t.remaining());
        assertFalse(t.tryConsumePermit());
    }

    @Test
    @DisplayName("Dopo il reset non c'è auto-fabbricazione: niente throttle; tryConsumePermit true ma stato invariato")
    void afterResetNoFabrication() throws InterruptedException {
        var t = AlpacaRateLimitMultiton.getInstance().forAccount("NOFAB");
        var resetSoon = Instant.now().plusMillis(120);
        t.update(new AlpacaRateLimitInfo(200, 0, resetSoon));

        assertTrue(t.shouldThrottle());
        Thread.sleep(150);
        assertFalse(t.shouldThrottle());
        assertTrue(t.tryConsumePermit());
        assertEquals(0, t.remaining(), "non creiamo budget locale; aspettiamo il prossimo response");
    }

    @Test
    @DisplayName("Staleness: true se mai aggiornato o se più vecchio del TTL")
    void staleness() throws InterruptedException {
        var t = AlpacaRateLimitMultiton.getInstance().forAccount("STALE");
        Duration ttl = Duration.ofMillis(80);
        assertTrue(t.isStale(ttl));

        t.update(new AlpacaRateLimitInfo(200, 9, Instant.now().plusSeconds(5)));
        assertFalse(t.isStale(ttl));

        Thread.sleep(100);
        assertTrue(t.isStale(ttl));
    }

    @Test
    @DisplayName("Registry getInfo riflette lo snapshot del tracker")
    void registryMirror() {
        var reg = AlpacaRateLimitMultiton.getInstance();
        var acc = "MIRROR";
        assertTrue(reg.getInfo(acc).isEmpty());

        var info = new AlpacaRateLimitInfo(200, 2, Instant.now().plusSeconds(10));
        reg.update(acc, info);
        assertEquals(info, reg.getInfo(acc).orElseThrow());
    }

    @Test
    @DisplayName("updateFromResponse: se header mancanti -> no-op (snapshot assente)")
    void updateNoopWhenMissingHeaders() {
        var reg = AlpacaRateLimitMultiton.getInstance();
        var t = reg.forAccount("NOHDRS");

        var resp = mockResp(Map.of("X-RateLimit-Limit", "200")); // mancano remaining/reset
        t.updateFromResponse(resp);

        assertFalse(t.isInitialized());
        assertTrue(t.getSnapshot().isEmpty());
    }

    @Test
    @DisplayName("Case-insensitive + trimming (via utils usati dal multiton)")
    void caseInsensitiveAndTrim() {
        var t = AlpacaRateLimitMultiton.getInstance().forAccount("CASE");

        long epochSec = Instant.now().plusSeconds(20).getEpochSecond();
        var resp = mockResp(Map.of(
                "x-ratelimit-limit", " 200 ",
                "x-ratelimit-remaining", " 199 ",
                "x-ratelimit-reset", " " + epochSec + " "
        ));
        t.updateFromResponse(resp);

        var s = t.getSnapshot().orElseThrow();
        assertEquals(200, s.limit());
        assertEquals(199, s.remaining());
        assertEquals(epochSec, s.resetAt().getEpochSecond());
    }
}
