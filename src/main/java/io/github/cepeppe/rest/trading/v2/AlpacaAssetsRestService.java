package io.github.cepeppe.rest.trading.v2;

import io.github.cepeppe.Constants;
import io.github.cepeppe.exception.ApcaRestClientException;
import io.github.cepeppe.http.HttpMethod;
import io.github.cepeppe.json.JsonCodec;
import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.AlpacaRestService;
import io.github.cepeppe.rest.data.response.AlpacaAssetResponse;
import io.github.cepeppe.rest.limits.AlpacaRateLimitMultiton;
import io.github.cepeppe.utils.HttpUtils;

import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * <h1>AlpacaAssetsRestService</h1>
 *
 * Adapter REST per l'endpoint <b>Trading API v2</b> di Alpaca relativo agli <b>Assets</b>.
 * Espone metodi <i>sync</i>/<i>async</i> per interrogare:
 * <ul>
 *   <li><code>GET /v2/assets</code> (lista con filtri)</li>
 *   <li><code>GET /v2/assets/{asset_id_or_symbol}</code> (dettaglio singolo)</li>
 * </ul>
 *
 * <p><b>Progettazione</b>:
 * <ul>
 *   <li>Estende {@link AlpacaRestService} per riuso di configurazione, header, e client HTTP.</li>
 *   <li>Supporta retry opzionale (abilitato solo su chiamate idempotenti, quindi GET).</li>
 *   <li>JSON mapping affidato a {@link JsonCodec} verso {@link AlpacaAssetResponse} o array dello stesso.</li>
 *   <li>Errori HTTP non-OK o I/O/parsing sono wrappati in {@link ApcaRestClientException} con contesto.</li>
 * </ul>
 *
 * <p><b>Thread-safety</b>: la classe è <i>stateless</i> (eccetto la config ereditata, immutabile); sicura per uso concorrente.</p>
 *
 * <p><b>Esempi d’uso</b>:
 * <pre>{@code
 * var svc = new AlpacaAssetsRestService(AlpacaRestBaseEndpoints.TRADING_PAPER);
 * List<AlpacaAssetResponse> equities = svc.getAssets(
 *     true, "active", "us_equity", "NASDAQ", List.of("has_options")
 * );
 *
 * AlpacaAssetResponse aapl = svc.getAsset(true, "AAPL");
 * }</pre>
 * </p>
 */
public class AlpacaAssetsRestService extends AlpacaRestService {

    /** Suffisso di risorsa per comporre l'URL: /v2/<b>assets</b>. */
    private static final String ASSETS_SUFFIX = "assets";

    /** Lunghezza massima della preview del body in log/exception per non inondare i log. */
    private static final int BODY_PREVIEW_LEN = 256;

    /** Logger di progetto (facciata su SLF4J/Logback). */
    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(AlpacaAssetsRestService.class);

    /**
     * Costruisce il servizio puntando a uno specifico endpoint (paper o production).
     *
     * @param desiredEndpoint enum tipizzato che incapsula baseUrl e metadati dell'ambiente target
     */
    public AlpacaAssetsRestService(AlpacaRestBaseEndpoints desiredEndpoint){
        super(desiredEndpoint);
    }

