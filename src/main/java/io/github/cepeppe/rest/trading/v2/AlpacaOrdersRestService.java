package io.github.cepeppe.rest.trading.v2;

import io.github.cepeppe.Constants;
import io.github.cepeppe.exception.ApcaRestClientException;
import io.github.cepeppe.http.HttpMethod;
import io.github.cepeppe.json.JsonCodec;
import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.AlpacaRestService;
import io.github.cepeppe.rest.data.request.AlpacaCreateOrderRequest;
import io.github.cepeppe.rest.data.request.AlpacaReplaceOrderRequest;
import io.github.cepeppe.rest.data.response.AlpacaDeleteAllOrdersResponse;
import io.github.cepeppe.rest.data.response.AlpacaOrderResponse;
import io.github.cepeppe.rest.limits.AlpacaRateLimitMultiton;
import io.github.cepeppe.utils.HttpUtils;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * <h1>AlpacaOrdersRestService</h1>
 *
 * <p>Adapter REST per gli endpoint <b>Alpaca Trading API v2 – /v2/orders</b>.</p>
 *
 * <h2>Endpoint coperti</h2>
 * <ul>
 *   <li><b>POST</b> /v2/orders — crea un ordine (non idempotente)</li>
 *   <li><b>GET</b>  /v2/orders — lista ordini con filtri (idempotente)</li>
 *   <li><b>GET</b>  /v2/orders/{order_id} — dettaglio per order_id (idempotente; 404→{@code null})</li>
 *   <li><b>GET</b>  /v2/orders:by_client_order_id?client_order_id=... — dettaglio per client_order_id (idempotente; 404→{@code null})</li>
 *   <li><b>PATCH</b> /v2/orders/{order_id} — replace ordine aperto (non idempotente)</li>
 *   <li><b>DELETE</b> /v2/orders — cancel di tutti gli ordini aperti (idempotente)</li>
 *   <li><b>DELETE</b> /v2/orders/{order_id} — cancel per order_id: 204→{@code true}, 404→{@code false}</li>
 * </ul>
 *
 * <h2>Regole di progetto adottate</h2>
 * <ul>
 *   <li><b>Retry</b>: esposto solo su metodi idempotenti (GET/DELETE). Metodi non idempotenti (POST/PATCH) non hanno flag di retry.</li>
 *   <li><b>Liste</b>: sempre <i>immutabili</i> e mai {@code null}. Body vuoto/null → lista vuota.</li>
 *   <li><b>404</b>: per GET di singolo ordine (by id o by client id) mappato a {@code null}.</li>
 *   <li><b>Logging</b>: debug (call), info (OK), warn (NOT OK con anteprima body).</li>
 *   <li><b>JSON</b>: mapping uniforme con {@link JsonCodec}. Timestamps in {@link Instant} ISO-8601 UTC.</li>
 * </ul>
 *
 * <h2>Glossario parametri & valori ammessi</h2>
 * <p><b>Per i metodi di LISTA:</b></p>
 * <ul>
 *   <li><b>status</b>: {@code "open"|"closed"|"all"} — filtra per stato dell’ordine. Se null/blank, usa default del server.</li>
 *   <li><b>limit</b>: intero positivo (tipico max 500 lato Alpaca). Se null, usa default del server.</li>
 *   <li><b>after</b> / <b>until</b>: finestra temporale in ISO-8601/RFC3339 (qui {@link Instant#toString()}). Null → non applicato.</li>
 *   <li><b>direction</b>: {@code "asc"|"desc"} — ordinamento temporale. Null/blank → default del server.</li>
 *   <li><b>nested</b>: {@code true|false} — se true, include <i>legs</i> (ordini figlio) nel payload.</li>
 *   <li><b>side</b>: {@code "buy"|"sell"} — filtra lato. Null/blank → non applicato.</li>
 *   <li><b>symbols</b>: lista simboli (es. {@code ["AAPL","TSLA","BTCUSD"]}); viene inviata come CSV in query. Vuota/null → non applicato.</li>
 * </ul>
 *
 * <p><b>Per i request di creazione/sostituzione ordine:</b></p>
 * <ul>
 *   <li><b>side</b>: {@code "buy"|"sell"}.</li>
 *   <li><b>type</b>: {@code "market"|"limit"|"stop"|"stop_limit"|"trailing_stop"}.</li>
 *   <li><b>time_in_force</b> (TIF): {@code "day"|"gtc"|"opg"|"cls"|"ioc"|"fok"}.</li>
 *   <li><b>order_class</b>: {@code "simple"|"bracket"|"oco"|"oto"}; per opzioni multi-gamba: {@code "mleg"}.</li>
 *   <li><b>qty</b> vs <b>notional</b>: <u>mutuamente esclusivi</u>; per equities/crypto deve esistere almeno uno dei due.</li>
 *   <li><b>limit_price</b> / <b>stop_price</b>: richiesti/ammessi in base a {@code type} (es. limit → {@code limit_price}).</li>
 *   <li><b>trailing_stop</b>: impostare <b>esattamente uno</b> fra {@code trail_price} e {@code trail_percent}.</li>
 *   <li><b>extended_hours</b>: {@code true|false}. Valido <u>solo</u> per equities con {@code type=limit} e {@code time_in_force=day}.</li>
 *   <li><b>client_order_id</b>: stringa ≤ 128 caratteri (utile per idempotenza applicativa lato client).</li>
 *   <li><b>mleg</b>: quando {@code order_class=mleg}, il set di {@code legs} è obbligatorio e ciascuna gamba deve avere {@code ratio_qty>0} e campi coerenti (es. {@code position_intent}).</li>
 *   <li><b>replace</b> (PATCH): campi ammessi dal server includono tipicamente {@code qty}, {@code time_in_force}, {@code limit_price}, {@code stop_price}, {@code trail_price} / {@code trail_percent}, {@code client_order_id}.</li>
 * </ul>
 *
 * <p><b>Idempotenza & retry:</b> POST/PATCH non espongono retry (non idempotenti). GET/DELETE espongono {@code enableRetries} e si appoggiano alle policy del tuo {@code HttpRestClient}.</p>
 */
public class AlpacaOrdersRestService extends AlpacaRestService {

    /** Path base: /v2/<b>orders</b>. */
    private static final String ORDERS_SUFFIX = "orders";

    /** Path speciale: /v2/<b>orders:by_client_order_id</b> (lookup per client_order_id con query param omonimo). */
    private static final String ORDERS_BY_CLIENT_ID_SUFFIX = "orders:by_client_order_id";

    /** Max caratteri di anteprima del body in log/exception (per non inondare i log). */
    private static final int BODY_PREVIEW_LEN = 256;

    /** Logger di progetto (facciata SLF4J/Logback). */
    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(AlpacaOrdersRestService.class);

    /**
     * Costruisce il servizio puntando a un endpoint specifico (paper o production).
     *
     * @param desiredEndpoint enum che incapsula baseUrl e metadati dell'ambiente target.
     */
    public AlpacaOrdersRestService(AlpacaRestBaseEndpoints desiredEndpoint) {
        super(desiredEndpoint);
    }

    // ================================================================================================================
    // CREATE (POST)  — NON idempotente → nessun enableRetries
    // ================================================================================================================

    /**
     * Crea un nuovo ordine (sincrono).
     *
     * <p><b>Endpoint:</b> {@code POST /v2/orders}</p>
     *
     * <h3>Request (riassunto campi principali)</h3>
     * <ul>
     *   <li><b>symbol</b>: stringa simbolo (es. {@code "AAPL"}, {@code "BTCUSD"}).</li>
     *   <li><b>side</b>: {@code "buy"|"sell"}.</li>
     *   <li><b>type</b>: {@code "market"|"limit"|"stop"|"stop_limit"|"trailing_stop"}.</li>
     *   <li><b>time_in_force</b>: {@code "day"|"gtc"|"opg"|"cls"|"ioc"|"fok"}.</li>
     *   <li><b>qty</b> <i>oppure</i> <b>notional</b>: <u>mutuamente esclusivi</u>, almeno uno richiesto.</li>
     *   <li><b>limit_price</b> / <b>stop_price</b>: in base al tipo.</li>
     *   <li><b>order_class</b>: {@code "simple"|"bracket"|"oco"|"oto"}; per opzioni: {@code "mleg"}.</li>
     *   <li><b>take_profit</b> / <b>stop_loss</b>: per bracket/oco, secondo i tuoi record request.</li>
     *   <li><b>trailing_stop</b>: uno tra {@code trail_price} e {@code trail_percent}.</li>
     *   <li><b>extended_hours</b>: valido solo equities con {@code type=limit} e {@code tif=day}.</li>
     *   <li><b>client_order_id</b>: ≤ 128 caratteri (idempotenza lato client).</li>
     * </ul>
     *
     * <h3>Idempotenza</h3>
     * <p>Operazione non idempotente: <b>nessun</b> parametro di retry. Per deduplicare usi {@code client_order_id}.</p>
     *
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param request payload ordine (non {@code null})
     * @return {@link AlpacaOrderResponse} ordine creato
     * @throws IllegalArgumentException se {@code request} è {@code null}
     * @throws ApcaRestClientException    se HTTP non è OK o errore di trasporto/parsing
     */
    public AlpacaOrderResponse createOrder(String accIdForRateLimitTracking, AlpacaCreateOrderRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        final String url  = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ORDERS_SUFFIX);
        final String body = JsonCodec.toJson(request);

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling POST /v2/orders (sync) | url={}", url);

            HttpResponse<String> resp = this.httpRestClient.sendHttpRequest(
                    url,
                    HttpMethod.POST,
                    /* enableRetries = */ false, // NON idempotente
                    this.alpacaRestConfig.getAuthHeaderParams(),
                    body
            );

            long ms = (System.nanoTime() - t0) / 1_000_000;

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                AlpacaOrderResponse out = JsonCodec.fromJson(resp.body(), AlpacaOrderResponse.class);
                LOG.info("POST /v2/orders OK | status={} | ms={}", resp.statusCode(), ms);
                return out;
            }

            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("POST /v2/orders NOT OK | status={} | ms={} | preview={}", resp.statusCode(), ms, preview);
            throw new ApcaRestClientException("createOrder: HTTP NOT OK: " + resp.statusCode() +
                    " (url=" + url + ", bodyPreview=" + preview + ")");

        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException("createOrder: error calling POST /v2/orders (url=" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Crea un nuovo ordine (asincrono).
     *
     * <p><b>Endpoint:</b> {@code POST /v2/orders}</p>
     * <p>Non idempotente → non espone retry.</p>
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param request payload ordine (non {@code null})
     * @return future completata con {@link AlpacaOrderResponse}
     * @throws IllegalArgumentException se {@code request} è {@code null}
     */
    public CompletableFuture<AlpacaOrderResponse> createOrderAsync(String accIdForRateLimitTracking, AlpacaCreateOrderRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        final String url  = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ORDERS_SUFFIX);
        final String body = JsonCodec.toJson(request);

        LOG.debug("Calling POST /v2/orders (async) | url={}", url);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url,
                        HttpMethod.POST,
                        /* enableRetries = */ false, // NON idempotente
                        this.alpacaRestConfig.getAuthHeaderParams(),
                        body
                )
                .thenApply(resp -> {
                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                        String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException("createOrder (async): HTTP NOT OK: " + resp.statusCode() +
                                " (url=" + url + ", bodyPreview=" + preview + ")");
                    }
                    return JsonCodec.fromJson(resp.body(), AlpacaOrderResponse.class);
                });
    }

    // ================================================================================================================
    // LIST (GET) — idempotente → enableRetries
    // ================================================================================================================

    /**
     * Elenca gli ordini con filtri (sincrono).
     *
     * <p><b>Endpoint:</b> {@code GET /v2/orders}</p>
     *
     * <h3>Filtri</h3>
     * <ul>
     *   <li><b>status</b>: {@code "open"|"closed"|"all"} — se null/blank usa default server</li>
     *   <li><b>limit</b>: intero positivo (tip. max 500) — se null usa default server</li>
     *   <li><b>after</b>/<b>until</b>: {@link Instant} ISO-8601 — se null non applicato</li>
     *   <li><b>direction</b>: {@code "asc"|"desc"}</li>
     *   <li><b>nested</b>: {@code true|false} — include legs se true</li>
     *   <li><b>side</b>: {@code "buy"|"sell"}</li>
     *   <li><b>symbols</b>: lista simboli → CSV in query</li>
     * </ul>
     *
     * <h3>Retry</h3>
     * <p>GET è idempotente → parametro {@code enableRetries} attivo/disattivo la policy del client.</p>
     *
     * @param enableRetries abilita/disabilita policy di retry (idempotente)
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param status        {@code "open"|"closed"|"all"}; null/blank → default server
     * @param limit         intero positivo; null → default server
     * @param after         {@link Instant} ISO-8601; null → non applicato
     * @param until         {@link Instant} ISO-8601; null → non applicato
     * @param direction     {@code "asc"|"desc"}; null/blank → default server
     * @param nested        {@code true|false}; null → non applicato
     * @param side          {@code "buy"|"sell"}; null/blank → non applicato
     * @param symbols       elenco simboli (CSV); null/vuoto → non applicato
     * @return lista <b>immutabile</b> di {@link AlpacaOrderResponse} (mai {@code null})
     * @throws ApcaRestClientException se HTTP non è OK o errore di trasporto/parsing
     */
    public List<AlpacaOrderResponse> getOrders(
            boolean enableRetries,
            String accIdForRateLimitTracking,
            String status,
            Integer limit,
            Instant after,
            Instant until,
            String direction,
            Boolean nested,
            String side,
            List<String> symbols
    ) {
        final Map<String, String> query = buildOrdersQueryParams(status, limit, after, until, direction, nested, side, symbols);
        final String url = HttpUtils.setQueryParams(
                HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ORDERS_SUFFIX),
                query
        );

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling GET /v2/orders (sync) | url={} | retries={}", url, enableRetries);

            HttpResponse<String> resp = this.httpRestClient.sendHttpRequest(
                    url,
                    HttpMethod.GET,
                    enableRetries,
                    this.alpacaRestConfig.getAuthHeaderParams(),
                    "" // GET: nessun body
            );

            long ms = (System.nanoTime() - t0) / 1_000_000;

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                AlpacaOrderResponse[] arr = JsonCodec.fromJson(resp.body(), AlpacaOrderResponse[].class);
                List<AlpacaOrderResponse> out = (arr == null) ? List.of() : List.copyOf(Arrays.asList(arr));
                LOG.info("GET /v2/orders OK | count={} | status={} | ms={}", out.size(), resp.statusCode(), ms);
                return out;
            }

            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("GET /v2/orders NOT OK | status={} | ms={} | preview={}", resp.statusCode(), ms, preview);
            throw new ApcaRestClientException("getOrders: HTTP NOT OK: " + resp.statusCode() +
                    " (url=" + url + ", bodyPreview=" + preview + ")");

        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException("getOrders: error calling GET /v2/orders (url=" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Elenca gli ordini con filtri (asincrono).
     *
     * <p><b>Endpoint:</b> {@code GET /v2/orders}</p>
     *
     * @param enableRetries abilita/disabilita retry (idempotente)
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param status        {@code "open"|"closed"|"all"}; null/blank → default server
     * @param limit         intero positivo; null → default server
     * @param after         {@link Instant} ISO-8601; null → non applicato
     * @param until         {@link Instant} ISO-8601; null → non applicato
     * @param direction     {@code "asc"|"desc"}; null/blank → default server
     * @param nested        {@code true|false}; null → non applicato
     * @param side          {@code "buy"|"sell"}; null/blank → non applicato
     * @param symbols       elenco simboli (CSV); null/vuoto → non applicato
     * @return future con lista <b>immutabile</b> di {@link AlpacaOrderResponse}
     */
    public CompletableFuture<List<AlpacaOrderResponse>> getAsyncOrders(
            boolean enableRetries,
            String accIdForRateLimitTracking,
            String status,
            Integer limit,
            Instant after,
            Instant until,
            String direction,
            Boolean nested,
            String side,
            List<String> symbols
    ) {
        final Map<String, String> query = buildOrdersQueryParams(status, limit, after, until, direction, nested, side, symbols);
        final String url = HttpUtils.setQueryParams(
                HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ORDERS_SUFFIX),
                query
        );

        LOG.debug("Calling GET /v2/orders (async) | url={} | retries={}", url, enableRetries);

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
                        String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException("getOrders (async): HTTP NOT OK: " + resp.statusCode() +
                                " (url=" + url + ", bodyPreview=" + preview + ")");
                    }
                    AlpacaOrderResponse[] arr = JsonCodec.fromJson(resp.body(), AlpacaOrderResponse[].class);
                    return (arr == null) ? List.of() : List.copyOf(Arrays.asList(arr));
                });
    }

    // ================================================================================================================
    // GET by ID / by client_order_id — idempotente → enableRetries ; 404→null
    // ================================================================================================================

    /**
     * Recupera un ordine per <b>order_id</b> (sincrono).
     *
     * <p><b>Endpoint:</b> {@code GET /v2/orders/{order_id}}</p>
     *
     * @param enableRetries abilita/disabilita retry (idempotente)
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param orderId       ID ordine (UUID string) non {@code null}/blank
     * @return              {@link AlpacaOrderResponse} oppure {@code null} se HTTP 404
     * @throws IllegalArgumentException se {@code orderId} è blank/null
     * @throws ApcaRestClientException    se status diverso da 200/404 o errore di trasporto/parsing
     */
    public AlpacaOrderResponse getOrder(boolean enableRetries, String accIdForRateLimitTracking, String orderId) {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        if (orderId.isBlank()) throw new IllegalArgumentException("orderId cannot be blank");

        final String url = HttpUtils.setPathParam(
                HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ORDERS_SUFFIX),
                orderId
        );

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling GET /v2/orders/{id} (sync) | url={} | retries={} | id={}", url, enableRetries, orderId);

            HttpResponse<String> resp = this.httpRestClient.sendHttpRequest(
                    url, HttpMethod.GET, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
            );

            long ms = (System.nanoTime() - t0) / 1_000_000;
            int sc = resp.statusCode();

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
            if (sc == 404) {
                LOG.info("GET /v2/orders/{id} NOT FOUND | id={} | status=404 | ms={}", orderId, ms);
                return null;
            }
            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc)) {
                AlpacaOrderResponse dto = JsonCodec.fromJson(resp.body(), AlpacaOrderResponse.class);
                LOG.info("GET /v2/orders/{id} OK | id={} | status={} | ms={}", orderId, sc, ms);
                return dto;
            }

            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("GET /v2/orders/{id} NOT OK | id={} | status={} | ms={} | preview={}", orderId, sc, ms, preview);
            throw new ApcaRestClientException("getOrder: HTTP NOT OK: " + sc +
                    " (url=" + url + ", bodyPreview=" + preview + ")");

        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException("getOrder: error calling GET /v2/orders/{id} (url=" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Recupera un ordine per <b>order_id</b> (asincrono).
     *
     * @param enableRetries abilita/disabilita retry (idempotente)
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param orderId       ID ordine (UUID string) non {@code null}/blank
     * @return              future con {@link AlpacaOrderResponse} oppure {@code null} se 404
     * @throws IllegalArgumentException se {@code orderId} è blank/null
     */
    public CompletableFuture<AlpacaOrderResponse> getAsyncOrder(boolean enableRetries, String accIdForRateLimitTracking, String orderId) {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        if (orderId.isBlank()) throw new IllegalArgumentException("orderId cannot be blank");

        final String url = HttpUtils.setPathParam(
                HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ORDERS_SUFFIX),
                orderId
        );

        LOG.debug("Calling GET /v2/orders/{id} (async) | url={} | retries={} | id={}", url, enableRetries, orderId);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url, HttpMethod.GET, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
                )
                .thenApply(resp -> {
                    final int sc = resp.statusCode();
                    if (sc == 404) {
                        LOG.info("GET /v2/orders/{id} NOT FOUND (async) | id={} | status=404", orderId);
                        return null;
                    }
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc)) {
                        String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException("getOrder (async): HTTP NOT OK: " + sc +
                                " (url=" + url + ", bodyPreview=" + preview + ")");
                    }
                    return JsonCodec.fromJson(resp.body(), AlpacaOrderResponse.class);
                });
    }

    /**
     * Recupera un ordine per <b>client_order_id</b> (sincrono).
     *
     * <p><b>Endpoint:</b> {@code GET /v2/orders:by_client_order_id?client_order_id=...}</p>
     *
     * @param enableRetries  abilita/disabilita retry (idempotente)
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param clientOrderId  stringa non vuota; lunghezza ≤ 128
     * @return               {@link AlpacaOrderResponse} oppure {@code null} se HTTP 404
     * @throws IllegalArgumentException se {@code clientOrderId} è blank/null
     * @throws ApcaRestClientException    se status diverso da 200/404 o errore di trasporto/parsing
     */
    public AlpacaOrderResponse getOrderByClientOrderId(boolean enableRetries, String accIdForRateLimitTracking, String clientOrderId) {
        Objects.requireNonNull(clientOrderId, "clientOrderId cannot be null");
        if (clientOrderId.isBlank()) throw new IllegalArgumentException("clientOrderId cannot be blank");

        final Map<String, String> query = Map.of("client_order_id", clientOrderId);
        final String url = HttpUtils.setQueryParams(
                HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ORDERS_BY_CLIENT_ID_SUFFIX),
                query
        );

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling GET /v2/orders:by_client_order_id (sync) | url={} | retries={} | client_order_id={}",
                    url, enableRetries, clientOrderId);

            HttpResponse<String> resp = this.httpRestClient.sendHttpRequest(
                    url, HttpMethod.GET, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
            );

            long ms = (System.nanoTime() - t0) / 1_000_000;
            int sc = resp.statusCode();

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);

            if (sc == 404) {
                LOG.info("GET /v2/orders:by_client_order_id NOT FOUND | client_order_id={} | status=404 | ms={}",
                        clientOrderId, ms);
                return null;
            }
            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc)) {
                AlpacaOrderResponse dto = JsonCodec.fromJson(resp.body(), AlpacaOrderResponse.class);
                LOG.info("GET /v2/orders:by_client_order_id OK | client_order_id={} | status={} | ms={}",
                        clientOrderId, sc, ms);
                return dto;
            }

            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("GET /v2/orders:by_client_order_id NOT OK | client_order_id={} | status={} | ms={} | preview={}",
                    clientOrderId, sc, ms, preview);
            throw new ApcaRestClientException("getOrderByClientOrderId: HTTP NOT OK: " + sc +
                    " (url=" + url + ", bodyPreview=" + preview + ")");

        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException("getOrderByClientOrderId: error calling GET /v2/orders:by_client_order_id (url=" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Recupera un ordine per <b>client_order_id</b> (asincrono).
     *
     * @param enableRetries  abilita/disabilita retry (idempotente)
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param clientOrderId  stringa non vuota; lunghezza ≤ 128
     * @return               future con {@link AlpacaOrderResponse} oppure {@code null} se 404
     * @throws IllegalArgumentException se {@code clientOrderId} è blank/null
     */
    public CompletableFuture<AlpacaOrderResponse> getAsyncOrderByClientOrderId(boolean enableRetries, String accIdForRateLimitTracking, String clientOrderId) {
        Objects.requireNonNull(clientOrderId, "clientOrderId cannot be null");
        if (clientOrderId.isBlank()) throw new IllegalArgumentException("clientOrderId cannot be blank");

        final Map<String, String> query = Map.of("client_order_id", clientOrderId);
        final String url = HttpUtils.setQueryParams(
                HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ORDERS_BY_CLIENT_ID_SUFFIX),
                query
        );

        LOG.debug("Calling GET /v2/orders:by_client_order_id (async) | url={} | retries={} | client_order_id={}",
                url, enableRetries, clientOrderId);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url, HttpMethod.GET, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
                )
                .thenApply(resp -> {
                    final int sc = resp.statusCode();

                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);

                    if (sc == 404) {
                        LOG.info("GET /v2/orders:by_client_order_id NOT FOUND (async) | client_order_id={} | status=404",
                                clientOrderId);
                        return null;
                    }
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc)) {
                        String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException("getOrderByClientOrderId (async): HTTP NOT OK: " + sc +
                                " (url=" + url + ", bodyPreview=" + preview + ")");
                    }
                    return JsonCodec.fromJson(resp.body(), AlpacaOrderResponse.class);
                });
    }

    // ================================================================================================================
    // REPLACE (PATCH) — NON idempotente → nessun enableRetries
    // ================================================================================================================

    /**
     * Sostituisce (replace) un ordine aperto (sincrono).
     *
     * <p><b>Endpoint:</b> {@code PATCH /v2/orders/{order_id}}</p>
     *
     * <h3>Campi tipici consentiti nel replace</h3>
     * <ul>
     *   <li><b>qty</b> (decimale positivo)</li>
     *   <li><b>time_in_force</b>: {@code "day"|"gtc"|"opg"|"cls"|"ioc"|"fok"}</li>
     *   <li><b>limit_price</b>, <b>stop_price</b></li>
     *   <li><b>trail_price</b> XOR <b>trail_percent</b> (uno solo)</li>
     *   <li><b>client_order_id</b> ≤ 128</li>
     * </ul>
     *
     * <h3>Idempotenza</h3>
     * <p>PATCH è operazione non idempotente → nessun retry esposto.</p>
     *
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param orderId ID ordine da sostituire (UUID string) non {@code null}/blank
     * @param request payload di replace (non {@code null})
     * @return        {@link AlpacaOrderResponse} ordine risultante (nuovo ID)
     * @throws IllegalArgumentException se {@code orderId} è blank/null o {@code request} null
     * @throws ApcaRestClientException    se HTTP non è OK o errore di trasporto/parsing
     */
    public AlpacaOrderResponse replaceOrder(String accIdForRateLimitTracking, String orderId, AlpacaReplaceOrderRequest request) {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        if (orderId.isBlank()) throw new IllegalArgumentException("orderId cannot be blank");
        Objects.requireNonNull(request, "request cannot be null");

        final String url  = HttpUtils.setPathParam(HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ORDERS_SUFFIX), orderId);
        final String body = JsonCodec.toJson(request);

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling PATCH /v2/orders/{id} (sync) | url={} | id={}", url, orderId);

            HttpResponse<String> resp = this.httpRestClient.sendHttpRequest(
                    url, HttpMethod.PATCH, /* enableRetries = */ false, this.alpacaRestConfig.getAuthHeaderParams(), body
            );

            long ms = (System.nanoTime() - t0) / 1_000_000;

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);

            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                AlpacaOrderResponse out = JsonCodec.fromJson(resp.body(), AlpacaOrderResponse.class);
                LOG.info("PATCH /v2/orders/{id} OK | id={} | status={} | ms={}", orderId, resp.statusCode(), ms);
                return out;
            }

            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("PATCH /v2/orders/{id} NOT OK | id={} | status={} | ms={} | preview={}",
                    orderId, resp.statusCode(), ms, preview);
            throw new ApcaRestClientException("replaceOrder: HTTP NOT OK: " + resp.statusCode() +
                    " (url=" + url + ", bodyPreview=" + preview + ")");

        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException("replaceOrder: error calling PATCH /v2/orders/{id} (url=" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Sostituisce (replace) un ordine aperto (asincrono).
     *
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param orderId ID ordine da sostituire (UUID string) non {@code null}/blank
     * @param request payload di replace (non {@code null})
     * @return        future completata con {@link AlpacaOrderResponse}
     * @throws IllegalArgumentException se {@code orderId} è blank/null o {@code request} null
     */
    public CompletableFuture<AlpacaOrderResponse> replaceOrderAsync(String accIdForRateLimitTracking, String orderId, AlpacaReplaceOrderRequest request) {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        if (orderId.isBlank()) throw new IllegalArgumentException("orderId cannot be blank");
        Objects.requireNonNull(request, "request cannot be null");

        final String url  = HttpUtils.setPathParam(HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ORDERS_SUFFIX), orderId);
        final String body = JsonCodec.toJson(request);

        LOG.debug("Calling PATCH /v2/orders/{id} (async) | url={} | id={}", url, orderId);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url, HttpMethod.PATCH, /* enableRetries = */ false, this.alpacaRestConfig.getAuthHeaderParams(), body
                )
                .thenApply(resp -> {
                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                        String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException("replaceOrder (async): HTTP NOT OK: " + resp.statusCode() +
                                " (url=" + url + ", bodyPreview=" + preview + ")");
                    }
                    return JsonCodec.fromJson(resp.body(), AlpacaOrderResponse.class);
                });
    }

    // ================================================================================================================
    // CANCEL ALL / CANCEL BY ID (DELETE) — idempotente → enableRetries
    // ================================================================================================================

    /**
     * Cancella <b>tutti</b> gli ordini aperti (sincrono).
     *
     * <p><b>Endpoint:</b> {@code DELETE /v2/orders}</p>
     * <p>La risposta è un array di esiti per ordine ({@link AlpacaDeleteAllOrdersResponse}).</p>
     *
     * @param enableRetries abilita/disabilita retry (idempotente)
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @return lista <b>immutabile</b> di {@link AlpacaDeleteAllOrdersResponse}; mai {@code null}
     * @throws ApcaRestClientException se HTTP non è OK o errore di trasporto/parsing
     */
    public List<AlpacaDeleteAllOrdersResponse> cancelAllOrders(boolean enableRetries, String accIdForRateLimitTracking) {
        final String url = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ORDERS_SUFFIX);

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling DELETE /v2/orders (sync) | url={} | retries={}", url, enableRetries);

            HttpResponse<String> resp = this.httpRestClient.sendHttpRequest(
                    url, HttpMethod.DELETE, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
            );

            long ms = (System.nanoTime() - t0) / 1_000_000;

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);

            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode()) || resp.statusCode()==207) {
                AlpacaDeleteAllOrdersResponse[] arr = JsonCodec.fromJson(resp.body(), AlpacaDeleteAllOrdersResponse[].class);
                List<AlpacaDeleteAllOrdersResponse> out = (arr == null) ? List.of() : List.copyOf(Arrays.asList(arr));
                LOG.info("DELETE /v2/orders OK | returned={} | status={} | ms={}", out.size(), resp.statusCode(), ms);
                return out;
            }

            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("DELETE /v2/orders NOT OK | status={} | ms={} | preview={}", resp.statusCode(), ms, preview);
            throw new ApcaRestClientException("cancelAllOrders: HTTP NOT OK: " + resp.statusCode() +
                    " (url=" + url + ", bodyPreview=" + preview + ")");

        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException("cancelAllOrders: error calling DELETE /v2/orders (url=" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Cancella <b>tutti</b> gli ordini aperti (asincrono).
     *
     * @param enableRetries abilita/disabilita retry (idempotente)
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently

     * @return future con lista <b>immutabile</b> di {@link AlpacaDeleteAllOrdersResponse}
     */
    public CompletableFuture<List<AlpacaDeleteAllOrdersResponse>> cancelAllOrdersAsync(boolean enableRetries, String accIdForRateLimitTracking) {
        final String url = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ORDERS_SUFFIX);

        LOG.debug("Calling DELETE /v2/orders (async) | url={} | retries={}", url, enableRetries);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url, HttpMethod.DELETE, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
                )
                .thenApply(resp -> {
                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                        String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException("cancelAllOrders (async): HTTP NOT OK: " + resp.statusCode() +
                                " (url=" + url + ", bodyPreview=" + preview + ")");
                    }
                    AlpacaDeleteAllOrdersResponse[] arr = JsonCodec.fromJson(resp.body(), AlpacaDeleteAllOrdersResponse[].class);
                    return (arr == null) ? List.of() : List.copyOf(Arrays.asList(arr));
                });
    }

    /**
     * Cancella un singolo ordine per ID (sincrono).
     *
     * <p><b>Endpoint:</b> {@code DELETE /v2/orders/{order_id}}</p>
     * <ul>
     *   <li><b>204 No Content</b> → cancellazione accettata → ritorna {@code true}</li>
     *   <li><b>404 Not Found</b> → ordine inesistente → ritorna {@code false}</li>
     *   <li>Altri status (es. <b>422</b> non più cancellabile) → {@link ApcaRestClientException}</li>
     * </ul>
     *
     * @param enableRetries abilita/disabilita retry (idempotente)
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param orderId       ID ordine (UUID string) non {@code null}/blank
     * @return              {@code true} se 204, {@code false} se 404
     * @throws IllegalArgumentException se {@code orderId} è blank/null
     * @throws ApcaRestClientException    per status diversi da {204,404} o errori di trasporto
     */
    public boolean cancelOrder(boolean enableRetries, String accIdForRateLimitTracking, String orderId) {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        if (orderId.isBlank()) throw new IllegalArgumentException("orderId cannot be blank");

        final String url = HttpUtils.setPathParam(
                HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ORDERS_SUFFIX),
                orderId
        );

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling DELETE /v2/orders/{id} (sync) | url={} | retries={} | id={}", url, enableRetries, orderId);

            HttpResponse<String> resp = this.httpRestClient.sendHttpRequest(
                    url, HttpMethod.DELETE, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
            );

            long ms = (System.nanoTime() - t0) / 1_000_000;
            int sc = resp.statusCode();

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);

            if (sc == 204) {
                LOG.info("DELETE /v2/orders/{id} ACCEPTED | id={} | status=204 | ms={}", orderId, ms);
                return true;
            }
            if (sc == 404) {
                LOG.info("DELETE /v2/orders/{id} NOT FOUND | id={} | status=404 | ms={}", orderId, ms);
                return false;
            }

            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("DELETE /v2/orders/{id} NOT OK | id={} | status={} | ms={} | preview={}", orderId, sc, ms, preview);
            throw new ApcaRestClientException("cancelOrder: HTTP NOT OK: " + sc +
                    " (url=" + url + ", bodyPreview=" + preview + ")");

        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException("cancelOrder: error calling DELETE /v2/orders/{id} (url=" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Cancella un singolo ordine per ID (asincrono).
     *
     * @param enableRetries abilita/disabilita retry (idempotente)
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param orderId       ID ordine (UUID string) non {@code null}/blank
     * @return              future con {@code true} se 204, {@code false} se 404
     * @throws IllegalArgumentException se {@code orderId} è blank/null
     */
    public CompletableFuture<Boolean> cancelOrderAsync(boolean enableRetries, String accIdForRateLimitTracking, String orderId) {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        if (orderId.isBlank()) throw new IllegalArgumentException("orderId cannot be blank");

        final String url = HttpUtils.setPathParam(
                HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ORDERS_SUFFIX),
                orderId
        );

        LOG.debug("Calling DELETE /v2/orders/{id} (async) | url={} | retries={} | id={}", url, enableRetries, orderId);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url, HttpMethod.DELETE, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
                )
                .thenApply(resp -> {
                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);

                    int sc = resp.statusCode();
                    if (sc == 204) return true;
                    if (sc == 404) return false;

                    String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                    throw new ApcaRestClientException("cancelOrder (async): HTTP NOT OK: " + sc +
                            " (url=" + url + ", bodyPreview=" + preview + ")");
                });
    }

    // ================================================================================================================
    // Helpers privati
    // ================================================================================================================

    /**
     * Costruisce la mappa dei query param per {@code GET /v2/orders}, filtrando null/blank e normalizzando le liste.
     * <ul>
     *   <li>status → {@code "open"|"closed"|"all"}</li>
     *   <li>limit → intero positivo (toString)</li>
     *   <li>after/until → {@link Instant#toString()} (ISO-8601)</li>
     *   <li>direction → {@code "asc"|"desc"}</li>
     *   <li>nested → {@code "true"|"false"}</li>
     *   <li>side → {@code "buy"|"sell"}</li>
     *   <li>symbols → CSV via {@link HttpUtils#normalizeListForQueryParams(List)}</li>
     * </ul>
     */
    private Map<String, String> buildOrdersQueryParams(
            String status,
            Integer limit,
            Instant after,
            Instant until,
            String direction,
            Boolean nested,
            String side,
            List<String> symbols
    ) {
        Map<String, String> q = new HashMap<>();

        if (status != null && !status.isBlank()) {
            q.put("status", status);
        }
        if (limit != null) {
            q.put("limit", String.valueOf(limit));
        }
        if (after != null) {
            q.put("after", after.toString());
        }
        if (until != null) {
            q.put("until", until.toString());
        }
        if (direction != null && !direction.isBlank()) {
            q.put("direction", direction);
        }
        if (nested != null) {
            q.put("nested", nested ? "true" : "false");
        }
        if (side != null && !side.isBlank()) {
            q.put("side", side);
        }
        if (symbols != null && !symbols.isEmpty()) {
            String csv = HttpUtils.normalizeListForQueryParams(symbols);
            if (!csv.isBlank()) {
                q.put("symbols", csv);
            }
        }
        return q;
        // Nota: Se in futuro Alpaca introducesse pagination (es. page_token), estendere qui.
    }
}
