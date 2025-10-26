package io.github.cepeppe.rest.limits;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * <h1>AlpacaRateLimitUtils</h1>
 *
 * Utility per estrarre in modo sicuro i 3 header di rate-limit dalle risposte HTTP di Alpaca.
 *
 * <p><b>Header considerati</b> (case-insensitive):</p>
 * <ul>
 *   <li>{@value #HDR_LIMIT} – intero (richieste/minuto)</li>
 *   <li>{@value #HDR_REMAINING} – intero (residuo/minuto)</li>
 *   <li>{@value #HDR_RESET} – epoch UNIX del prossimo reset (tipicamente <i>secondi</i>)</li>
 * </ul>
 *
 * <p><b>Parsing robusto</b>:</p>
 * <ul>
 *   <li>I nomi degli header sono case-insensitive (RFC): si usa {@code firstValue(...)}.</li>
 *   <li>I valori vengono {@code trim()}-mati prima del parse.</li>
 *   <li>Per sicurezza, l'epoch viene interpretato in modo <i>flessibile</i>:
 *       se &gt; 9_999_999_999 viene trattato come millisecondi, altrimenti secondi.</li>
 * </ul>
 *
 * <p><b>Contratto</b>:</p>
 * <ul>
 *   <li>Se <u>tutti e tre</u> gli header sono presenti e validi, ritorna un {@code Optional} valorizzato.</li>
 *   <li>In caso di header mancanti o malformati, ritorna {@code Optional.empty()} (senza lanciare eccezioni).</li>
 * </ul>
 *
 * <p><b>Esempio d'uso</b>:</p>
 * <pre>{@code
 * HttpResponse<String> resp = httpClient.send(request, BodyHandlers.ofString());
 * AlpacaRateLimitUtils.extractAlpacaRateLimit(resp).ifPresent(rl -> {
 *     if (rl.isDepleted()) {
 *         Thread.sleep(rl.timeUntilReset().toMillis()); // evita 429
 *     }
 *     log.info("rate-limit: {}/{} reset @ {}", rl.remaining(), rl.limit(), rl.resetAt());
 * });
 * }</pre>
 */
public final class AlpacaRateLimitUtils {

    public static final String HDR_LIMIT     = "X-RateLimit-Limit";
    public static final String HDR_REMAINING = "X-RateLimit-Remaining";
    public static final String HDR_RESET     = "X-RateLimit-Reset";

    private static final long EPOCH_MS_THRESHOLD = 9_999_999_999L; // > secondi -> interpreta come millisecondi

    private AlpacaRateLimitUtils() { }

    /**
     * Estrae i 3 header di rate-limit (Limit, Remaining, Reset) dalla {@link HttpResponse} e,
     * se presenti e validi, li mappa in un {@link AlpacaRateLimitInfo}.
     *
     * @param response risposta HTTP (tipicamente {@code HttpResponse<String>})
     * @return {@code Optional.of(AlpacaRateLimitInfo)} se tutti gli header sono presenti e ben formati;
     *         {@code Optional.empty()} altrimenti.
     */
    public static Optional<AlpacaRateLimitInfo> extractAlpacaRateLimit(HttpResponse<?> response) {
        if (response == null) {
            return Optional.empty();
        }
        HttpHeaders headers = response.headers();

        OptionalInt limitOpt     = readIntHeader(headers, HDR_LIMIT);
        OptionalInt remainingOpt = readIntHeader(headers, HDR_REMAINING);
        OptionalLong resetOpt    = readLongHeader(headers, HDR_RESET);

        if (limitOpt.isEmpty() || remainingOpt.isEmpty() || resetOpt.isEmpty()) {
            return Optional.empty();
        }

        Instant resetAt = toInstantFromEpochFlexible(resetOpt.getAsLong());
        return Optional.of(new AlpacaRateLimitInfo(limitOpt.getAsInt(), remainingOpt.getAsInt(), resetAt));
    }

    /** Legge e fa parse di un header intero; vuoto se assente o non numerico. */
    private static OptionalInt readIntHeader(HttpHeaders headers, String name) {
        return headers.firstValue(name)
                .map(String::trim)
                .map(val -> {
                    try { return OptionalInt.of(Integer.parseInt(val)); }
                    catch (NumberFormatException ex) { return OptionalInt.empty(); }
                })
                .orElseGet(OptionalInt::empty);
    }

    /** Legge e fa parse di un header long; vuoto se assente o non numerico. */
    private static OptionalLong readLongHeader(HttpHeaders headers, String name) {
        return headers.firstValue(name)
                .map(String::trim)
                .map(val -> {
                    try { return OptionalLong.of(Long.parseLong(val)); }
                    catch (NumberFormatException ex) { return OptionalLong.empty(); }
                })
                .orElseGet(OptionalLong::empty);
    }

    /**
     * Converte un epoch numerico in {@link Instant} con politica flessibile:
     * <ul>
     *   <li>Valori &le; {@value #EPOCH_MS_THRESHOLD} → trattati come <b>secondi</b> (range sicuro fino all'anno ~2286)</li>
     *   <li>Valori &gt;  {@value #EPOCH_MS_THRESHOLD} → trattati come <b>millisecondi</b></li>
     * </ul>
     */
    private static Instant toInstantFromEpochFlexible(long epoch) {
        return (epoch > EPOCH_MS_THRESHOLD)
                ? Instant.ofEpochMilli(epoch)
                : Instant.ofEpochSecond(epoch);
    }
}
