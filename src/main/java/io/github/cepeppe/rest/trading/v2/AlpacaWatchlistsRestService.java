package io.github.cepeppe.rest.trading.v2;

import io.github.cepeppe.Constants;
import io.github.cepeppe.exception.ApcaRestClientException;
import io.github.cepeppe.http.HttpMethod;
import io.github.cepeppe.json.JsonCodec;
import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.AlpacaRestService;
import io.github.cepeppe.rest.data.request.AlpacaCreateWatchlistRequest;
import io.github.cepeppe.rest.data.response.AlpacaWatchlistResponse;
import io.github.cepeppe.rest.limits.AlpacaRateLimitMultiton;
import io.github.cepeppe.utils.HttpUtils;

import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * <h1>AlpacaWatchlistsRestService</h1>
 *
 * Adapter REST per gli endpoint <b>Trading API v2</b> relativi alle <b>watchlists</b>.
 * Copre:
 * <ul>
 *   <li><code>GET /v2/watchlists</code> – elenco watchlists dell’account.</li>
 *   <li><code>POST /v2/watchlists</code> – creazione watchlist (name + symbols).</li>
 *   <li><code>GET /v2/watchlists/{id}</code> – dettaglio per ID (404 → {@code null}).</li>
 *   <li><code>DELETE /v2/watchlists/{id}</code> – cancellazione per ID (idempotente).</li>
 *   <li><code>GET /v2/watchlists:by_name?name=...</code> – dettaglio per nome (404 → {@code null}).</li>
 *   <li><code>DELETE /v2/watchlists:by_name?name=...</code> – cancellazione per nome (idempotente).</li>
 *   <li><code>DELETE /v2/watchlists/{id}/{symbol}</code> – rimozione di un simbolo dalla watchlist (ritorna la watchlist aggiornata).</li>
 * </ul>
 *
 * <p><b>Retry policy</b>:
 * <ul>
 *   <li>Retry opzionale solo su metodi <i>idempotenti</i> (GET/DELETE). Il client lo fa già rispettando {@link HttpMethod#isIdempotent()}.</li>
 *   <li>Sulle POST <b>forziamo</b> <code>enableRetries=false</code> lato servizio, ignorando eventuali richieste di retry.</li>
 * </ul>
 *
 * <p><b>Thread-safety</b>: la classe è stateless (config immutabile ereditata); sicura in concorrenza.</p>
 *
 * <p><b>Docs Alpaca</b>: Get All / By Name / Delete Symbol / Delete By Id/Name.</p>
 */
public class AlpacaWatchlistsRestService extends AlpacaRestService {

    /** Path base: /v2/<b>watchlists</b>. */
    private static final String WATCHLISTS_SUFFIX = "watchlists";
    /** Path alternativo per operazioni by-name: /v2/<b>watchlists:by_name</b>. */
    private static final String WATCHLISTS_BY_NAME_SUFFIX = "watchlists:by_name";

    /** Anteprima massima del body nei log/error. */
    private static final int BODY_PREVIEW_LEN = 256;

    /** Logger di progetto. */
    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(AlpacaWatchlistsRestService.class);

    public AlpacaWatchlistsRestService(AlpacaRestBaseEndpoints desiredEndpoint) {
        super(desiredEndpoint);
    }

    // ========================================================================
    // GET /v2/watchlists — List
    // ========================================================================

    /**
     * Recupera l'elenco delle watchlists dell'account (sincrono).
     *
     * @param enableRetries abilita/disabilita le policy di retry (consentito perché GET idempotente)
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @return lista <b>immutabile</b> di watchlists (mai {@code null})
     * @throws ApcaRestClientException per status non-OK o errori di I/O/parsing
     */
    public List<AlpacaWatchlistResponse> getWatchlists(boolean enableRetries, String accIdForRateLimitTracking) {

        final String url = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), WATCHLISTS_SUFFIX);

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling GET /v2/watchlists (sync) | url={} | retries={}", url, enableRetries);

            HttpResponse<String> resp = this.httpRestClient.sendHttpRequest(
                    url, HttpMethod.GET, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
            );

            long ms = (System.nanoTime() - t0) / 1_000_000;

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);

            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                AlpacaWatchlistResponse[] arr = JsonCodec.fromJson(resp.body(), AlpacaWatchlistResponse[].class);
                List<AlpacaWatchlistResponse> out = (arr == null) ? List.of() : List.copyOf(Arrays.asList(arr));
                LOG.info("GET /v2/watchlists OK | count={} | status={} | ms={}", out.size(), resp.statusCode(), ms);
                return out;
            }

            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("GET /v2/watchlists NOT OK | status={} | ms={} | preview={}", resp.statusCode(), ms, preview);
            throw new ApcaRestClientException(
                    "getWatchlists: HTTP status NOT OK: " + resp.statusCode() +
                            " (url=" + url + ", bodyPreview=" + preview + ")"
            );

        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException("getWatchlists: error calling /v2/watchlists (url=" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Recupera l'elenco delle watchlists dell'account (asincrono).
     *
     * @param enableRetries abilita/disabilita le policy di retry (consentito perché GET idempotente)
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @return future completato con lista <b>immutabile</b> (mai {@code null})
     */
    public CompletableFuture<List<AlpacaWatchlistResponse>> getAsyncWatchlists(boolean enableRetries, String accIdForRateLimitTracking) {

        final String url = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), WATCHLISTS_SUFFIX);

        LOG.debug("Calling GET /v2/watchlists (async) | url={} | retries={}", url, enableRetries);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url, HttpMethod.GET, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
                )
                .thenApply(resp -> {
                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                        String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException(
                                "getWatchlists (async): HTTP status NOT OK: " + resp.statusCode() +
                                        " (url=" + url + ", bodyPreview=" + preview + ")"
                        );
                    }
                    return resp.body();
                })
                .thenApply(body -> {
                    AlpacaWatchlistResponse[] arr = JsonCodec.fromJson(body, AlpacaWatchlistResponse[].class);
                    return (arr == null) ? List.of() : List.copyOf(Arrays.asList(arr));
                });
    }

    // ========================================================================
    // POST /v2/watchlists — Create (NON idempotente → retry disabilitato)
    // ========================================================================

    /**
     * Crea una nuova watchlist (sincrono).
     * <p><b>Retry:</b> disabilitato forzatamente in quanto POST non idempotente.</p>
     *
     * @param enableRetries ignorato (forzato a {@code false})
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param request       body { name, symbols[] }; {@code name} non blank
     * @return la watchlist creata
     */
    public AlpacaWatchlistResponse createWatchlist(boolean enableRetries, String accIdForRateLimitTracking, AlpacaCreateWatchlistRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("request.name cannot be null/blank");
        }

        final String url = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), WATCHLISTS_SUFFIX);
        final String bodyJson = JsonCodec.toJson(request);

        long t0 = System.nanoTime();
        try {
            if (enableRetries) {
                LOG.warn("createWatchlist: retries requested but forbidden for POST; forcing retries=false");
            }
            LOG.debug("Calling POST /v2/watchlists (sync) | url={} | retries=false | name={}", url, request.name());

            HttpResponse<String> resp = this.httpRestClient.sendHttpRequest(
                    url, HttpMethod.POST, /*force*/ false, this.alpacaRestConfig.getAuthHeaderParams(), bodyJson
            );

            long ms = (System.nanoTime() - t0) / 1_000_000;

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);

            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                AlpacaWatchlistResponse out = JsonCodec.fromJson(resp.body(), AlpacaWatchlistResponse.class);
                LOG.info("POST /v2/watchlists OK | id={} | status={} | ms={}", (out != null ? out.id() : "n/a"), resp.statusCode(), ms);
                return out;
            }

            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("POST /v2/watchlists NOT OK | status={} | ms={} | preview={}", resp.statusCode(), ms, preview);
            throw new ApcaRestClientException(
                    "createWatchlist: HTTP status NOT OK: " + resp.statusCode() +
                            " (url=" + url + ", bodyPreview=" + preview + ")"
            );

        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException("createWatchlist: error calling POST /v2/watchlists (url=" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Crea una nuova watchlist (asincrono).
     * <p><b>Retry:</b> disabilitato forzatamente (POST non idempotente).</p>
     *
     * @param enableRetries ignorato (forzato a {@code false})
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param request       body { name, symbols[] }
     * @return future con la watchlist creata
     */
    public CompletableFuture<AlpacaWatchlistResponse> createAsyncWatchlist(boolean enableRetries, String accIdForRateLimitTracking, AlpacaCreateWatchlistRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("request.name cannot be null/blank");
        }

        final String url = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), WATCHLISTS_SUFFIX);
        final String bodyJson = JsonCodec.toJson(request);

        if (enableRetries) {
            LOG.warn("createAsyncWatchlist: retries requested but forbidden for POST; forcing retries=false");
        }
        LOG.debug("Calling POST /v2/watchlists (async) | url={} | retries=false | name={}", url, request.name());

        return this.httpRestClient.sendAsyncHttpRequest(
                        url, HttpMethod.POST, /*force*/ false, this.alpacaRestConfig.getAuthHeaderParams(), bodyJson
                )
                .thenApply(resp -> {
                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                        String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException(
                                "createWatchlist (async): HTTP status NOT OK: " + resp.statusCode() +
                                        " (url=" + url + ", bodyPreview=" + preview + ")"
                        );
                    }
                    return resp.body();
                })
                .thenApply(body -> JsonCodec.fromJson(body, AlpacaWatchlistResponse.class));
    }

    // ========================================================================
    // GET /v2/watchlists/{id} — Single (404 → null)
    // ========================================================================
    /**
     * Recupera una watchlist per ID (sincrono).
     * <p><b>404</b> → ritorna {@code null}.</p>
     */
    public AlpacaWatchlistResponse getWatchlistById(boolean enableRetries, String accIdForRateLimitTracking, String watchlistId) {
        Objects.requireNonNull(watchlistId, "watchlistId cannot be null");
        if (watchlistId.isBlank()) throw new IllegalArgumentException("watchlistId cannot be blank");

        final String url = HttpUtils.setPathParam(
                HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), WATCHLISTS_SUFFIX), watchlistId
        );

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling GET /v2/watchlists/{id} (sync) | url={} | retries={} | id={}",
                    url, enableRetries, watchlistId);

            HttpResponse<String> resp = this.httpRestClient.sendHttpRequest(
                    url, HttpMethod.GET, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
            );

            long ms = (System.nanoTime() - t0) / 1_000_000;
            int sc = resp.statusCode();

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);

            if (sc == 404) {
                LOG.info("GET /v2/watchlists/{id} NOT FOUND | id={} | status=404 | ms={}", watchlistId, ms);
                return null;
            }
            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc)) {
                AlpacaWatchlistResponse out = JsonCodec.fromJson(resp.body(), AlpacaWatchlistResponse.class);
                LOG.info("GET /v2/watchlists/{id} OK | id={} | status={} | ms={}", watchlistId, sc, ms);
                return out;
            }

            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("GET /v2/watchlists/{id} NOT OK | id={} | status={} | ms={} | preview={}", watchlistId, sc, ms, preview);
            throw new ApcaRestClientException(
                    "getWatchlistById: HTTP status NOT OK: " + sc + " (url=" + url + ", bodyPreview=" + preview + ")"
            );

        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException("getWatchlistById: error calling GET /v2/watchlists/{id} (url=" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Recupera una watchlist per ID (asincrono).
     * <p><b>404</b> → future completata con {@code null}.</p>
     */
    public CompletableFuture<AlpacaWatchlistResponse> getAsyncWatchlistById(boolean enableRetries, String accIdForRateLimitTracking, String watchlistId) {
        Objects.requireNonNull(watchlistId, "watchlistId cannot be null");
        if (watchlistId.isBlank()) throw new IllegalArgumentException("watchlistId cannot be blank");

        final String url = HttpUtils.setPathParam(
                HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), WATCHLISTS_SUFFIX), watchlistId
        );

        LOG.debug("Calling GET /v2/watchlists/{id} (async) | url={} | retries={} | id={}",
                url, enableRetries, watchlistId);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url, HttpMethod.GET, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
                )
                .thenApply(resp -> {
                    int sc = resp.statusCode();
                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);

                    if (sc == 404) {
                        LOG.info("GET /v2/watchlists/{id} NOT FOUND (async) | id={} | status=404", watchlistId);
                        return null;
                    }
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc)) {
                        String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException(
                                "getWatchlistById (async): HTTP status NOT OK: " + sc +
                                        " (url=" + url + ", bodyPreview=" + preview + ")"
                        );
                    }
                    return resp.body();
                })
                .thenApply(body -> (body == null) ? null : JsonCodec.fromJson(body, AlpacaWatchlistResponse.class));
    }

    // ========================================================================
    // DELETE /v2/watchlists/{id} — Delete by ID (idempotente)
    // ========================================================================

    /**
     * Cancella una watchlist per ID (sincrono).
     *
     * @return {@code true} se lo status è considerato OK.
     */
    public boolean deleteWatchlistById(boolean enableRetries, String accIdForRateLimitTracking, String watchlistId) {
        Objects.requireNonNull(watchlistId, "watchlistId cannot be null");
        if (watchlistId.isBlank()) throw new IllegalArgumentException("watchlistId cannot be blank");

        final String url = HttpUtils.setPathParam(
                HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), WATCHLISTS_SUFFIX), watchlistId
        );

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling DELETE /v2/watchlists/{id} (sync) | url={} | retries={} | id={}", url, enableRetries, watchlistId);

            HttpResponse<String> resp = this.httpRestClient.sendHttpRequest(
                    url, HttpMethod.DELETE, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
            );

            long ms = (System.nanoTime() - t0) / 1_000_000;

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);

            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                LOG.info("DELETE /v2/watchlists/{id} OK | id={} | status={} | ms={}", watchlistId, resp.statusCode(), ms);
                return true;
            }

            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("DELETE /v2/watchlists/{id} NOT OK | id={} | status={} | ms={} | preview={}", watchlistId, resp.statusCode(), ms, preview);
            throw new ApcaRestClientException(
                    "deleteWatchlistById: HTTP status NOT OK: " + resp.statusCode() +
                            " (url=" + url + ", bodyPreview=" + preview + ")"
            );

        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException("deleteWatchlistById: error calling DELETE /v2/watchlists/{id} (url=" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Cancella una watchlist per ID (asincrono).
     */
    public CompletableFuture<Boolean> deleteAsyncWatchlistById(boolean enableRetries, String accIdForRateLimitTracking, String watchlistId) {
        Objects.requireNonNull(watchlistId, "watchlistId cannot be null");
        if (watchlistId.isBlank()) throw new IllegalArgumentException("watchlistId cannot be blank");

        final String url = HttpUtils.setPathParam(
                HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), WATCHLISTS_SUFFIX), watchlistId
        );

        LOG.debug("Calling DELETE /v2/watchlists/{id} (async) | url={} | retries={} | id={}", url, enableRetries, watchlistId);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url, HttpMethod.DELETE, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
                )
                .thenApply(resp -> {
                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                        String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException(
                                "deleteWatchlistById (async): HTTP status NOT OK: " + resp.statusCode() +
                                        " (url=" + url + ", bodyPreview=" + preview + ")"
                        );
                    }
                    return true;
                });
    }

    // ========================================================================
    // GET /v2/watchlists:by_name?name=... — Single (404 → null)
    // ========================================================================

    /**
     * Recupera una watchlist per nome (sincrono). 404 → {@code null}.
     */
    public AlpacaWatchlistResponse getWatchlistByName(boolean enableRetries, String accIdForRateLimitTracking, String name) {
        Objects.requireNonNull(name, "name cannot be null");
        if (name.isBlank()) throw new IllegalArgumentException("name cannot be blank");

        final String base = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), WATCHLISTS_BY_NAME_SUFFIX);
        final Map<String,String> query = Map.of("name", name);
        final String url = HttpUtils.setQueryParams(base, query);

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling GET /v2/watchlists:by_name (sync) | url={} | retries={} | name={}", url, enableRetries, name);

            HttpResponse<String> resp = this.httpRestClient.sendHttpRequest(
                    url, HttpMethod.GET, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
            );

            long ms = (System.nanoTime() - t0) / 1_000_000;
            int sc = resp.statusCode();

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);

            if (sc == 404) {
                LOG.info("GET /v2/watchlists:by_name NOT FOUND | name={} | status=404 | ms={}", name, ms);
                return null;
            }
            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc)) {
                AlpacaWatchlistResponse out = JsonCodec.fromJson(resp.body(), AlpacaWatchlistResponse.class);
                LOG.info("GET /v2/watchlists:by_name OK | name={} | status={} | ms={}", name, sc, ms);
                return out;
            }

            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("GET /v2/watchlists:by_name NOT OK | name={} | status={} | ms={} | preview={}", name, sc, ms, preview);
            throw new ApcaRestClientException(
                    "getWatchlistByName: HTTP status NOT OK: " + sc +
                            " (url=" + url + ", bodyPreview=" + preview + ")"
            );

        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException("getWatchlistByName: error calling GET /v2/watchlists:by_name (url=" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Recupera una watchlist per nome (asincrono). 404 → {@code null}.
     */
    public CompletableFuture<AlpacaWatchlistResponse> getAsyncWatchlistByName(boolean enableRetries, String accIdForRateLimitTracking, String name) {
        Objects.requireNonNull(name, "name cannot be null");
        if (name.isBlank()) throw new IllegalArgumentException("name cannot be blank");

        final String base = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), WATCHLISTS_BY_NAME_SUFFIX);
        final String url = HttpUtils.setQueryParams(base, Map.of("name", name));

        LOG.debug("Calling GET /v2/watchlists:by_name (async) | url={} | retries={} | name={}", url, enableRetries, name);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url, HttpMethod.GET, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
                )
                .thenApply(resp -> {
                    int sc = resp.statusCode();
                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
                    if (sc == 404) {
                        LOG.info("GET /v2/watchlists:by_name NOT FOUND (async) | name={} | status=404", name);
                        return null;
                    }
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc)) {
                        String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException(
                                "getWatchlistByName (async): HTTP status NOT OK: " + sc +
                                        " (url=" + url + ", bodyPreview=" + preview + ")"
                        );
                    }
                    return resp.body();
                })
                .thenApply(body -> (body == null) ? null : JsonCodec.fromJson(body, AlpacaWatchlistResponse.class));
    }

    // ========================================================================
    // DELETE /v2/watchlists:by_name?name=... — Delete by name (idempotente)
    // ========================================================================

    /**
     * Cancella una watchlist per nome (sincrono).
     * @return {@code true} se lo status è considerato OK.
     */
    public boolean deleteWatchlistByName(boolean enableRetries, String accIdForRateLimitTracking, String name) {
        Objects.requireNonNull(name, "name cannot be null");
        if (name.isBlank()) throw new IllegalArgumentException("name cannot be blank");

        final String base = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), WATCHLISTS_BY_NAME_SUFFIX);
        final String url  = HttpUtils.setQueryParams(base, Map.of("name", name));

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling DELETE /v2/watchlists:by_name (sync) | url={} | retries={} | name={}", url, enableRetries, name);

            HttpResponse<String> resp = this.httpRestClient.sendHttpRequest(
                    url, HttpMethod.DELETE, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
            );

            long ms = (System.nanoTime() - t0) / 1_000_000;

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);

            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                LOG.info("DELETE /v2/watchlists:by_name OK | name={} | status={} | ms={}", name, resp.statusCode(), ms);
                return true;
            }

            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("DELETE /v2/watchlists:by_name NOT OK | name={} | status={} | ms={} | preview={}", name, resp.statusCode(), ms, preview);
            throw new ApcaRestClientException(
                    "deleteWatchlistByName: HTTP status NOT OK: " + resp.statusCode() +
                            " (url=" + url + ", bodyPreview=" + preview + ")"
            );

        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException("deleteWatchlistByName: error calling DELETE /v2/watchlists:by_name (url=" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Cancella una watchlist per nome (asincrono).
     */
    public CompletableFuture<Boolean> deleteAsyncWatchlistByName(boolean enableRetries, String accIdForRateLimitTracking, String name) {
        Objects.requireNonNull(name, "name cannot be null");
        if (name.isBlank()) throw new IllegalArgumentException("name cannot be blank");

        final String base = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), WATCHLISTS_BY_NAME_SUFFIX);
        final String url  = HttpUtils.setQueryParams(base, Map.of("name", name));

        LOG.debug("Calling DELETE /v2/watchlists:by_name (async) | url={} | retries={} | name={}", url, enableRetries, name);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url, HttpMethod.DELETE, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
                )
                .thenApply(resp -> {
                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);

                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                        String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException(
                                "deleteWatchlistByName (async): HTTP status NOT OK: " + resp.statusCode() +
                                        " (url=" + url + ", bodyPreview=" + preview + ")"
                        );
                    }
                    return true;
                });
    }

    // ========================================================================
    // DELETE /v2/watchlists/{id}/{symbol} — Remove symbol (idempotente)
    // ========================================================================

    /**
     * Rimuove un simbolo da una watchlist (sincrono).
     * <p>Endpoint: <code>DELETE /v2/watchlists/{id}/{symbol}</code></p>
     * <p>Response: watchlist aggiornata.</p>
     */
    public AlpacaWatchlistResponse deleteSymbolFromWatchlist(boolean enableRetries, String accIdForRateLimitTracking, String watchlistId, String symbol) {
        Objects.requireNonNull(watchlistId, "watchlistId cannot be null");
        if (watchlistId.isBlank()) throw new IllegalArgumentException("watchlistId cannot be blank");
        Objects.requireNonNull(symbol, "symbol cannot be null");
        if (symbol.isBlank()) throw new IllegalArgumentException("symbol cannot be blank");

        final String base = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), WATCHLISTS_SUFFIX);
        final String withId = HttpUtils.setPathParam(base, watchlistId);
        final String url    = HttpUtils.setPathParam(withId, symbol); // gestisce URL-encoding (es. "BTC/USDT")

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling DELETE /v2/watchlists/{id}/{symbol} (sync) | url={} | retries={} | id={} | symbol={}",
                    url, enableRetries, watchlistId, symbol);

            HttpResponse<String> resp = this.httpRestClient.sendHttpRequest(
                    url, HttpMethod.DELETE, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
            );

            long ms = (System.nanoTime() - t0) / 1_000_000;

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);

            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                AlpacaWatchlistResponse out = JsonCodec.fromJson(resp.body(), AlpacaWatchlistResponse.class);
                LOG.info("DELETE /v2/watchlists/{id}/{symbol} OK | id={} | symbol={} | status={} | ms={}",
                        watchlistId, symbol, resp.statusCode(), ms);
                return out;
            }

            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("DELETE /v2/watchlists/{id}/{symbol} NOT OK | id={} | symbol={} | status={} | ms={} | preview={}",
                    watchlistId, symbol, resp.statusCode(), ms, preview);

            throw new ApcaRestClientException(
                    "deleteSymbolFromWatchlist: HTTP status NOT OK: " + resp.statusCode() +
                            " (url=" + url + ", bodyPreview=" + preview + ")"
            );

        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException("deleteSymbolFromWatchlist: error calling DELETE /v2/watchlists/{id}/{symbol} (url=" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Rimuove un simbolo da una watchlist (asincrono).
     */
    public CompletableFuture<AlpacaWatchlistResponse> deleteAsyncSymbolFromWatchlist(boolean enableRetries, String watchlistId, String symbol) {
        Objects.requireNonNull(watchlistId, "watchlistId cannot be null");
        if (watchlistId.isBlank()) throw new IllegalArgumentException("watchlistId cannot be blank");
        Objects.requireNonNull(symbol, "symbol cannot be null");
        if (symbol.isBlank()) throw new IllegalArgumentException("symbol cannot be blank");

        final String base   = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), WATCHLISTS_SUFFIX);
        final String withId = HttpUtils.setPathParam(base, watchlistId);
        final String url    = HttpUtils.setPathParam(withId, symbol);

        LOG.debug("Calling DELETE /v2/watchlists/{id}/{symbol} (async) | url={} | retries={} | id={} | symbol={}",
                url, enableRetries, watchlistId, symbol);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url, HttpMethod.DELETE, enableRetries, this.alpacaRestConfig.getAuthHeaderParams(), ""
                )
                .thenApply(resp -> {
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                        String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException(
                                "deleteSymbolFromWatchlist (async): HTTP status NOT OK: " + resp.statusCode() +
                                        " (url=" + url + ", bodyPreview=" + preview + ")"
                        );
                    }
                    return resp.body();
                })
                .thenApply(body -> JsonCodec.fromJson(body, AlpacaWatchlistResponse.class));
    }
}