    /**
     * Chiamata sincrona a <code>/v2/assets</code> con filtri opzionali.
     *
     * <ol>
     *   <li>Costruisce la query string con i parametri non null/non blank.</li>
     *   <li>Esegue una GET con header di autenticazione forniti dalla config.</li>
     *   <li>Se lo status è OK (vedi {@link Constants.Http#DEFAULT_OK_STATUS_SET}), deserializza in {@link AlpacaAssetResponse}[] e ritorna come List.</li>
     *   <li>Altrimenti lancia {@link ApcaRestClientException} con contesto (status + anteprima body).</li>
     * </ol>
     *
     * @param enableRetries  se true abilita la politica di retry del client (solo per GET/idempotenti)
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param status         filtro "status" (es. "active"); null/blank = non applicato
     * @param assetClass     filtro "asset_class" (es. "us_equity"); null/blank = non applicato
     * @param exchange       filtro "exchange" (es. "NASDAQ", "NYSE"); null/blank = non applicato
     * @param attributes     lista attributi (CSV generato automaticamente, vedi doc Alpaca); null/empty = non applicato
     * @return               lista di {@link AlpacaAssetResponse}
     * @throws ApcaRestClientException per status non-OK o errori di I/O/parsing
     */
    public List<AlpacaAssetResponse> getAssets(
            boolean enableRetries, String accIdForRateLimitTracking, String status, String assetClass, String exchange, List<String> attributes
    ){
        final Map<String, String> queryParams = buildAssetsQueryParams(status, assetClass, exchange, attributes);
        final String url = buildAssetsUrl(queryParams);

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling GET assets (sync) | url={} | retries={}", url, enableRetries);

            // Nota: per GET il corpo non è richiesto; il client ignora stringhe vuote.
            HttpResponse<String> response = this.httpRestClient.sendHttpRequest(
                    url,
                    HttpMethod.GET,
                    enableRetries,
                    this.alpacaRestConfig.getAuthHeaderParams(),
                    "" // Preferire null se supportato dal client, qui rimane "" per coerenza col resto del codice
            );

            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(response);
            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(response.statusCode())) {
                AlpacaAssetResponse[] arr = JsonCodec.fromJson(response.body(), AlpacaAssetResponse[].class);

                LOG.info("GET /v2/assets OK | count={} | status={} | ms={}",
                        (arr != null ? arr.length : 0), response.statusCode(), elapsedMs);

                return (arr == null) ? List.of() : List.copyOf(Arrays.asList(arr));

            } else {
                String bodyPreview = HttpUtils.safePreview(response.body(), BODY_PREVIEW_LEN);
                LOG.warn("GET /v2/assets NOT OK | status={} | ms={} | preview={}", response.statusCode(), elapsedMs, bodyPreview);

                throw new ApcaRestClientException(
                        "getAssets: HTTP status NOT OK: " + response.statusCode() +
                                " (url=" + url + ", bodyPreview=" + bodyPreview + ")"
                );
            }
        } catch (ApcaRestClientException e) {
            throw e; // già contestualizzata
        } catch (Exception e) {
            throw new ApcaRestClientException("getAssets: error calling Alpaca assets (url=" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Chiamata asincrona a <code>/v2/assets</code> che restituisce direttamente il DTO decodificato.
     *
     * <p>Pipeline async:</p>
     * <ol>
     *   <li><b>sendAsyncHttpRequest</b>: invoca GET con eventuale retry lato client.</li>
     *   <li><b>thenApply</b>: valida lo status; se non OK lancia {@link ApcaRestClientException} (la future fallirà).</li>
     *   <li><b>thenApply</b>: deserializza il body JSON in {@link List} di {@link AlpacaAssetResponse}.</li>
     * </ol>
     *
     * <p>Nota performance: se vuoi spostare la deserializzazione su un executor dedicato,
     * usa <code>thenApplyAsync(..., yourExecutor)</code>.</p>
     *
     * @param enableRetries  se true abilita la politica di retry del client (solo per GET/idempotenti)
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param status         filtro "status" (es. "active"); null/blank = non applicato
     * @param assetClass     filtro "asset_class" (es. "us_equity"); null/blank = non applicato
     * @param exchange       filtro "exchange" (es. "NASDAQ", "NYSE"); null/blank = non applicato
     * @param attributes     lista attributi (CSV generato automaticamente); null/empty = non applicato
     * @return               future completata con lista di {@link AlpacaAssetResponse} o eccezionalmente con {@link ApcaRestClientException}
     */
    public CompletableFuture<List<AlpacaAssetResponse>> getAsyncAssets(
            boolean enableRetries, String accIdForRateLimitTracking, String status, String assetClass, String exchange, List<String> attributes
    ) {
        final Map<String, String> queryParams = buildAssetsQueryParams(status, assetClass, exchange, attributes);
        final String url = buildAssetsUrl(queryParams);

        LOG.debug("Calling GET assets (async) | url={} | retries={}", url, enableRetries);

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
                                "getAssets (async): HTTP status NOT OK: " + resp.statusCode() +
                                        " (url=" + url + ", bodyPreview=" + bodyPreview + ")"
                        );
                    }
                    return resp.body();
                })
                // 2) Decodifica JSON -> DTO
                .thenApply(body -> {
                    AlpacaAssetResponse[] arr = JsonCodec.fromJson(body, AlpacaAssetResponse[].class);
                    return (arr == null) ? List.of() : List.copyOf(Arrays.asList(arr));
                });
    }

    /**
     * Chiamata sincrona di dettaglio a <code>/v2/assets/{asset_id_or_symbol}</code>.
     *
     * @param enableRetries     se true abilita la politica di retry del client (GET idempotente)
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param assetIdOrSymbol   id asset o simbolo (es. "AAPL"); non può essere null
     * @return                  {@link AlpacaAssetResponse} singolo
     * @throws ApcaRestClientException per status non-OK o errori di I/O/parsing
     */
    public AlpacaAssetResponse getAsset(boolean enableRetries, String accIdForRateLimitTracking, String assetIdOrSymbol){
        Objects.requireNonNull(assetIdOrSymbol, "assetIdOrSymbol cannot be null");

        // Costruzione URL: base + path param
        final String url = HttpUtils.setPathParam(
                HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ASSETS_SUFFIX),
                assetIdOrSymbol
        );

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling GET asset (sync) | url={} | retries={}", url, enableRetries);

            HttpResponse<String> response = this.httpRestClient.sendHttpRequest(
                    url,
                    HttpMethod.GET,
                    enableRetries,
                    this.alpacaRestConfig.getAuthHeaderParams(),
                    "" // GET: nessun body
            );

            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(response.statusCode())) {
                AlpacaAssetResponse dto = JsonCodec.fromJson(response.body(), AlpacaAssetResponse.class);

                LOG.info("GET /v2/assets/{{id}} OK | symbol/id={} | status={} | ms={}",
                        assetIdOrSymbol, response.statusCode(), elapsedMs);

                return dto;
            } else {
                String bodyPreview = HttpUtils.safePreview(response.body(), BODY_PREVIEW_LEN);
                LOG.warn("GET /v2/assets/{{id}} NOT OK | symbol/id={} | status={} | ms={} | preview={}",
                        assetIdOrSymbol, response.statusCode(), elapsedMs, bodyPreview);

                throw new ApcaRestClientException(
                        "getAsset: HTTP status NOT OK: " + response.statusCode() +
                                " (url=" + url + ", bodyPreview=" + bodyPreview + ")"
                );
            }
        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException("getAsset: error calling Alpaca assets (url=" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Chiamata asincrona di dettaglio a <code>/v2/assets/{asset_id_or_symbol}</code>.
     *
     * @param enableRetries     se true abilita la politica di retry del client (GET idempotente)
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param assetIdOrSymbol   id asset o simbolo (es. "AAPL"); non può essere null
     * @return                  future completata con {@link AlpacaAssetResponse} o eccezionalmente con {@link ApcaRestClientException}
     */
    public CompletableFuture<AlpacaAssetResponse> getAsyncAsset(boolean enableRetries, String accIdForRateLimitTracking, String assetIdOrSymbol) {
        Objects.requireNonNull(assetIdOrSymbol, "assetIdOrSymbol cannot be null");

        // Costruzione URL: base + path param
        final String url = HttpUtils.setPathParam(
                HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ASSETS_SUFFIX),
                assetIdOrSymbol
        );

        LOG.debug("Calling GET asset (async) | url={} | retries={}", url, enableRetries);

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
                                "getAsset (async): HTTP status NOT OK: " + resp.statusCode() +
                                        " (url=" + url + ", bodyPreview=" + bodyPreview + ")"
                        );
                    }
                    return resp.body();
                })
                // 2) Decodifica JSON -> DTO
                .thenApply(body -> JsonCodec.fromJson(body, AlpacaAssetResponse.class));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Helpers privati (solo refactor cosmetico per riuso; non cambiano la semantica dei metodi pubblici)
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Costruisce la mappa dei query params per /v2/assets filtrando null/blank.
     * Normalizza la lista "attributes" in CSV (via HttpUtils).
     */
    private Map<String, String> buildAssetsQueryParams(String status, String assetClass, String exchange, List<String> attributes) {
        Map<String, String> queryParams = new HashMap<>();

        if (status != null && !status.isBlank()) {
            queryParams.put("status", status);
        }
        if (assetClass != null && !assetClass.isBlank()) {
            queryParams.put("asset_class", assetClass);
        }
        if (exchange != null && !exchange.isBlank()) {
            queryParams.put("exchange", exchange);
        }
        if (attributes != null && !attributes.isEmpty()) {
            String attrs = HttpUtils.normalizeListForQueryParams(attributes);
            if (!attrs.isBlank()) {
                queryParams.put("attributes", attrs);
            }
        }
        return queryParams;
    }

    /**
     * Costruisce l'URL finale per /v2/assets includendo, se presenti, i query params.
     */
    private String buildAssetsUrl(Map<String, String> queryParams) {
        return HttpUtils.setQueryParams(
                HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ASSETS_SUFFIX),
                queryParams
        );
    }
}
