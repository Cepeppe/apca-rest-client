package io.github.cepeppe.rest.limits;

import io.github.cepeppe.rest.limits.AlpacaRateLimitUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlpacaRateLimitUtilsTest {

    private static HttpHeaders headersOf(Map<String, String> oneValuePerKey) {
        Map<String, List<String>> multi = oneValuePerKey.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> List.of(e.getValue())
                ));
        return HttpHeaders.of(multi, (k, v) -> true);
    }

    private static HttpResponse<?> mockRespWithHeaders(Map<String, String> hdrs) {
        HttpResponse<?> resp = mock(HttpResponse.class);
        when(resp.headers()).thenReturn(headersOf(hdrs));
        return resp;
    }

    @Test
    @DisplayName("Estrazione OK con epoch in secondi")
    void extractSecondsEpoch() {
        long epochSec = Instant.now().plusSeconds(30).getEpochSecond();
        var resp = mockRespWithHeaders(Map.of(
                "X-RateLimit-Limit", "200",
                "X-RateLimit-Remaining", "123",
                "X-RateLimit-Reset", Long.toString(epochSec)
        ));

        var opt = AlpacaRateLimitUtils.extractAlpacaRateLimit(resp);
        assertTrue(opt.isPresent());
        var info = opt.get();
        assertEquals(200, info.limit());
        assertEquals(123, info.remaining());
        assertEquals(epochSec, info.resetAt().getEpochSecond());
    }

    @Test
    @DisplayName("Estrazione OK con epoch in millisecondi")
    void extractMillisEpoch() {
        long epochMs = Instant.now().plusSeconds(45).toEpochMilli();
        var resp = mockRespWithHeaders(Map.of(
                "X-RateLimit-Limit", "200",
                "X-RateLimit-Remaining", "0",
                "X-RateLimit-Reset", Long.toString(epochMs)
        ));

        var opt = AlpacaRateLimitUtils.extractAlpacaRateLimit(resp);
        assertTrue(opt.isPresent());
        assertEquals(Instant.ofEpochMilli(epochMs), opt.get().resetAt());
    }

    @Test
    @DisplayName("Header mancanti o malformati -> Optional.empty()")
    void extractMissingOrMalformed() {
        // manca reset
        var r1 = mockRespWithHeaders(Map.of(
                "X-RateLimit-Limit", "200",
                "X-RateLimit-Remaining", "1"
        ));
        assertTrue(AlpacaRateLimitUtils.extractAlpacaRateLimit(r1).isEmpty());

        // limit non numerico
        var r2 = mockRespWithHeaders(Map.of(
                "X-RateLimit-Limit", "abc",
                "X-RateLimit-Remaining", "1",
                "X-RateLimit-Reset", "123"
        ));
        assertTrue(AlpacaRateLimitUtils.extractAlpacaRateLimit(r2).isEmpty());

        // remaining non numerico
        var r3 = mockRespWithHeaders(Map.of(
                "X-RateLimit-Limit", "200",
                "X-RateLimit-Remaining", "zzz",
                "X-RateLimit-Reset", "123"
        ));
        assertTrue(AlpacaRateLimitUtils.extractAlpacaRateLimit(r3).isEmpty());
    }

    @Test
    @DisplayName("Case-insensitive + trimming")
    void caseInsensitiveAndTrim() {
        long epochSec = Instant.now().plusSeconds(20).getEpochSecond();
        var resp = mockRespWithHeaders(Map.of(
                "x-ratelimit-limit", "  200  ",
                "x-ratelimit-remaining", " 199 ",
                "x-ratelimit-reset", " " + epochSec + " "
        ));

        var opt = AlpacaRateLimitUtils.extractAlpacaRateLimit(resp);
        assertTrue(opt.isPresent());
        var info = opt.get();
        assertEquals(200, info.limit());
        assertEquals(199, info.remaining());
        assertEquals(epochSec, info.resetAt().getEpochSecond());
    }
}
