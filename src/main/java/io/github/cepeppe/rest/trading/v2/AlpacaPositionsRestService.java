package io.github.cepeppe.rest.trading.v2;


import io.github.cepeppe.Constants;
import io.github.cepeppe.exception.ApcaRestClientException;
import io.github.cepeppe.http.HttpMethod;
import io.github.cepeppe.json.JsonCodec;
import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.AlpacaRestService;
import io.github.cepeppe.rest.data.response.AlpacaClosePositionResponse;
import io.github.cepeppe.rest.data.response.AlpacaOrderResponse;
import io.github.cepeppe.rest.data.response.AlpacaPositionResponse;
import io.github.cepeppe.rest.limits.AlpacaRateLimitMultiton;
import io.github.cepeppe.utils.HttpUtils;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * <h1>AlpacaPositionsRestService</h1>
 *
 * Servizio REST per interrogare le posizioni aperte su Alpaca Trading API v2.
 * <ul>
 *   <li><b>GET /v2/positions</b> – elenco posizioni aperte.</li>
 *   <li><b>GET /v2/positions/{symbol|asset_id}</b> – singola posizione aperta per simbolo/assetId.</li>
 * </ul>
 *
 * <h2>Comportamento & note</h2>
 * <ul>
 *   <li><b>Lista immutabile</b>: i metodi che restituiscono elenchi tornano una lista <i>non modificabile</i>
 *       , coerente per thread-safety ed evitando side-effect.</li>
 *   <li><b>Async list NPE</b>: protezione da body {@code null} → lista vuota ì.</li>
 *   <li><b>Messaggi coerenti</b>: corretto il copy/paste nei messaggi/exception a "getOpenPositions".</li>
 *   <li><b>Log endpoint</b>: i log riportano il path reale "/v2/positions".</li>
 *   <li><b>404 singolo</b>: per la singola posizione, HTTP 404 viene mappato a {@code null}
 *       (nessuna posizione aperta per quel simbolo/assetId) sia in sync che in asyncì.</li>
 * </ul>
 */
public class AlpacaPositionsRestService extends AlpacaRestService {

    /** Suffisso di risorsa per comporre l'URL: /v2/<b>positions</b>. */
    private static final String POSITIONS_SUFFIX = "positions";

    /** Lunghezza massima della preview del body in log/exception per non inondare i log. */
    private static final int BODY_PREVIEW_LEN = 256;

    /** Logger di progetto (facciata su SLF4J/Logback). */
    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(AlpacaPositionsRestService.class);

    public AlpacaPositionsRestService(AlpacaRestBaseEndpoints desiredEndpoint) {
        super(desiredEndpoint);
    }

