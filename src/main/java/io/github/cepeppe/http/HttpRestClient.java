package io.github.cepeppe.http;


import io.github.cepeppe.Constants;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * HttpRestClient
 *
 * Client REST minimale che costruisce ed esegue richieste HTTP (sincrone e asincrone)
 * delegando la parte “di rete” a {@link BaseHttpClient}. Punti chiave:
 *
 * - **Retry condizionale**: i retry sono abilitati solo se esplicitamente richiesti
 *   e se il metodo HTTP è idempotente (si veda {@link HttpMethod#isIdempotent()}).
 *
 * - **Header generici**: il chiamante può passare header arbitrari (Authorization,
 *   X-Request-Id, Accept, Content-Type, …). Vengono applicati con `setHeader` per
 *   evitare duplicati e garantire l’ultimo valore.
 *
 * - **Politica del body**: il body viene inviato **solo** per metodi che nel tuo enum
 *   espongono `BodyPolicy.OPTIONAL` (tipicamente POST/PUT/PATCH/CONNECT). Per i metodi
 *   con `DISCOURAGED` (GET/HEAD/DELETE/OPTIONS/TRACE) il body passato viene ignorato
 *   per scelta conservativa (maggiore interoperabilità tra server/proxy).
 *
 * - **Content-Type**: quando inviamo un body, impostiamo di default
 *   `Content-Type: application/json; charset=UTF-8`, **ma solo se** il chiamante
 *   non l’ha già specificato.
 *
 * - **Eccezioni specifiche**: il metodo sincrono dichiara `IOException` e
 *   `InterruptedException`, che sono le checked tipiche per `java.net.http.HttpClient`.
 *
 * - **Fail-fast sugli argomenti**: `uri` e `method` non possono essere null.
 *
 * Altre scelte implementative già presenti e qui documentate:
 * - `Accept: application/json` come default (sovrascrivibile dai `headers` passati).
 * - `expectContinue(true)` sempre abilitato (handshake 100-Continue): in scenari normali è
 *   benigno; con payload grandi evita di inviare il body se il server rifiuta subito
 *   (es. 401/417). Trade-off: un RTT extra e rari server/proxy legacy che non lo gestiscono bene.
 * - La configurazione “di rete” (timeout di connessione, redirect, HTTP/2, proxy, TLS,
 *   politiche di retry/backoff) vive dentro {@link BaseHttpClient}.
 */
public class HttpRestClient {

    private final BaseHttpClient httpClient;

    public HttpRestClient() {
        this.httpClient = new BaseHttpClient();
    }

    /**
     * Invia una richiesta HTTP in modo sincrono.
     *
     * <p><strong>Retry</strong>: se {@code enableRetries} è {@code true} e il metodo è
     * idempotente ({@link HttpMethod#isIdempotent()}), la richiesta verrà inviata con
     * logica di retry del {@link BaseHttpClient}; altrimenti senza retry.</p>
     *
     * @param uri           endpoint assoluto (es. {@code https://api.example.com/v1/items/42})
     * @param method        metodo HTTP (l'enum definisce anche idempotenza e policy del body)
     * @param enableRetries abilita i retry solo per metodi idempotenti
     * @param headers       header aggiuntivi (Authorization, X-Request-Id, Accept, Content-Type, …)
     * @param body          payload JSON (usato solo se consentito dalla policy del metodo)
     *
     * @return {@link HttpResponse} con {@code String} come body
     *
     * @throws IOException          errori I/O del client HTTP
     * @throws InterruptedException se il thread è interrotto durante l'attesa
     */
    public HttpResponse<String> sendHttpRequest(
            String uri, HttpMethod method, boolean enableRetries, Map<String, String> headers, String body
    ) throws Exception {
        HttpRequest httpRequest = createHttpRequest(uri, method, headers, body);

        // Modifica #1: AND corto circuito (&&) al posto di &.
        if (enableRetries && method.isIdempotent()) {
            return this.httpClient.sendWithRetry(httpRequest);
        } else {
            return this.httpClient.send(httpRequest);
        }
    }

    /**
     * Invia una richiesta HTTP in modo asincrono.
     *
     * <p><strong>Retry</strong>: se {@code enableRetries} è {@code true} e il metodo è
     * idempotente ({@link HttpMethod#isIdempotent()}), la richiesta verrà inviata con
     * logica di retry del {@link BaseHttpClient}; altrimenti senza retry.</p>
     *
     * @param uri           endpoint assoluto (es. {@code https://api.example.com/v1/items/42})
     * @param method        metodo HTTP (l'enum definisce anche idempotenza e policy del body)
     * @param enableRetries abilita i retry solo per metodi idempotenti
     * @param headers       header aggiuntivi (Authorization, X-Request-Id, Accept, Content-Type, …)
     * @param body          payload JSON (usato solo se consentito dalla policy del metodo)
     *
     * @return future con {@link HttpResponse} e body {@code String}
     */
    public CompletableFuture<HttpResponse<String>> sendAsyncHttpRequest(
            String uri, HttpMethod method, boolean enableRetries, Map<String, String> headers, String body
    ) {
        HttpRequest httpRequest = createHttpRequest(uri, method, headers, body);

        // Modifica #1: AND corto circuito (&&) al posto di &.
        if (enableRetries && method.isIdempotent()) {
            return this.httpClient.sendAsyncWithRetry(httpRequest);
        } else {
            return this.httpClient.sendAsync(httpRequest);
        }
    }

    /**
     * Crea una {@link HttpRequest} pronta all'uso per API JSON.
     *
     * <p><strong>Policy per il body</strong>:
     * <ul>
     *   <li>Il body viene <em>inviato solo</em> quando il metodo del tuo enum espone
     *       {@code BodyPolicy.OPTIONAL} (POST/PUT/PATCH/CONNECT).</li>
     *   <li>Per {@code DISCOURAGED} (GET/HEAD/DELETE/OPTIONS/TRACE) il body passato viene
     *       ignorato per scelta conservativa (maggiore interoperabilità).</li>
     * </ul>
     *
     * <p><strong>Header</strong>:
     * <ul>
     *   <li>Default: {@code Accept: application/json} (sovrascrivibile dai {@code headers}).</li>
     *   <li>Usiamo {@code setHeader(...)} al posto di {@code header(...)} per evitare duplicati
     *       e garantire che l’ultimo valore vinca (utile per Authorization/Accept/Content-Type).</li>
     *   <li>Se inviamo un body e il chiamante <em>non</em> ha già fornito un {@code Content-Type},
     *       impostiamo {@code application/json; charset=UTF-8} come default (Modifica #2).</li>
     * </ul>
     *
     * <p><strong>Altre note</strong>:
     * <ul>
     *   <li>{@code expectContinue(true)} abilita l’handshake 100-Continue.</li>
     *   <li>Timeout per-request: {@code Constants.Http.DEFAULT_REQUEST_TIMEOUT}.</li>
     *   <li>La configurazione dell’{@code HttpClient} (connessione, TLS, proxy, redirect, HTTP/2,
     *       retry/backoff) è gestita da {@link BaseHttpClient}.</li>
     * </ul>
     */
    private HttpRequest createHttpRequest(
            String uri, HttpMethod method, Map<String, String> headers, String body
    ) {
        // Modifica #5: fail-fast sugli argomenti obbligatori.
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(method, "method");

        // Builder base: URI, timeout per-request, handshake 100-Continue e Accept JSON.
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Constants.Http.DEFAULT_REQUEST_TIMEOUT)
                .expectContinue(true)                     // Handshake 100-Continue (vedi note in Javadoc).
                .setHeader("Accept", "application/json"); // Default sovrascrivibile dai 'headers'.

        // Applica eventuali header addizionali (Authorization, X-Request-Id, Accept, Content-Type, ...).
        // Usiamo setHeader per evitare duplicati e garantire l'ultimo valore.
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    reqBuilder.setHeader(e.getKey(), e.getValue());
                }
            }
        }

        // Valutazioni sul body: presente? consentito dal metodo?
        final boolean hasBody     = body != null && !body.isBlank();
        final boolean bodyAllowed = method.requestBodyPolicy() == HttpMethod.BodyPolicy.OPTIONAL;

        // Invia il body solo se è presente e consentito dal metodo secondo l'enum.
        final boolean sendBody    = hasBody && bodyAllowed;

        // Modifica #2: non sovrascrivere Content-Type se già fornito dal chiamante.
        final boolean callerSetContentType =
                headers != null && headers.keySet().stream().anyMatch(h -> "Content-Type".equalsIgnoreCase(h));
        if (sendBody && !callerSetContentType) {
            reqBuilder.setHeader("Content-Type", "application/json; charset=UTF-8");
        }

        // Configurazione per metodo: ogni ramo documenta il comportamento atteso.
        return switch (method) {

            case GET -> {
                // GET: lettura della risorsa, nessun body in richiesta per interoperabilità.
                // Response: body consentito; cacheability dipende dagli header.
                yield reqBuilder.GET().build();
            }

            case HEAD -> {
                // HEAD: come GET ma la risposta **non** deve avere body.
                // Request: body privo di significato -> non inviato.
                yield reqBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            }

            case POST -> {
                // POST: creazione/elaborazione. Body comune (JSON).
                // NOTA: il Content-Type è stato impostato sopra solo se mancava.
                HttpRequest.Builder b = reqBuilder;
                if (sendBody) {
                    b = b.POST(HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    // POST senza body: lecito (es. endpoint che triggerano azioni).
                    b = b.POST(HttpRequest.BodyPublishers.noBody());
                }
                yield b.build();
            }

            case PUT -> {
                // PUT: sostituzione completa della risorsa. Body tipicamente presente.
                HttpRequest.Builder b = reqBuilder;
                if (sendBody) {
                    b = b.PUT(HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    // PUT senza body: raro ma possibile (dipende dall'API).
                    b = b.PUT(HttpRequest.BodyPublishers.noBody());
                }
                yield b.build();
            }

            case PATCH -> {
                // PATCH: aggiornamento parziale. Body quasi sempre presente (merge/JSON Patch).
                HttpRequest.Builder b = reqBuilder;
                if (sendBody) {
                    b = b.method("PATCH", HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    // PATCH senza body: tecnicamente possibile ma spesso senza significato.
                    b = b.method("PATCH", HttpRequest.BodyPublishers.noBody());
                }
                yield b.build();
            }

            case DELETE -> {
                // DELETE: idempotente; il body è "discouraged" e spesso ignorato dai server.
                // Per coerenza/interoperabilità NON lo inviamo.
                yield reqBuilder.DELETE().build();
            }

            case OPTIONS -> {
                // OPTIONS: discovery della risorsa/server (es. Allow, CORS).
                // Request: niente body per interoperabilità.
                yield reqBuilder.method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
            }

            case TRACE -> {
                // TRACE: diagnostica (echo della richiesta). Non si invia body.
                yield reqBuilder.method("TRACE", HttpRequest.BodyPublishers.noBody()).build();
            }

            case CONNECT -> {
                // CONNECT: stabilisce un tunnel via proxy. Raro nei client REST-API.
                HttpRequest.Builder b = reqBuilder;
                if (sendBody) {
                    b = b.method("CONNECT", HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    b = b.method("CONNECT", HttpRequest.BodyPublishers.noBody());
                }
                yield b.build();
            }
        };
    }
}
