package io.github.cepeppe.rest.trading.v2;


import io.github.cepeppe.Constants;
import io.github.cepeppe.exception.ApcaRestClientException;
import io.github.cepeppe.http.HttpMethod;
import io.github.cepeppe.json.JsonCodec;
import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.AlpacaRestService;
import io.github.cepeppe.rest.data.response.AlpacaMarketCalendarDayResponse;
import io.github.cepeppe.rest.limits.AlpacaRateLimitMultiton;
import io.github.cepeppe.utils.HttpUtils;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * <h1>AlpacaCalendarRestService</h1>
 *
 * Servizio REST per interrogare il <b>Market Calendar</b> di Alpaca Trading API v2:
 * <ul>
 *   <li><b>GET /v2/calendar</b> – elenco delle sessioni di mercato in un intervallo di date, o per una singola data.</li>
 * </ul>
 *
 * <h2>Riferimenti</h2>
 * Docs ufficiali: https://docs.alpaca.markets/reference/getcalendar-1
 *
 * <h2>Contratto della risposta</h2>
 * L’endpoint restituisce un <b>array</b> di oggetti con campi:
 * <ul>
 *   <li><code>date</code> – stringa "YYYY-MM-DD" (data di trade).</li>
 *   <li><code>open</code> – stringa "HH:mm" (ora di apertura sessione regolare, <i>America/New_York</i>).</li>
 *   <li><code>close</code> – stringa "HH:mm" (ora di chiusura sessione regolare, <i>America/New_York</i>).</li>
 *   <li><code>session_open</code> – stringa "HHmm" (eventuale apertura sessione estesa/pre-market, <i>America/New_York</i>).</li>
 *   <li><code>session_close</code> – stringa "HHmm" (eventuale chiusura sessione estesa/after-hours, <i>America/New_York</i>).</li>
 *   <li><code>settlement_date</code> – stringa "YYYY-MM-DD" (data di regolamento T+1 della trade date; in PAPER è informativa).</li>
 * </ul>
 *
 * <h2>Scelte di implementazione</h2>
 * <ul>
 *   <li><b>DTO</b>: si usa {@link AlpacaMarketCalendarDayResponse} con <i>String</i> per campi orari/data
 *       (nessuna conversione a {@code Instant}/{@code LocalTime} qui; il fuso corretto è <i>America/New_York</i> ed
 *       eventuali conversioni si fanno a livello applicativo con utility dedicate).</li>
 *   <li><b>URL building</b>: con {@link HttpUtils} (join + querystring sicura).</li>
 *   <li><b>Decoding</b>: con {@link JsonCodec} centrale (profilo STRICT di default).</li>
 *   <li><b>Esiti HTTP</b>: status 2xx → OK; altri → {@link io.github.cepeppe.exception.ApcaRestClientException} con preview del body.</li>
 *   <li><b>Liste immutabili</b>: gli elenchi restituiti sono sempre <i>unmodifiable</i>.</li>
 * </ul>
 */
public class AlpacaCalendarRestService extends AlpacaRestService {

    /** Path risorsa: /v2/<b>calendar</b>. */
    private static final String CALENDAR_SUFFIX = "calendar";

    /** Max caratteri di anteprima body per log/exception. */
    private static final int BODY_PREVIEW_LEN = 256;

    /** Logger di progetto. */
    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(AlpacaCalendarRestService.class);

    public AlpacaCalendarRestService(AlpacaRestBaseEndpoints desiredEndpoint) {
        super(desiredEndpoint);
    }

    // =====================================================================================
    // SYNC
    // =====================================================================================

