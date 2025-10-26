package io.github.cepeppe.rest.trading.v2.account;



import io.github.cepeppe.Constants;
import io.github.cepeppe.exception.ApcaRestClientException;
import io.github.cepeppe.http.HttpMethod;
import io.github.cepeppe.json.JsonCodec;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.AlpacaRestService;
import io.github.cepeppe.rest.data.response.AlpacaAccountDetailsResponse;
import io.github.cepeppe.rest.data.response.AlpacaPortfolioHistoryResponse;
import io.github.cepeppe.rest.limits.AlpacaRateLimitMultiton;
import io.github.cepeppe.utils.HttpUtils;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * <h1>AlpacaAccountRestService</h1>
 *
 * Servizio REST per l'endpoint Trading v2 di Alpaca relativo all'account:
 * <pre>/v2/account</pre>
 *
 * <h2>Responsabilità</h2>
 * <ul>
 *   <li>Costruire l'URL corretto in base all'ambiente (paper/live) e base URL fornito da {@link AlpacaRestService}.</li>
 *   <li>Eseguire richieste HTTP <b>sincrone</b> e <b>asincrone</b> (raw e decodificate) verso <code>/v2/account</code>
 *       e <code>/v2/account/portfolio/history</code>.</li>
 *   <li>Applicare la logica di retry (se abilitata) tramite il tuo {@code HttpRestClient} per metodi idempotenti.</li>
 *   <li>Validare lo status HTTP secondo {@link Constants.Http#DEFAULT_OK_STATUS_SET}.</li>
 *   <li>Deserializzare il body JSON nei DTO tipizzati
 *       {@link AlpacaAccountDetailsResponse} e {@link AlpacaPortfolioHistoryResponse} tramite {@link JsonCodec}.</li>
 *   <li>Wrappare errori remoti e di I/O in {@link ApcaRestClientException} con contesto utile al debug (URL, status, preview body).</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * Classe stateless; delega a componenti thread-safe (JDK HttpClient dietro a HttpRestClient).
 *
 * <h2>Retry policy</h2>
 * Il flag <code>enableRetries</code> abilita i retry lato client. I retry sono ammessi per metodi idempotenti (GET) e
 * controllati da {@link Constants.Http#DEFAULT_RETRY_STATUS_SET} (429/5xx) con backoff esponenziale + jitter.
 */
public class AlpacaAccountRestService extends AlpacaRestService {

    /** Suffix di risorsa per l'endpoint account. Verrà unito a baseUrl (che deve essere .../v2/). */
    private static final String ACCOUNT_SUFFIX = "account";

    /** Suffix di risorsa per la portfolio history dell'account. */
    private static final String ACCOUNT_PORTFOLIO_HISTORY_SUFFIX = "account/portfolio/history";

    /**
     * Crea il servizio puntando ad uno specifico insieme di endpoint base (paper/live).
     *
     * @param desiredEndpoint insieme di endpoint base (contiene almeno il baseUrl corretto per Trading v2)
     */
    public AlpacaAccountRestService(AlpacaRestBaseEndpoints desiredEndpoint) {
        super(desiredEndpoint);
    }

    // =====================================================================
    // /v2/account
    // =====================================================================

    /**
     * Recupera i dettagli dell'account (sincrono) e decodifica il JSON in {@link AlpacaAccountDetailsResponse}.
     *
     * <p><b>Retry:</b> se {@code enableRetries} è true, la chiamata potrà essere ritentata
     * dal {@code HttpRestClient} secondo la policy configurata per i metodi idempotenti.</p>
     *
     * @param enableRetries abilita/disabilita i retry lato client per questa richiesta
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @return DTO tipizzato con i dettagli dell'account
     * @throws ApcaRestClientException in caso di status HTTP non-OK, errori di rete o parsing JSON
     */
    public AlpacaAccountDetailsResponse getAccountDetails(boolean enableRetries, String accIdForRateLimitTracking){
        final String url = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ACCOUNT_SUFFIX);
        try {
            HttpResponse<String> response = this.httpRestClient.sendHttpRequest(
                    url,
                    HttpMethod.GET,
                    enableRetries,
                    this.alpacaRestConfig.getAuthHeaderParams(),
                    "" // GET: nessun body (il client dovrebbe ignorarlo/normalizzarlo)
            );

            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(response.statusCode())) {
                return JsonCodec.fromJson(response.body(), AlpacaAccountDetailsResponse.class);
            } else {
                String bodyPreview = HttpUtils.safePreview(response.body(), 256);
                throw new ApcaRestClientException(
                        "getAccountDetails: HTTP status NOT OK: " + response.statusCode() +
                                " (url=" + url + ", bodyPreview=" + bodyPreview + ")"
                );
            }
        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException("getAccountDetails: error calling Alpaca account (url=" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Esegue la chiamata (asincrona), valida lo status, e decodifica il body JSON in {@link AlpacaAccountDetailsResponse}.
     *
     * <p>La future viene completata <i>exceptionally</i> se lo status non è in
     * {@link Constants.Http#DEFAULT_OK_STATUS_SET} o se la deserializzazione fallisce.</p>
     *
     * @param enableRetries abilita/disabilita i retry lato client per questa richiesta
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @return future con il DTO dell'account
     */
    public CompletableFuture<AlpacaAccountDetailsResponse> getAsyncAccountDetails(boolean enableRetries, String accIdForRateLimitTracking) {
        final String url = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ACCOUNT_SUFFIX);
        return this.httpRestClient.sendAsyncHttpRequest(
                        url,
                        HttpMethod.GET,
                        enableRetries,
                        this.alpacaRestConfig.getAuthHeaderParams(),
                        "" // GET: nessun body
                )
                .thenApply(resp -> {
                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                        String bodyPreview = HttpUtils.safePreview(resp.body(), 256);
                        throw new ApcaRestClientException(
                                "getAccountDetails (async): HTTP status NOT OK: " + resp.statusCode() +
                                        " (url=" + url + ", bodyPreview=" + bodyPreview + ")"
                        );
                    }
                    return resp.body();
                })
                .thenApply(body -> JsonCodec.fromJson(body, AlpacaAccountDetailsResponse.class));
    }

    // =====================================================================
    // /v2/account/portfolio/history
    // =====================================================================

    /**
     * <h3>Get Account Portfolio History (sincrono, default)</h3>
     * Recupera la serie temporale <i>equity / profit&loss</i> con i default server (es. periodo 1M, timeframe derivato).
     *
     * <p>Equivalente a chiamare l’overload completo con tutti i parametri opzionali {@code null}.</p>
     *
     * @param enableRetries abilita i retry lato client (GET idempotente)
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @return DTO tipizzato {@link AlpacaPortfolioHistoryResponse}
     * @throws ApcaRestClientException per status non-OK o errori di rete/parsing
     */
    public AlpacaPortfolioHistoryResponse getAccountPortfolioHistory(boolean enableRetries, String accIdForRateLimitTracking) {
        return getAccountPortfolioHistory(
                enableRetries,
                accIdForRateLimitTracking,
                null, // period
                null, // timeframe
                null, // intraday_reporting
                null, // start
                null, // end
                null, // pnl_reset
                null, // extended_hours (deprecato)
                (String) null // cashflow_types
        );
    }

    /**
     * <h3>Get Account Portfolio History (sincrono, parametri completi)</h3>
     *
     * <p>Costruisce l'URL <code>/v2/account/portfolio/history</code> con i query param documentati:
     * {@code period}, {@code timeframe}, {@code intraday_reporting}, {@code start}, {@code end},
     * {@code pnl_reset}, {@code extended_hours} (deprecato), {@code cashflow_types}.</p>
     *
     * <p><b>Vincoli</b>:
     * <ul>
     *   <li>Al massimo <b>due</b> tra {@code start}, {@code end}, {@code period} possono essere specificati.</li>
     *   <li>Per periodi &gt; 30 giorni, Alpaca accetta solo {@code timeframe=1D}.</li>
     *   <li>Per uso crypto, si consiglia: {@code intraday_reporting=continuous}, {@code pnl_reset=no_reset}.</li>
     * </ul>
     * </p>
     *
     * @param enableRetries       abilita i retry lato client
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param period              es. {@code 1D}, {@code 1W}, {@code 1M}, {@code 1A}; {@code null} per default
     * @param timeframe           es. {@code 1Min}, {@code 5Min}, {@code 15Min}, {@code 1H}, {@code 1D}; {@code null} = default server
     * @param intradayReporting   {@code market_hours} | {@code extended_hours} | {@code continuous}; {@code null} = default
     * @param start               istante RFC3339 (server normalizza a America/New_York e allinea al timeframe); {@code null} se assente
     * @param end                 istante RFC3339; {@code null} se assente
     * @param pnlReset            {@code per_day} (default) | {@code no_reset}; solo per timeframe &lt; 1D
     * @param extendedHours       <b>deprecato</b>, preferire {@code intraday_reporting}; incluso solo se non-null
     * @param cashflowTypesCsv    {@code "ALL"}, {@code "NONE"} oppure CSV di activity types (es. {@code "DIV,CFEE"})
     * @return DTO tipizzato {@link AlpacaPortfolioHistoryResponse}
     * @throws ApcaRestClientException per status non-OK o errori di rete/parsing
     * @throws IllegalArgumentException se più di 2 tra {@code start}/{@code end}/{@code period} sono valorizzati
     */
    public AlpacaPortfolioHistoryResponse getAccountPortfolioHistory(
            boolean enableRetries,
            String accIdForRateLimitTracking,
            String period,
            String timeframe,
            String intradayReporting,
            Instant start,
            Instant end,
            String pnlReset,
            Boolean extendedHours,
            String cashflowTypesCsv
    ) {
        final String base = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ACCOUNT_PORTFOLIO_HISTORY_SUFFIX);
        validateStartEndPeriod(start, end, period);

        final Map<String, String> query = buildPortfolioHistoryQueryParams(
                period, timeframe, intradayReporting, start, end, pnlReset, extendedHours, cashflowTypesCsv
        );
        final String url = HttpUtils.setQueryParams(base, query);

        try {
            HttpResponse<String> response = this.httpRestClient.sendHttpRequest(
                    url,
                    HttpMethod.GET,
                    enableRetries,
                    this.alpacaRestConfig.getAuthHeaderParams(),
                    "" // GET: nessun body
            );

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(response);
            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(response.statusCode())) {
                return JsonCodec.fromJson(response.body(), AlpacaPortfolioHistoryResponse.class,
                        JsonCodec.Profile.FLEXIBLE_TEMPORAL_NUMERIC);
            } else {
                String bodyPreview = HttpUtils.safePreview(response.body(), 256);
                throw new ApcaRestClientException(
                        "getAccountPortfolioHistory: HTTP status NOT OK: " + response.statusCode() +
                                " (url=" + url + ", bodyPreview=" + bodyPreview + ")"
                );
            }
        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException("getAccountPortfolioHistory: error calling Alpaca (url=" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Overload pratico: accetta una lista di {@code cashflow_types} e la normalizza in CSV.
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @see #getAccountPortfolioHistory(boolean, String, String, String, String, Instant, Instant, String, Boolean, String)
     */
    public AlpacaPortfolioHistoryResponse getAccountPortfolioHistory(
            boolean enableRetries,
            String accIdForRateLimitTracking,
            String period,
            String timeframe,
            String intradayReporting,
            Instant start,
            Instant end,
            String pnlReset,
            Boolean extendedHours,
            List<String> cashflowTypes
    ) {
        final String csv = HttpUtils.normalizeListForQueryParams(cashflowTypes);
        return getAccountPortfolioHistory(enableRetries, accIdForRateLimitTracking, period, timeframe, intradayReporting, start, end, pnlReset, extendedHours, csv);
    }

    /**
     * <h3>Get Account Portfolio History (asincrono, default)</h3>
     * Future completata con {@link AlpacaPortfolioHistoryResponse} usando i default server.
     *
     * @param enableRetries abilita i retry lato client
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @return {@link CompletableFuture} con DTO tipizzato
     */
    public CompletableFuture<AlpacaPortfolioHistoryResponse> getAsyncAccountPortfolioHistory(boolean enableRetries, String accIdForRateLimitTracking) {
        return getAsyncAccountPortfolioHistory(
                enableRetries,  accIdForRateLimitTracking, null, null, null, null, null, null, null, (String) null
        );
    }

    /**
     * <h3>Get Account Portfolio History (asincrono, parametri completi)</h3>
     * Vedi semantica e vincoli dell’omonimo metodo sincrono.
     *
     * @param enableRetries     abilita i retry lato client
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param period            es. 1D/1W/1M/1A
     * @param timeframe         1Min/5Min/15Min/1H/1D
     * @param intradayReporting market_hours/extended_hours/continuous
     * @param start             istante RFC3339 (stringa ISO-8601 via {@link Instant#toString()})
     * @param end               istante RFC3339
     * @param pnlReset          per_day/no_reset
     * @param extendedHours     deprecato (usare intraday_reporting)
     * @param cashflowTypesCsv  ALL/NONE/CSV
     * @return future con DTO tipizzato
     */
    public CompletableFuture<AlpacaPortfolioHistoryResponse> getAsyncAccountPortfolioHistory(
            boolean enableRetries,
            String accIdForRateLimitTracking,
            String period,
            String timeframe,
            String intradayReporting,
            Instant start,
            Instant end,
            String pnlReset,
            Boolean extendedHours,
            String cashflowTypesCsv
    ) {
        final String base = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ACCOUNT_PORTFOLIO_HISTORY_SUFFIX);
        validateStartEndPeriod(start, end, period);

        final Map<String, String> query = buildPortfolioHistoryQueryParams(
                period, timeframe, intradayReporting, start, end, pnlReset, extendedHours, cashflowTypesCsv
        );
        final String url = HttpUtils.setQueryParams(base, query);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url,
                        HttpMethod.GET,
                        enableRetries,
                        this.alpacaRestConfig.getAuthHeaderParams(),
                        "" // GET: nessun body
                )
                .thenApply(resp -> {
                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                        String bodyPreview = HttpUtils.safePreview(resp.body(), 256);
                        throw new ApcaRestClientException(
                                "getAccountPortfolioHistory (async): HTTP status NOT OK: " + resp.statusCode() +
                                        " (url=" + url + ", bodyPreview=" + bodyPreview + ")"
                        );
                    }
                    return resp.body();
                })
                .thenApply(body ->   JsonCodec.fromJson(body, AlpacaPortfolioHistoryResponse.class, JsonCodec.Profile.FLEXIBLE_TEMPORAL_NUMERIC));
    }

    /**
     * Overload asincrono: accetta una lista di {@code cashflow_types} e la normalizza in CSV.
     *
     * @see #getAsyncAccountPortfolioHistory(boolean, String, String, String, String, Instant, Instant, String, Boolean, String)
     */
    public CompletableFuture<AlpacaPortfolioHistoryResponse> getAsyncAccountPortfolioHistory(
            boolean enableRetries,
            String accIdForRateLimitTracking,
            String period,
            String timeframe,
            String intradayReporting,
            Instant start,
            Instant end,
            String pnlReset,
            Boolean extendedHours,
            List<String> cashflowTypes
    ) {
        final String csv = HttpUtils.normalizeListForQueryParams(cashflowTypes);
        return getAsyncAccountPortfolioHistory(enableRetries, accIdForRateLimitTracking, period, timeframe, intradayReporting, start, end, pnlReset, extendedHours, csv);
    }

    // ---------------------------------------------------------------------
    // Helpers privati: validazione & costruzione query params
    // ---------------------------------------------------------------------

    /**
     * Verifica che non più di 2 tra {@code start}, {@code end}, {@code period} siano valorizzati.
     * In caso contrario, solleva {@link IllegalArgumentException}.
     */
    private static void validateStartEndPeriod(Instant start, Instant end, String period) {
        int count = 0;
        if (start != null) count++;
        if (end != null) count++;
        if (period != null && !period.isBlank()) count++;
        if (count > 2) {
            throw new IllegalArgumentException("Invalid combination: at most two of {start, end, period} may be specified.");
        }
    }

    /**
     * Costruisce la mappa dei query param, includendo solo le coppie chiave/valore non-null e non-blank.
     * <ul>
     *   <li>Gli {@link Instant} sono serializzati in ISO-8601 UTC via {@code Instant.toString()} (RFC3339 compatibile).</li>
     *   <li>{@code extended_hours} è deprecato: viene incluso solo se non-null.</li>
     *   <li>{@code cashflow_types}: accetta CSV già normalizzato (ALL/NONE/CSV).</li>
     * </ul>
     */
    private static Map<String, String> buildPortfolioHistoryQueryParams(
            String period,
            String timeframe,
            String intradayReporting,
            Instant start,
            Instant end,
            String pnlReset,
            Boolean extendedHours,
            String cashflowTypesCsv
    ) {
        Map<String, String> q = new HashMap<>();

        if (period != null && !period.isBlank())               q.put("period", period);
        if (timeframe != null && !timeframe.isBlank())         q.put("timeframe", timeframe);
        if (intradayReporting != null && !intradayReporting.isBlank())
            q.put("intraday_reporting", intradayReporting);
        if (start != null)                                     q.put("start", start.toString()); // ISO-8601 (Z)
        if (end != null)                                       q.put("end", end.toString());     // ISO-8601 (Z)
        if (pnlReset != null && !pnlReset.isBlank())           q.put("pnl_reset", pnlReset);
        if (extendedHours != null)                             q.put("extended_hours", String.valueOf(extendedHours));
        if (cashflowTypesCsv != null && !cashflowTypesCsv.isBlank())
            q.put("cashflow_types", cashflowTypesCsv);

        return q;
    }
}
