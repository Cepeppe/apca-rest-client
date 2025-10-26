package io.github.cepeppe.rest.trading.v2;



import io.github.cepeppe.Constants;
import io.github.cepeppe.exception.ApcaRestClientException;
import io.github.cepeppe.http.HttpMethod;
import io.github.cepeppe.json.JsonCodec;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.AlpacaRestService;
import io.github.cepeppe.rest.data.response.AlpacaMarketClockInfoResponse;
import io.github.cepeppe.rest.limits.AlpacaRateLimitMultiton;
import io.github.cepeppe.utils.HttpUtils;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Cliente REST per l'endpoint <strong>Trading v2 /clock</strong> di Alpaca.
 *
 * <p>Responsabilità:</p>
 * <ul>
 *   <li>Costruire l'URL di chiamata unendo in modo sicuro {@code baseUrl} e il path logico {@code clock}.</li>
 *   <li>Eseguire chiamate HTTP sincrone/asincrone e decodificare la risposta JSON nel DTO
 *       {@link AlpacaMarketClockInfoResponse} quando lo status è nel set OK
 *       ({@link Constants.Http#DEFAULT_OK_STATUS_SET}).</li>
 *   <li>In caso di errore (status non OK, I/O o JSON parsing), sollevare/propagare una
 *       {@link ApcaRestClientException} con messaggio contestualizzato.</li>
 * </ul>
 *
 * <p>API esposte:</p>
 * <ul>
 *   <li>{@link #getAsyncMarketClockInfo(boolean, String)}
 *       restituisce {@code CompletableFuture<AlpacaMarketClockInfoResponse>} e fallisce la future se lo status non è OK.</li>
 * </ul>
 *
 * <p><strong>Nota di sicurezza:</strong> questa classe non logga mai credenziali; gli header di autenticazione sono
 * passati dal layer {@link AlpacaRestService} tramite {@code alpacaRestConfig.getAuthHeaderParams()}.</p>
 */
public class AlpacaClockRestService extends AlpacaRestService {

    /**
     * Path logico dell'endpoint "clock".
     * <p>
     * Non include la slash finale per evitare problemi di doppia slash con baseUrl; l’unione avviene con HttpUtils.joinUrl(String, String).
     */
    private static final String CLOCK_SUFFIX = "clock";

    /**
     * Crea il servizio per l'endpoint desiderato (paper/prod) ereditando client e config dal base service.
     *
     * @param desiredEndpoint endpoint base di Alpaca (trading v2, ecc.)
     */
    public AlpacaClockRestService(AlpacaRestBaseEndpoints desiredEndpoint) {
        super(desiredEndpoint);
    }

    /* ======================================================================
       METODI SINCRONI
       ====================================================================== */


    /**
     * Recupera sincronicamente le informazioni di "market clock".
     *
     * @param enableRetries abilita/disabilita la logica di retry del client HTTP sottostante
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @return DTO decodificato con le informazioni di market clock
     * @throws ApcaRestClientException se lo status non è OK o in caso di errori I/O/JSON
     */
    public AlpacaMarketClockInfoResponse getMarketClockInfo(boolean enableRetries, String accIdForRateLimitTracking) {
        final String url = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), CLOCK_SUFFIX);
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
                // Decodifica JSON -> DTO; eventuali errori sono wrappati nel catch generico in basso
                return JsonCodec.fromJson(response.body(), AlpacaMarketClockInfoResponse.class);
            } else {
                // Status non OK: alza eccezione con contesto utile (status e anteprima del body)
                String bodyPreview = HttpUtils.safePreview(response.body(), 256);
                throw new ApcaRestClientException(
                        "getMarketClockInfo: HTTP status NOT OK: " + response.statusCode() +
                                " (url=" + url + ", bodyPreview=" + bodyPreview + ")"
                );
            }
        } catch (ApcaRestClientException e) {
            // Già contestualizzata: propaghiamo così com'è
            throw e;
        } catch (Exception e) {
            // Wrappiamo qualsiasi altra eccezione (I/O, JSON parsing, ecc.) con contesto URL
            throw new ApcaRestClientException("getMarketClockInfo: error calling Alpaca clock (url=" + url + "): " + e.getMessage(), e);
        }
    }

    /**
     * Variante asincrona che:
     * <ol>
     *   <li>chiama l'endpoint "clock",</li>
     *   <li>verifica che lo status code sia in {@link Constants.Http#DEFAULT_OK_STATUS_SET},</li>
     *   <li>decodifica il body in {@link AlpacaMarketClockInfoResponse}.</li>
     * </ol>
     * Se uno di questi passaggi fallisce, la future viene completata <em>eccezionalmente</em>
     * con una {@link ApcaRestClientException}.
     *
     * <p>Resta pienamente asincrona: il chiamante può comporre handler con
     * {@code thenApply}, {@code thenCompose}, {@code exceptionally}, {@code handle}, {@code orTimeout}, ecc.</p>
     *
     * @param enableRetries abilita/disabilita retry a livello HTTP client
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @return future che completerà con il DTO decodificato oppure eccezionalmente su errore
     */
    public CompletableFuture<AlpacaMarketClockInfoResponse> getAsyncMarketClockInfo(boolean enableRetries, String accIdForRateLimitTracking) {
        final String url = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), CLOCK_SUFFIX);
        return this.httpRestClient.sendAsyncHttpRequest(
                        url,
                        HttpMethod.GET,
                        enableRetries,
                        this.alpacaRestConfig.getAuthHeaderParams(),
                        "" // GET: nessun body
                )
                // 1) Validazione status + estrazione body (senza bloccare)
                .thenApply(resp -> {
                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(resp.statusCode())) {
                        String bodyPreview = HttpUtils.safePreview(resp.body(), 256);
                        throw new ApcaRestClientException(
                                "getMarketClockInfo (async): HTTP status NOT OK: " + resp.statusCode() +
                                        " (url=" + url + ", bodyPreview=" + bodyPreview + ")"
                        );
                    }
                    return resp.body();
                })
                // 2) Decodifica JSON -> DTO (eccezioni durante il parsing completeranno eccezionalmente la future)
                .thenApply(body -> JsonCodec.fromJson(body, AlpacaMarketClockInfoResponse.class));
        // Se desideri spostare la deserializzazione su un executor dedicato:
        // .thenApplyAsync(body -> JsonCodec.fromJson(body, AlpacaMarketClockInfoResponse.class), yourExecutor)
    }

}