    /**
     * Recupera il calendario per <b>intervallo</b> di date (estremi inclusi).
     *
     * <p>Query supportate: {@code start=YYYY-MM-DD}, {@code end=YYYY-MM-DD}.</p>
     *
     * @param enableRetries abilita le policy di retry del client per metodi idempotenti
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param startInclusive data inizio (inclusa) – non nulla
     * @param endInclusive   data fine (inclusa) – non nulla e >= start
     * @return lista <b>immutabile</b> di giorni di calendario (può essere vuota, mai {@code null})
     * @throws IllegalArgumentException se gli input non sono validi
     * @throws ApcaRestClientException in caso di status non OK o errori di rete/parsing
     */
    public List<AlpacaMarketCalendarDayResponse> getCalendarDays(boolean enableRetries,
                                                                 String accIdForRateLimitTracking,
                                                                 LocalDate startInclusive,
                                                                 LocalDate endInclusive) {

        Objects.requireNonNull(startInclusive, "startInclusive cannot be null");
        Objects.requireNonNull(endInclusive, "endInclusive cannot be null");
        if (endInclusive.isBefore(startInclusive)) {
            throw new IllegalArgumentException("endInclusive must be >= startInclusive");
        }

        final String base = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), CALENDAR_SUFFIX);

        Map<String, String> query = new LinkedHashMap<>();
        query.put("start", startInclusive.toString()); // ISO-8601 yyyy-MM-dd
        query.put("end",   endInclusive.toString());

        final String url = HttpUtils.setQueryParams(base, query);

        long t0 = System.nanoTime();
        try {
            LOG.debug("GET /v2/calendar (sync) | url={} | retries={} | start={} | end={}",
                    url, enableRetries, startInclusive, endInclusive);

            HttpResponse<String> resp = this.httpRestClient.sendHttpRequest(
                    url,
                    HttpMethod.GET,
                    enableRetries,
                    this.alpacaRestConfig.getAuthHeaderParams(),
                    "" // GET: nessun body
            );

            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
            int sc = resp.statusCode();

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc)) {
                AlpacaMarketCalendarDayResponse[] arr =
                        JsonCodec.fromJson(resp.body(), AlpacaMarketCalendarDayResponse[].class);
                List<AlpacaMarketCalendarDayResponse> out =
                        (arr == null) ? List.of() : List.copyOf(Arrays.asList(arr));

                LOG.info("GET /v2/calendar OK | count={} | status={} | ms={}", out.size(), sc, elapsedMs);
                return out;
            }

            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("GET /v2/calendar NOT OK | status={} | ms={} | preview={}", sc, elapsedMs, preview);
            throw new ApcaRestClientException(
                    "getCalendarDays: HTTP status NOT OK: " + sc +
                            " (url=" + url + ", bodyPreview=" + preview + ")"
            );

        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException(
                    "getCalendarDays: error calling Alpaca GET /v2/calendar (url=" + url + "): " + e.getMessage(), e
            );
        }
    }

    /**
     * Recupera il calendario per una <b>singola data</b>.
     *
     * <p>Query supportata: {@code date=YYYY-MM-DD}. Alpaca risponde comunque con un array di 0..1 elementi.</p>
     *
     * @param enableRetries abilita le policy di retry del client
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param date data di interesse (non nulla)
     * @return il giorno di calendario o {@code null} se non presente (es. weekend/festivo)
     * @throws ApcaRestClientException in caso di status non OK o errori di rete/parsing
     */
    public AlpacaMarketCalendarDayResponse getCalendarDay(boolean enableRetries, String accIdForRateLimitTracking, LocalDate date) {
        Objects.requireNonNull(date, "date cannot be null");

        final String base = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), CALENDAR_SUFFIX);
        final String url  = HttpUtils.setQueryParams(base, Map.of("date", date.toString()));

        long t0 = System.nanoTime();
        try {
            LOG.debug("GET /v2/calendar (sync) | url={} | retries={} | date={}", url, enableRetries, date);

            HttpResponse<String> resp = this.httpRestClient.sendHttpRequest(
                    url,
                    HttpMethod.GET,
                    enableRetries,
                    this.alpacaRestConfig.getAuthHeaderParams(),
                    ""
            );

            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
            int sc = resp.statusCode();

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc)) {
                AlpacaMarketCalendarDayResponse[] arr =
                        JsonCodec.fromJson(resp.body(), AlpacaMarketCalendarDayResponse[].class);

                AlpacaMarketCalendarDayResponse out =
                        (arr == null || arr.length == 0) ? null : arr[0];

                LOG.info("GET /v2/calendar OK | date={} | present={} | status={} | ms={}",
                        date, (out != null), sc, elapsedMs);
                return out;
            }

            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("GET /v2/calendar NOT OK | date={} | status={} | ms={} | preview={}",
                    date, sc, elapsedMs, preview);

            throw new ApcaRestClientException(
                    "getCalendarDay: HTTP status NOT OK: " + sc +
                            " (url=" + url + ", bodyPreview=" + preview + ")"
            );

        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException(
                    "getCalendarDay: error calling Alpaca GET /v2/calendar (url=" + url + "): " + e.getMessage(), e
            );
        }
    }

    // =====================================================================================
    // ASYNC
    // =====================================================================================

    /**
     * Variante asincrona: calendario per intervallo (estremi inclusi).
     *
     */
    public CompletableFuture<List<AlpacaMarketCalendarDayResponse>> getAsyncCalendarDays(boolean enableRetries,
                                                                                         String accIdForRateLimitTracking,
                                                                                         LocalDate startInclusive,
                                                                                         LocalDate endInclusive) {
        Objects.requireNonNull(startInclusive, "startInclusive cannot be null");
        Objects.requireNonNull(endInclusive, "endInclusive cannot be null");
        if (endInclusive.isBefore(startInclusive)) {
            throw new IllegalArgumentException("endInclusive must be >= startInclusive");
        }

        final String base = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), CALENDAR_SUFFIX);
        final String url  = HttpUtils.setQueryParams(base, Map.of(
                "start", startInclusive.toString(),
                "end",   endInclusive.toString()
        ));

        LOG.debug("GET /v2/calendar (async) | url={} | retries={} | start={} | end={}",
                url, enableRetries, startInclusive, endInclusive);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url,
                        HttpMethod.GET,
                        enableRetries,
                        this.alpacaRestConfig.getAuthHeaderParams(),
                        ""
                )
                .thenApply(resp -> {
                    int sc = resp.statusCode();
                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc)) {
                        String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException(
                                "getCalendarDays (async): HTTP status NOT OK: " + sc +
                                        " (url=" + url + ", bodyPreview=" + preview + ")"
                        );
                    }
                    return resp.body();
                })
                .thenApply(body -> {
                    AlpacaMarketCalendarDayResponse[] arr =
                            JsonCodec.fromJson(body, AlpacaMarketCalendarDayResponse[].class);
                    return (arr == null) ? List.of() : List.copyOf(Arrays.asList(arr));
                });
    }

    /**
     * Variante asincrona: calendario per singola data.
     *
     * @see #getCalendarDay(boolean, String, LocalDate)
     */
    public CompletableFuture<AlpacaMarketCalendarDayResponse> getAsyncCalendarDay(boolean enableRetries, String accIdForRateLimitTracking, LocalDate date) {
        Objects.requireNonNull(date, "date cannot be null");

        final String base = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), CALENDAR_SUFFIX);
        final String url  = HttpUtils.setQueryParams(base, Map.of("date", date.toString()));

        LOG.debug("GET /v2/calendar (async) | url={} | retries={} | date={}", url, enableRetries, date);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url,
                        HttpMethod.GET,
                        enableRetries,
                        this.alpacaRestConfig.getAuthHeaderParams(),
                        ""
                )
                .thenApply(resp -> {
                    int sc = resp.statusCode();
                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc)) {
                        String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException(
                                "getCalendarDay (async): HTTP status NOT OK: " + sc +
                                        " (url=" + url + ", bodyPreview=" + preview + ")"
                        );
                    }
                    return resp.body();
                })
                .thenApply(body -> {
                    AlpacaMarketCalendarDayResponse[] arr =
                            JsonCodec.fromJson(body, AlpacaMarketCalendarDayResponse[].class);
                    return (arr == null || arr.length == 0) ? null : arr[0];
                });
    }
}