    /**
     * Recupera l'elenco delle posizioni aperte (sincrono).
     *
     * @param enableRetries abilita/disabilita le policy di retry del client per metodi idempotenti
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @return lista <b>immutabile</b> di posizioni; mai {@code null}
     * @throws ApcaRestClientException in caso di esito non OK o errori di trasporto/parsing
     */
    public List<AlpacaPositionResponse> getOpenPositions(boolean enableRetries, String accIdForRateLimitTracking) {
        final String url = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), POSITIONS_SUFFIX);

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling GET /v2/positions (sync) | url={} | retries={}", url, enableRetries);

            // GET: nessun body richiesto (il nostro HttpClient ignora stringa vuota).
            HttpResponse<String> response = this.httpRestClient.sendHttpRequest(
                    url,
                    HttpMethod.GET,
                    enableRetries,
                    this.alpacaRestConfig.getAuthHeaderParams(),
                    ""
            );

            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(response);

            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(response.statusCode())) {
                AlpacaPositionResponse[] arr = JsonCodec.fromJson(response.body(), AlpacaPositionResponse[].class);

                // Lista immutabile + protezione null (arr può essere null se body vuoto/“null”?)
                List<AlpacaPositionResponse> out =
                        (arr == null) ? List.of() : List.copyOf(Arrays.asList(arr));

                LOG.info("GET /v2/positions OK | count={} | status={} | ms={}",
                        out.size(), response.statusCode(), elapsedMs);

                return out;

            } else {
                String bodyPreview = HttpUtils.safePreview(response.body(), BODY_PREVIEW_LEN);
                LOG.warn("GET /v2/positions NOT OK | status={} | ms={} | preview={}",
                        response.statusCode(), elapsedMs, bodyPreview);

                // Messaggio coerente con il metodo
                throw new ApcaRestClientException(
                        "getOpenPositions: HTTP status NOT OK: " + response.statusCode() +
                                " (url=" + url + ", bodyPreview=" + bodyPreview + ")"
                );
            }
        } catch (ApcaRestClientException e) {
            throw e; // già contestualizzata
        } catch (Exception e) {
            throw new ApcaRestClientException(
                    "getOpenPositions: error calling Alpaca positions (url=" + url + "): " + e.getMessage(), e
            );
        }
    }

    /**
     * Recupera l'elenco delle posizioni aperte (asincrono).
     *
     * <p><b>Note:</b> restituisce lista <b>immutabile</b>; se il body è vuoto o "null",
     * torna {@code List.of()} .</p>
     *
     * @param enableRetries abilita/disabilita le policy di retry del client
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @return future completato con lista non nulla (eventualmente vuota)
     */
    public CompletableFuture<List<AlpacaPositionResponse>> getAsyncOpenPosition(boolean enableRetries, String accIdForRateLimitTracking) {

        final String url = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), POSITIONS_SUFFIX);

        LOG.debug("Calling GET /v2/positions (async) | url={} | retries={}", url, enableRetries);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url,
                        HttpMethod.GET,
                        enableRetries,
                        this.alpacaRestConfig.getAuthHeaderParams(),
                        "" // GET: nessun body
                )
                // 1) Validazione status + estrazione body
                .thenApply(resp -> {

                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                        String bodyPreview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException(
                                "getOpenPositions (async): HTTP status NOT OK: " + resp.statusCode() +
                                        " (url=" + url + ", bodyPreview=" + bodyPreview + ")"
                        );
                    }
                    return resp.body();
                })
                // 2) Decodifica JSON -> DTO (lista immutabile; protezione null)
                .thenApply(body -> {
                    AlpacaPositionResponse[] arr = JsonCodec.fromJson(body, AlpacaPositionResponse[].class);
                    return (arr == null) ? List.of() : List.copyOf(Arrays.asList(arr));
                });
    }

    /**
     * Recupera la singola posizione aperta per simbolo/assetId (sincrono).
     *
     * <p><b>Gestione 404</b>: se Alpaca risponde 404, viene restituito {@code null}
     * (nessuna posizione aperta per quel simbolo/assetId).</p>
     *
     * @param enableRetries abilita/disabilita le policy di retry del client
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param symbolOrAssetId simbolo (es. "AAPL", "BTCUSD") o asset_id (UUID)
     * @return DTO della posizione, oppure {@code null} se non esiste posizione aperta (HTTP 404)
     * @throws ApcaRestClientException per esiti diversi da 200/404 o errori di trasporto/parsing
     */
    public AlpacaPositionResponse getOpenPosition(boolean enableRetries, String accIdForRateLimitTracking, String symbolOrAssetId) {

        Objects.requireNonNull(symbolOrAssetId, "assetIdOrSymbol cannot be null");

        final String url = HttpUtils.setPathParam(
                HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), POSITIONS_SUFFIX),
                symbolOrAssetId
        );

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling GET /v2/positions/{id} (sync) | url={} | retries={} | id={}",
                    url, enableRetries, symbolOrAssetId);

            HttpResponse<String> response = this.httpRestClient.sendHttpRequest(
                    url,
                    HttpMethod.GET,
                    enableRetries,
                    this.alpacaRestConfig.getAuthHeaderParams(),
                    ""
            );

            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(response);

            // Mappiamo 404
            if (response.statusCode() == 404) {
                LOG.info("GET /v2/positions NOT FOUND | symbol/id={} | status=404 | ms={}", symbolOrAssetId, elapsedMs);
                return null;
            }

            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(response.statusCode())) {
                AlpacaPositionResponse dto = JsonCodec.fromJson(response.body(), AlpacaPositionResponse.class);

                LOG.info("GET /v2/positions OK | symbol/id={} | status={} | ms={}",
                        symbolOrAssetId, response.statusCode(), elapsedMs);

                return dto;

            } else {
                String bodyPreview = HttpUtils.safePreview(response.body(), BODY_PREVIEW_LEN);
                LOG.warn("GET /v2/positions NOT OK | symbol/id={} | status={} | ms={} | preview={}",
                        symbolOrAssetId, response.statusCode(), elapsedMs, bodyPreview);

                throw new ApcaRestClientException(
                        "getOpenPosition: HTTP status NOT OK: " + response.statusCode() +
                                " (url=" + url + ", bodyPreview=" + bodyPreview + ")"
                );
            }
        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException(
                    "getOpenPosition: error calling Alpaca position (url=" + url + "): " + e.getMessage(), e
            );
        }
    }

    /**
     * Recupera la singola posizione aperta per simbolo/assetId (asincrono).
     *
     * <p><b>Gestione 404</b>: se Alpaca risponde 404, il future si completa con {@code null}
     * (nessuna posizione aperta per quel simbolo/assetId).</p>
     *
     * @param enableRetries abilita/disabilita le policy di retry del client
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param symbolOrAssetId simbolo (es. "AAPL", "BTCUSD") o asset_id (UUID)
     * @return future completato con il DTO della posizione, oppure {@code null} se non esiste
     */
    public CompletableFuture<AlpacaPositionResponse> getAsyncOpenPosition(boolean enableRetries, String accIdForRateLimitTracking, String symbolOrAssetId) {

        Objects.requireNonNull(symbolOrAssetId, "assetIdOrSymbol cannot be null");

        final String url = HttpUtils.setPathParam(
                HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), POSITIONS_SUFFIX),
                symbolOrAssetId
        );

        LOG.debug("Calling GET /v2/positions/{id} (async) | url={} | retries={} | id={}",
                url, enableRetries, symbolOrAssetId);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url,
                        HttpMethod.GET,
                        enableRetries,
                        this.alpacaRestConfig.getAuthHeaderParams(),
                        "" // GET: nessun body
                )
                // 1) Validazione status + estrazione body (404 -> null)
                .thenApply(resp -> {
                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
                    int sc = resp.statusCode();
                    if (sc == 404) {
                        LOG.info("GET /v2/positions NOT FOUND (async) | symbol/id={} | status=404", symbolOrAssetId);
                        return null;
                    }
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc)) {
                        String bodyPreview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException(
                                "getOpenPosition (async): HTTP status NOT OK: " + sc +
                                        " (url=" + url + ", bodyPreview=" + bodyPreview + ")"
                        );
                    }
                    return resp.body();
                })
                // 2) Decodifica JSON -> DTO (propaga null se 404)
                .thenApply(body -> (body == null) ? null : JsonCodec.fromJson(body, AlpacaPositionResponse.class));
    }

    /**
     * Chiude (liquida) <b>tutte</b> le posizioni aperte sul conto (sincrono).
     *
     * <p>Endpoint: {@code DELETE /v2/positions}. In caso di successo, Alpaca può restituire
     * <b>207 Multi-Status</b> con un array di risultati per simbolo ({@link AlpacaClosePositionResponse}).<br/>
     * Se qualche ordine non è più cancellabile, il server può rispondere con <b>500</b> e rifiutare la richiesta.</p>
     *
     * <p>Query param: {@code cancel_orders=true|false}. Se {@code true}, cancella <i>prima</i> tutti gli
     * ordini aperti e poi liquida le posizioni.</p>
     *
     * @param enableRetries abilita/disabilita le policy di retry del client (si applicano ai metodi permessi)
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param cancelOrders se {@code true}, cancella gli ordini aperti prima della liquidazione
     * @return lista <b>immutabile</b> di esiti per simbolo; mai {@code null} (può essere vuota)
     * @throws ApcaRestClientException in caso di status non considerato OK (incluso 500) o errori di trasporto/parsing
     */
    public List<AlpacaClosePositionResponse> closeAllOpenPositions(boolean enableRetries, String accIdForRateLimitTracking, boolean cancelOrders) {
        // Costruzione URL con query param cancel_orders
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("cancel_orders", cancelOrders ? "true" : "false");

        final String base = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), POSITIONS_SUFFIX);
        final String url  = HttpUtils.setQueryParams(base, queryParams);

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling DELETE /v2/positions (sync) | url={} | retries={} | cancel_orders={}",
                    url, enableRetries, cancelOrders);

            // DELETE senza body
            HttpResponse<String> resp = this.httpRestClient.sendHttpRequest(
                    url,
                    HttpMethod.DELETE,
                    enableRetries,
                    this.alpacaRestConfig.getAuthHeaderParams(),
                    ""
            );

            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
            int sc = resp.statusCode();

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);

            // L'endpoint può rispondere 207 Multi-Status. Trattiamolo come "OK" esplicitamente.
            boolean ok = Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc) || sc == 207;

            if (ok) {
                AlpacaClosePositionResponse[] arr = JsonCodec.fromJson(resp.body(), AlpacaClosePositionResponse[].class);
                List<AlpacaClosePositionResponse> out = (arr == null) ? List.of() : List.copyOf(Arrays.asList(arr));

                LOG.info("DELETE /v2/positions OK | returned={} | status={} | ms={} | cancel_orders={}",
                        out.size(), sc, elapsedMs, cancelOrders);

                return out;
            }

            // Gestione NOT OK (incluso 500 Failed to liquidate)
            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("DELETE /v2/positions NOT OK | status={} | ms={} | cancel_orders={} | preview={}",
                    sc, elapsedMs, cancelOrders, preview);

            throw new ApcaRestClientException(
                    "closeAllOpenPositions: HTTP status NOT OK: " + sc +
                            " (url=" + url + ", bodyPreview=" + preview + ")"
            );

        } catch (ApcaRestClientException e) {
            throw e; // già contestualizzata
        } catch (Exception e) {
            throw new ApcaRestClientException(
                    "closeAllOpenPositions: error calling Alpaca DELETE /v2/positions (url=" + url + "): " + e.getMessage(), e
            );
        }
    }

    /**
     * Chiude (liquida) <b>tutte</b> le posizioni aperte sul conto (asincrono).
     *
     * <p>Endpoint: {@code DELETE /v2/positions}. Esito atteso: <b>207 Multi-Status</b> con array di
     * {@link AlpacaClosePositionResponse}. In caso di errore (es. ordini non più cancellabili), il server può
     * restituire <b>500</b> con dettaglio nel body.</p>
     *
     * @param enableRetries abilita/disabilita le policy di retry del client
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param cancelOrders se {@code true}, cancella gli ordini aperti prima della liquidazione
     * @return future completato con lista <b>immutabile</b> di risultati per simbolo (mai {@code null})
     */
    public CompletableFuture<List<AlpacaClosePositionResponse>> closeAllOpenPositionsAsync(boolean enableRetries, String accIdForRateLimitTracking, boolean cancelOrders) {
        // Costruzione URL con query param cancel_orders
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("cancel_orders", cancelOrders ? "true" : "false");

        final String base = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), POSITIONS_SUFFIX);
        final String url  = HttpUtils.setQueryParams(base, queryParams);

        LOG.debug("Calling DELETE /v2/positions (async) | url={} | retries={} | cancel_orders={}",
                url, enableRetries, cancelOrders);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url,
                        HttpMethod.DELETE,
                        enableRetries,
                        this.alpacaRestConfig.getAuthHeaderParams(),
                        "" // DELETE: nessun body
                )
                // 1) Validazione status (207 è OK per questo endpoint)
                .thenApply(resp -> {
                    int sc = resp.statusCode();
                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);

                    boolean ok = Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc) || sc == 207;
                    if (!ok) {
                        String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException(
                                "closeAllOpenPositions (async): HTTP status NOT OK: " + sc +
                                        " (url=" + url + ", bodyPreview=" + preview + ")"
                        );
                    }
                    return resp.body();
                })
                // 2) Decodifica JSON → DTO + lista immutabile (protezione null)
                .thenApply(body -> {
                    AlpacaClosePositionResponse[] arr = JsonCodec.fromJson(body, AlpacaClosePositionResponse[].class);
                    return (arr == null) ? List.of() : List.copyOf(Arrays.asList(arr));
                });
    }

    // ========================================================================
    // OVERLOADS PUBBLICI – closePosition by QTY / by PERCENTAGE
    // ========================================================================

    /**
     * Chiude (liquida) la posizione aperta per il simbolo/assetId indicato,
     * specificando la <b>quantità</b> da liquidare (sincrono).
     *
     * <p>Endpoint: {@code DELETE /v2/positions/{symbol_or_asset_id}}</p>
     *
     * <h3>Parametri</h3>
     * <ul>
     *   <li>{@code qty} – quantità da liquidare (fino a 9 decimali). Deve essere &gt; 0.</li>
     *   <li>{@code enableRetries} – abilita/disabilita le policy di retry del client.</li>
     * </ul>
     *
     * <p><b>Response</b>: 200 OK con l’ordine creato per chiudere la posizione
     * ({@link AlpacaOrderResponse}). Status non 2xx → {@link ApcaRestClientException}.</p>
     *
     * <h3>Razionale</h3>
     * Questo overload rende impossibile passare anche {@code percentage}, rispettando a compile-time
     * la regola “qty Cannot work with percentage”.
     *
     * @param enableRetries abilita/disabilita le policy di retry del client
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param symbolOrAssetId simbolo (es. "AAPL", "BTCUSD") o asset_id (UUID)
     * @param qty quantità da liquidare; deve essere &gt; 0 (non nullo)
     * @return l’ordine creato per la chiusura parziale/totale
     * @throws IllegalArgumentException se i parametri sono invalidi (es. qty ≤ 0 o id vuoto)
     * @throws ApcaRestClientException se HTTP non è OK o in caso di errori di trasporto/parsing
     */
    public AlpacaOrderResponse closePositionByQty(boolean enableRetries,
                                                  String accIdForRateLimitTracking,
                                                  String symbolOrAssetId,
                                                  BigDecimal qty) {
        // Validazioni dedicate all'overload "qty"
        Objects.requireNonNull(symbolOrAssetId, "symbolOrAssetId cannot be null");
        if (symbolOrAssetId.isBlank()) {
            throw new IllegalArgumentException("symbolOrAssetId cannot be blank");
        }
        Objects.requireNonNull(qty, "qty cannot be null");
        if (qty.signum() <= 0) {
            throw new IllegalArgumentException("qty must be > 0");
        }

        // Delego al metodo centrale (ora privato) passando solo qty
        return closePosition(enableRetries, accIdForRateLimitTracking, symbolOrAssetId, qty, null);
    }

    /**
     * Chiude (liquida) la posizione aperta per il simbolo/assetId indicato,
     * specificando la <b>percentuale</b> da liquidare (sincrono).
     *
     * <p>Endpoint: {@code DELETE /v2/positions/{symbol_or_asset_id}}</p>
     *
     * <h3>Parametri</h3>
     * <ul>
     *   <li>{@code percentage} – percentuale (0..100) della posizione da liquidare, fino a 9 decimali.
     *       Vengono vendute frazioni solo se la posizione è originariamente frazionaria.</li>
     *   <li>{@code enableRetries} – abilita/disabilita le policy di retry del client.</li>
     * </ul>
     *
     * <p><b>Response</b>: 200 OK con l’ordine creato per la chiusura
     * ({@link AlpacaOrderResponse}). Status non 2xx → {@link ApcaRestClientException}.</p>
     *
     * <h3>Razionale</h3>
     * Questo overload rende impossibile passare anche {@code qty}, rispettando a compile-time
     * la regola “qty Cannot work with percentage”.
     *
     * @param enableRetries abilita/disabilita le policy di retry del client
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param symbolOrAssetId simbolo (es. "AAPL", "BTCUSD") o asset_id (UUID)
     * @param percentage percentuale 0..100 da liquidare (non nullo)
     * @return l’ordine creato per la chiusura parziale/totale
     * @throws IllegalArgumentException se i parametri sono invalidi (es. percentage &lt; 0 o &gt; 100)
     * @throws ApcaRestClientException se HTTP non è OK o in caso di errori di trasporto/parsing
     */
    public AlpacaOrderResponse closePositionByPercentage(boolean enableRetries,
                                                         String accIdForRateLimitTracking,
                                                         String symbolOrAssetId,
                                                         BigDecimal percentage) {
        // Validazioni dedicate all'overload "percentage"
        Objects.requireNonNull(symbolOrAssetId, "symbolOrAssetId cannot be null");
        if (symbolOrAssetId.isBlank()) {
            throw new IllegalArgumentException("symbolOrAssetId cannot be blank");
        }
        Objects.requireNonNull(percentage, "percentage cannot be null");
        if (percentage.signum() < 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("percentage must be between 0 and 100 (inclusive)");
        }

        // Delego al metodo centrale (ora privato) passando solo percentage
        return closePosition(enableRetries, accIdForRateLimitTracking, symbolOrAssetId, null, percentage);
    }

    /**
     * Chiude (liquida) la posizione aperta per il simbolo/assetId indicato,
     * specificando la <b>quantità</b> da liquidare (asincrono).
     *
     * <p>Endpoint: {@code DELETE /v2/positions/{symbol_or_asset_id}} | Response: 200 OK con
     * {@link AlpacaOrderResponse}. Status non 2xx → {@link ApcaRestClientException}.</p>
     *
     * @param enableRetries abilita/disabilita le policy di retry del client
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param symbolOrAssetId simbolo (es. "AAPL", "BTCUSD") o asset_id (UUID)
     * @param qty quantità da liquidare; deve essere &gt; 0 (non nullo)
     * @return future completato con l’ordine creato per la chiusura
     * @throws IllegalArgumentException se i parametri sono invalidi
     */
    public CompletableFuture<AlpacaOrderResponse> closePositionByQtyAsync(boolean enableRetries,
                                                                          String accIdForRateLimitTracking,
                                                                          String symbolOrAssetId,
                                                                          BigDecimal qty) {
        // Validazioni dedicate all'overload "qty"
        Objects.requireNonNull(symbolOrAssetId, "symbolOrAssetId cannot be null");
        if (symbolOrAssetId.isBlank()) {
            throw new IllegalArgumentException("symbolOrAssetId cannot be blank");
        }
        Objects.requireNonNull(qty, "qty cannot be null");
        if (qty.signum() <= 0) {
            throw new IllegalArgumentException("qty must be > 0");
        }

        // Delego al metodo centrale async (ora privato) passando solo qty
        return closePositionAsync(enableRetries, accIdForRateLimitTracking, symbolOrAssetId, qty, null);
    }

    /**
     * Chiude (liquida) la posizione aperta per il simbolo/assetId indicato,
     * specificando la <b>percentuale</b> da liquidare (asincrono).
     *
     * <p>Endpoint: {@code DELETE /v2/positions/{symbol_or_asset_id}} | Response: 200 OK con
     * {@link AlpacaOrderResponse}. Status non 2xx → {@link ApcaRestClientException}.</p>
     *
     * @param enableRetries abilita/disabilita le policy di retry del client
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param symbolOrAssetId simbolo (es. "AAPL", "BTCUSD") o asset_id (UUID)
     * @param percentage percentuale 0..100 da liquidare (non nullo)
     * @return future completato con l’ordine creato per la chiusura
     * @throws IllegalArgumentException se i parametri sono invalidi
     */
    public CompletableFuture<AlpacaOrderResponse> closePositionByPercentageAsync(boolean enableRetries,
                                                                                 String accIdForRateLimitTracking,
                                                                                 String symbolOrAssetId,
                                                                                 BigDecimal percentage) {
        // Validazioni dedicate all'overload "percentage"
        Objects.requireNonNull(symbolOrAssetId, "symbolOrAssetId cannot be null");
        if (symbolOrAssetId.isBlank()) {
            throw new IllegalArgumentException("symbolOrAssetId cannot be blank");
        }
        Objects.requireNonNull(percentage, "percentage cannot be null");
        if (percentage.signum() < 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("percentage must be between 0 and 100 (inclusive)");
        }

        // Delego al metodo centrale async (ora privato) passando solo percentage
        return closePositionAsync(enableRetries, accIdForRateLimitTracking, symbolOrAssetId, null, percentage);
    }

    /**
     * Chiude (liquida) la posizione aperta per il simbolo/assetId indicato (sincrono).
     *
     * <p>Endpoint: {@code DELETE /v2/positions/{symbol_or_asset_id}}</p>
     *
     * <h3>Query params (mutuamente esclusivi)</h3>
     * <ul>
     *   <li>{@code qty} – quantità da liquidare (fino a 9 decimali). Non può coesistere con {@code percentage}.</li>
     *   <li>{@code percentage} – percentuale (0..100) della posizione da liquidare (fino a 9 decimali).
     *       Non può coesistere con {@code qty}. Vende frazioni solo se la posizione è originariamente frazionaria.</li>
     * </ul>
     *
     * <p><b>Response</b>: 200 OK con l’<i>ordine</i> creato per chiudere la posizione ({@link AlpacaOrderResponse}).<br/>
     * Status diversi da 2xx (es. 404 se la posizione non esiste, 422 per parametri invalidi, 500) → {@link ApcaRestClientException}.</p>
     *
     * @param enableRetries abilita/disabilita le policy di retry del client
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param symbolOrAssetId simbolo (es. "AAPL", "BTCUSD") o asset_id (UUID)
     * @param qty quantità da liquidare; <b>non</b> impostare se usi {@code percentage} (può essere {@code null})
     * @param percentage percentuale 0..100 da liquidare; <b>non</b> impostare se usi {@code qty} (può essere {@code null})
     * @return l'ordine creato da Alpaca per la chiusura
     * @throws ApcaRestClientException se HTTP non è OK o in caso di errori di trasporto/parsing
     * @throws IllegalArgumentException se i parametri sono incoerenti/invalidi
     */
    private AlpacaOrderResponse closePosition(boolean enableRetries,
                                             String accIdForRateLimitTracking,
                                             String symbolOrAssetId,
                                             BigDecimal qty,
                                             BigDecimal percentage) {

        Objects.requireNonNull(symbolOrAssetId, "symbolOrAssetId cannot be null");
        if (symbolOrAssetId.isBlank()) {
            throw new IllegalArgumentException("symbolOrAssetId cannot be blank");
        }

        // Validazioni di coerenza sui parametri qty/percentage
        if (qty != null && percentage != null) {
            throw new IllegalArgumentException("Specify either qty OR percentage, not both");
        }
        if (qty != null && qty.signum() <= 0) {
            throw new IllegalArgumentException("qty must be > 0");
        }
        if (percentage != null) {
            if (percentage.signum() < 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("percentage must be between 0 and 100 (inclusive)");
            }
        }

        // Costruzione URL: /v2/positions/{symbol_or_asset_id}[?qty|percentage]
        final String base = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), POSITIONS_SUFFIX);
        final String withId = HttpUtils.setPathParam(base, symbolOrAssetId);

        Map<String, String> query = new HashMap<>();
        if (qty != null) {
            query.put("qty", qty.toPlainString());
        } else if (percentage != null) {
            query.put("percentage", percentage.toPlainString());
        }
        final String url = query.isEmpty() ? withId : HttpUtils.setQueryParams(withId, query);

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling DELETE /v2/positions/{id} (sync) | url={} | retries={} | id={} | qty={} | percentage={}",
                    url, enableRetries, symbolOrAssetId, qty, percentage);

            HttpResponse<String> resp = this.httpRestClient.sendHttpRequest(
                    url,
                    HttpMethod.DELETE,
                    enableRetries,
                    this.alpacaRestConfig.getAuthHeaderParams(),
                    "" // DELETE: nessun body
            );

            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
            int sc = resp.statusCode();

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);

            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc)) {
                AlpacaOrderResponse order = JsonCodec.fromJson(resp.body(), AlpacaOrderResponse.class);
                LOG.info("DELETE /v2/positions/{id} OK | id={} | status={} | ms={} | qty={} | percentage={}",
                        symbolOrAssetId, sc, elapsedMs, qty, percentage);
                return order;
            }

            // Non OK → eccezione con anteprima body
            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("DELETE /v2/positions/{id} NOT OK | id={} | status={} | ms={} | qty={} | percentage={} | preview={}",
                    symbolOrAssetId, sc, elapsedMs, qty, percentage, preview);

            throw new ApcaRestClientException(
                    "closePosition: HTTP status NOT OK: " + sc +
                            " (url=" + url + ", bodyPreview=" + preview + ")"
            );

        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException(
                    "closePosition: error calling Alpaca DELETE /v2/positions/{id} (url=" + url + "): " + e.getMessage(), e
            );
        }
    }

    /**
     * Chiude (liquida) la posizione aperta per il simbolo/assetId indicato (asincrono).
     *
     * <p>Endpoint: {@code DELETE /v2/positions/{symbol_or_asset_id}} | Response attesa: 200 OK con
     * {@link AlpacaOrderResponse}. Status non 2xx → {@link ApcaRestClientException}.</p>
     *
     * <h3>Regole parametri</h3>
     * <ul>
     *   <li>Usa <b>solo uno</b> tra {@code qty} e {@code percentage}.</li>
     *   <li>{@code qty} &gt; 0; {@code percentage} tra 0 e 100 (inclusi).</li>
     * </ul>
     *
     * @param enableRetries abilita/disabilita le policy di retry del client
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param symbolOrAssetId simbolo (es. "AAPL", "BTCUSD") o asset_id (UUID)
     * @param qty quantità da liquidare (fino a 9 decimali), oppure {@code null}
     * @param percentage percentuale 0..100 da liquidare (fino a 9 decimali), oppure {@code null}
     * @return future completato con l’ordine creato per la chiusura
     * @throws IllegalArgumentException se i parametri sono incoerenti/invalidi
     */
    private CompletableFuture<AlpacaOrderResponse> closePositionAsync(boolean enableRetries,
                                                                     String accIdForRateLimitTracking,
                                                                     String symbolOrAssetId,
                                                                     BigDecimal qty,
                                                                     BigDecimal percentage) {

        Objects.requireNonNull(symbolOrAssetId, "symbolOrAssetId cannot be null");
        if (symbolOrAssetId.isBlank()) {
            throw new IllegalArgumentException("symbolOrAssetId cannot be blank");
        }

        if (qty != null && percentage != null) {
            throw new IllegalArgumentException("Specify either qty OR percentage, not both");
        }
        if (qty != null && qty.signum() <= 0) {
            throw new IllegalArgumentException("qty must be > 0");
        }
        if (percentage != null) {
            if (percentage.signum() < 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("percentage must be between 0 and 100 (inclusive)");
            }
        }

        final String base = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), POSITIONS_SUFFIX);
        final String withId = HttpUtils.setPathParam(base, symbolOrAssetId);

        Map<String, String> query = new HashMap<>();
        if (qty != null) {
            query.put("qty", qty.toPlainString());
        } else if (percentage != null) {
            query.put("percentage", percentage.toPlainString());
        }
        final String url = query.isEmpty() ? withId : HttpUtils.setQueryParams(withId, query);

        LOG.debug("Calling DELETE /v2/positions/{id} (async) | url={} | retries={} | id={} | qty={} | percentage={}",
                url, enableRetries, symbolOrAssetId, qty, percentage);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url,
                        HttpMethod.DELETE,
                        enableRetries,
                        this.alpacaRestConfig.getAuthHeaderParams(),
                        "" // DELETE: nessun body
                )
                // 1) Validazione status (2xx → OK)
                .thenApply(resp -> {
                    int sc = resp.statusCode();
                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc)) {
                        String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException(
                                "closePosition (async): HTTP status NOT OK: " + sc +
                                        " (url=" + url + ", bodyPreview=" + preview + ")"
                        );
                    }
                    return resp.body();
                })
                // 2) Decodifica JSON → DTO ordine
                .thenApply(body -> JsonCodec.fromJson(body, AlpacaOrderResponse.class));
    }

}
