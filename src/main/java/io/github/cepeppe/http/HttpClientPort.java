package io.github.cepeppe.http;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Porta applicativa per l’invio di richieste HTTP sincrone e asincrone,
 * con o senza logica di retry con backoff esponenziale.
 */
public interface HttpClientPort {

    /**
     * Invia una richiesta HTTP in modo sincrono, senza retry.
     *
     * @param req la {@link HttpRequest} da inviare.
     * @return la {@link HttpResponse} con corpo {@code String}.
     * @throws RuntimeException se l’invio fallisce (dipende dall’implementazione).
     */
    HttpResponse<String> send(HttpRequest req) throws Exception;

    /**
     * Invia una richiesta HTTP in modo sincrono applicando retry con backoff esponenziale.
     *
     * @param req              la {@link HttpRequest} da inviare.
     * @return la {@link HttpResponse} con corpo {@code String} del primo tentativo riuscito.
     * @throws Exception se tutti i tentativi falliscono o l’attesa tra i retry viene interrotta.
     */
    HttpResponse<String> sendWithRetry(HttpRequest req) throws Exception;

    /**
     * Invia una richiesta HTTP in modo asincrono, senza retry.
     *
     * @param req la {@link HttpRequest} da inviare.
     * @return un {@link CompletableFuture} completato con la {@link HttpResponse}
     *         o completato eccezionalmente in caso di errore.
     */
    CompletableFuture<HttpResponse<String>> sendAsync(HttpRequest req);

    /**
     * Invia una richiesta HTTP in modo asincrono applicando retry con backoff esponenziale.
     *
     * @param req              la {@link HttpRequest} da inviare.
     * @return un {@link CompletableFuture} completato con la prima {@link HttpResponse}
     *         valida o eccezionalmente se i tentativi si esauriscono.
     */
    CompletableFuture<HttpResponse<String>> sendAsyncWithRetry(HttpRequest req);
}
