package io.github.cepeppe.http;


import io.github.cepeppe.Constants;
import io.github.cepeppe.Env;
import io.github.cepeppe.logging.ApcaRestClientLogger;
import lombok.Getter;
import lombok.Setter;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.github.cepeppe.Constants.Http.*;

/**
 * Implementazione di {@link HttpClientPort} basata su {@link HttpClient} (Java 11+).
 * <p>
 * La classe incapsula:
 * <ul>
 *   <li>Metodi sincroni e asincroni per l'invio di richieste HTTP.</li>
 *   <li>Meccanismo di retry con backoff esponenziale (con cap) + jitter e rispetto opzionale dell'header Retry-After:
 *     <ul>
 *       <li>Codici di stato HTTP configurabili (es. 429, 503...)</li>
 *       <li>Eccezioni I/O/timeout sollevate dal client HTTP</li>
 *     </ul>
 *   </li>
 *   <li>Parametri di retry configurabili via variabili d'ambiente (con fallback ai default di libreria).</li>
 *   <li>Supporto opzionale a proxy HTTP.</li>
 * </ul>
 *
 * <h2>Configurazione</h2>
 * I parametri di retry vengono letti da variabili d'ambiente tramite {@link Env}:
 * <ul>
 *   <li><b>BASE_BACKOFF_MS</b>: base del backoff esponenziale in millisecondi (default: Constants.Http.DEFAULT_BASE_BACKOFF_MS).</li>
 *   <li><b>MAX_ATTEMPTS</b>: numero massimo di tentativi inclusivo del primo (default: Constants.Http.DEFAULT_MAX_ATTEMPTS).</li>
 *   <li><b>RETRY_STATUS_SET</b>: insieme di status HTTP per cui ritentare (default: Constants.Http.DEFAULT_RETRY_STATUS_SET).</li>
 *   <li><b>BACKOFF_CAP_MS</b> (opz.): cap massimo per il backoff (default 10_000 ms).</li>
 *   <li><b>JITTER_MIN</b>/<b>JITTER_MAX</b> (opz.): moltiplicatori jitter (default 0.5–1.5).</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * L'istanza di {@link HttpClient} è thread-safe. I parametri di retry sono mutabili tramite setter
 * (annotazioni Lombok {@link Getter}/{@link Setter}); se vengono cambiati a runtime, i nuovi valori
 * saranno rispettati dalle chiamate successive.
 *
 * <h2>Note importanti per l'integrazione</h2>
 * <ul>
 *   <li><b>(Idempotenza)</b> Questa classe non forza vincoli di idempotenza:
 *       decidere <i>a monte</i> quali metodi siano sicuri da ritentare (GET/HEAD/PUT/DELETE/OPTIONS) e gestire
 *       eventuali POST/PAATCH con Idempotency-Key. Responsabilità della classe “utente”.</li>
 *   <li><b>(Timeout end-to-end)</b> Qui non impostiamo {@code HttpRequest.Builder.timeout(...)} né un “overall timeout”.
 *       È responsabilità del chiamante definire per richiesta il timeout desiderato e l’eventuale timeout complessivo.</li>
 * </ul>
 */
public class BaseHttpClient implements HttpClientPort {

    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(BaseHttpClient.class);

    /**
     * Client HTTP condiviso dall'istanza; thread-safe.
     */
    private final HttpClient client;

    /**
     * Base (in millisecondi) per il calcolo del backoff esponenziale:
     * <pre>delay(attempt) = min(BASE * 2^(attempt-1), BACKOFF_CAP_MS) * jitter(0.5–1.5)</pre>
     * dove attempt inizia da 1. Modificabile via setter o variabili d'ambiente.
     */
    @Getter @Setter
    private long retryBaseBackoffMs;

    /**
     * Massimo numero di tentativi complessivi (incluso il primo invio).
     * Se raggiunto senza successo:
     * - se l'ultimo tentativo ha prodotto una risposta (anche "ritentabile"), viene restituita quella risposta (uniforma sync/async).
     * - se nessun tentativo ha prodotto una risposta valida, viene rilanciata l'ultima eccezione.
     */
    @Getter @Setter
    private int retryMaxAttempts;

    /**
     * Insieme di codici di stato HTTP considerati ritentabili (es. 429, 500, 503...).
     * Se la risposta rientra in questo insieme e non è stato raggiunto il limite di tentativi,
     * il client esegue un retry con backoff.
     */
    @Getter @Setter
    private Set<Integer> retryStatusSet;

    // ---- Parametri avanzati per backoff/jitter/Retry-After ----
    private long backoffCapMs;          // cap massimo del backoff (default 10s)
    private double jitterMin;           // moltiplicatore jitter minimo (default 0.5)
    private double jitterMax;           // moltiplicatore jitter massimo (default 1.5)

    /**
     * Costruttore predefinito: inizializza il client con timeout, versione HTTP,
     * redirect e autenticatore di default, quindi carica i parametri di retry.
     */
    public BaseHttpClient(){
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)      // timeout di connessione di default
                .version(DEFAULT_HTTP_VERSION)                // es. HTTP_1_1 o HTTP_2
                .followRedirects(HttpClient.Redirect.NORMAL);  // segue redirect 3xx standard

        // autenticatore di sistema, se presente
        Authenticator auth = Authenticator.getDefault();
        if (auth != null) {
            httpClientBuilder.authenticator(auth);
        }

        this.client = httpClientBuilder.build();

        loadRetrySettingsOrDefault();
    }

    /**
     * Costruttore con timeout di connessione custom.
     *
     * @param connectTimeout timeout di connessione da usare per il client
     */
    public BaseHttpClient(Duration connectTimeout){
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .version(DEFAULT_HTTP_VERSION)
                .followRedirects(HttpClient.Redirect.NORMAL);

        // autenticatore di sistema, se presente
        Authenticator auth = Authenticator.getDefault();
        if (auth != null) {
            httpClientBuilder.authenticator(auth);
        }

        this.client = httpClientBuilder.build();
        loadRetrySettingsOrDefault();
    }

    /**
     * Costruttore con timeout di connessione e configurazione proxy.
     *
     * @param connectTimeout timeout di connessione da usare per il client
     * @param proxyAddress   indirizzo del proxy (hostname o IP)
     * @param proxyPort      porta del proxy
     */
    public BaseHttpClient(Duration connectTimeout, String proxyAddress, int proxyPort){
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .version(DEFAULT_HTTP_VERSION)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(ProxySelector.of(new InetSocketAddress(proxyAddress, proxyPort))); // instrada via proxy

        // autenticatore di sistema, se presente
        Authenticator auth = Authenticator.getDefault();
        if (auth != null) {
            httpClientBuilder.authenticator(auth);
        }

        this.client = httpClientBuilder.build();

        loadRetrySettingsOrDefault();
    }

    /**
     * Carica i parametri di retry da variabili d'ambiente, se presenti;
     * in caso contrario utilizza i valori di default definiti in {@code Constants.Http}.
     *
     * Protezione contro null su retryStatusSet.
     * Caricati cap e jitter (con default sicuri).
     */
    private void loadRetrySettingsOrDefault(){
        this.retryBaseBackoffMs = Env.getLong("BASE_BACKOFF_MS", DEFAULT_BASE_BACKOFF_MS);
        this.retryMaxAttempts   = Env.getInt("MAX_ATTEMPTS", DEFAULT_MAX_ATTEMPTS);

        Set<Integer> set = Env.getSetOf("RETRY_STATUS_SET", Integer.class, DEFAULT_RETRY_STATUS_SET);
        this.retryStatusSet = (set == null || set.isEmpty()) ? DEFAULT_RETRY_STATUS_SET : set;

        this.backoffCapMs = Env.getLong("BACKOFF_CAP_MS", 5_000L); // 5s default
        this.jitterMin    = Env.getDouble("JITTER_MIN", 0.5d);
        this.jitterMax    = Env.getDouble("JITTER_MAX", 1.5d);

        // clamp jitter coerente
        if (this.jitterMin <= 0d || this.jitterMax < this.jitterMin) {
            this.jitterMin = 0.5d;
            this.jitterMax = 1.5d;
        }
        // base backoff minimo sensato (se mal configurato)
        if (this.retryBaseBackoffMs < 1L) this.retryBaseBackoffMs = 100L;
        if (this.backoffCapMs < this.retryBaseBackoffMs) this.backoffCapMs = Math.max(1000L, this.retryBaseBackoffMs);
        if (this.retryMaxAttempts < 1) this.retryMaxAttempts = 1;
    }

    /**
     * Invia una richiesta HTTP in modo sincrono (nessun retry).
     *
     * @param req richiesta HTTP già costruita (metodo, URI, header, body handler)
     * @return la risposta HTTP come stringa
     * @throws Exception se si verifica un errore di I/O, timeout o simili durante l'invio
     */
    @Override
    public HttpResponse<String> send(HttpRequest req) throws Exception{
        LOG.debug("Sending sync http request: " + req.method() + " " + req.uri());
        return this.client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Invia una richiesta HTTP in modo sincrono con retry su status ritentabili o eccezioni I/O.
     * <p>
     * Strategia:
     * <ol>
     *   <li>Invia la richiesta.</li>
     *   <li>Se la risposta <i>non</i> è in {@code retryStatusSet}, ritorna subito.</li>
     *   <li>Se è in {@code retryStatusSet} o è stata sollevata un'eccezione:
     *     attende un backoff esponenziale con cap (con jitter) e ritenta fino a {@code retryMaxAttempts}.</li>
     *   <li><b>(Punto 1 implementato)</b> Se esaurisci i tentativi, restituisci l'ultima risposta ottenuta
     *       (anche se “ritentabile”); se non hai mai ottenuto una risposta valida, rilancia l’ultima eccezione.</li>
     * </ol>
     *
     * <p><b>Nota (Idempotenza)</b>: RESPONSABILITÀ DEL CHIAMANTE - qui non imponiamo vincoli; decidi a monte se la richiesta è sicura da ritentare.</p>
     * <p><b>Nota (Timeout end-to-end)</b>: RESPONSABILITÀ DEL CHIAMANTE - imposta eventuali timeout per richiesta via {@code HttpRequest.Builder.timeout(...)}.</p>
     *
     * @param req richiesta HTTP
     * @return la prima risposta non ritentabile (o l’ultima disponibile se esce per esaurimento tentativi)
     * @throws Exception l'ultima eccezione catturata (se nessuna risposta disponibile) quando si esauriscono i tentativi
     */
    @Override
    public HttpResponse<String> sendWithRetry(HttpRequest req) throws Exception {
        int attempt = 1;
        Exception lastEx = null;
        HttpResponse<String> lastResponse = null;

        LOG.debug("Sending sync http request with enabled retries: " + req.method() + " " + req.uri());
        while (attempt <= this.retryMaxAttempts) {
            try {

                HttpResponse<String> res = this.client.send(req, HttpResponse.BodyHandlers.ofString());
                lastResponse = res;

                // Se la risposta NON è ritentabile ritorna subito
                if (!this.retryStatusSet.contains(res.statusCode())) {
                    return res;
                }

                // Risposta ritentabile: se abbiamo ancora tentativi dormi con backoff (+jitter) e riprova
                if (attempt < this.retryMaxAttempts) {
                    long retryAfterMs = parseRetryAfterMillis(res, Instant.now()).orElse(nullSafeZero());
                    long delay = computeDelayMs(attempt, retryAfterMs);
                    Thread.sleep(delay);
                    attempt++;
                    continue;
                }

                // Ultimo tentativo ritorna comunque l’ultima risposta
                return res;

            } catch (Exception ex) {
                lastEx = ex;

                if (attempt < this.retryMaxAttempts) {
                    long delay = computeDelayMs(attempt, null); // nessuna Retry-After disponibile in caso di eccezione
                    Thread.sleep(delay);
                    attempt++;
                    continue;
                }

                // Nessuna risposta valida e tentativi esauriti rilancia ultima eccezione
                throw lastEx;
            }
        }

        // Ridondante, ma per completezza: se esci dal loop e hai una risposta, ritorna; altrimenti rilancia l'eccezione.
        if (lastResponse != null) return lastResponse;
        if (lastEx != null) throw lastEx;
        // Stato impossibile qui, ma gestito esplicitamente:
        throw new IllegalStateException("sendWithRetry: stato inatteso");
    }

    /**
     * Invia una richiesta HTTP in modo asincrono (nessun retry).
     * <p>
     * Restituisce una {@link CompletableFuture} già collegata al risultato o al fallimento.
     *
     * <p>Catena semplificata: niente no-op thenCompose/exceptionallyCompose.</p>
     *
     * @param req richiesta HTTP
     * @return future che completerà con {@link HttpResponse} oppure fallirà con l'eccezione sollevata
     */
    @Override
    public CompletableFuture<HttpResponse<String>> sendAsync(HttpRequest req){
        LOG.debug("Sending Async http request: " + req.method() + " " + req.uri());
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Invia una richiesta HTTP in modo asincrono con retry su status ritentabili o eccezioni I/O.
     * <p>
     * Il retry è implementato ricorsivamente tramite {@link #attemptAsync(HttpClient, HttpRequest, int)}.
     * <br><b>(Punto 3 implementato)</b> La pipeline è compatibile JDK11: uso di {@code handle(...)} + {@code thenCompose(...)}.
     *
     * @param req richiesta HTTP
     * @return future che completerà con la prima risposta non ritentabile oppure (punto 1) con l’ultima risposta
     *         anche se “ritentabile”, o fallirà se nessun tentativo ha prodotto una risposta valida.
     */
    @Override
    public CompletableFuture<HttpResponse<String>> sendAsyncWithRetry(HttpRequest req){
        LOG.debug("Sending A-sync http request with enabled retries: " + req.method() + " " + req.uri());
        return attemptAsync(client, req, 1);
    }

    /**
     * Implementazione ricorsiva del retry asincrono.
     * <p>
     * Logica:
     * <ul>
     *   <li>Invia la richiesta con {@code sendAsync}.</li>
     *   <li>Se lo status è ritentabile e non è stato raggiunto {@code retryMaxAttempts},
     *       pianifica un nuovo tentativo dopo il delay calcolato (backoff con cap + jitter e rispetto Retry-After).</li>
     *   <li>Se è l’ultimo tentativo, completa con la risposta attuale (punto 1).</li>
     *   <li>In caso di eccezione, si comporta come sopra: ritenta (se possibile) oppure fallisce definitivamente.</li>
     * </ul>
     *
     * <p><b>Nota (Idempotenza)</b>: il chiamante deve decidere se la richiesta è sicura da ritentare.</p>
     * <p><b>Nota (Timeout end-to-end)</b>: impostare eventuali timeout a livello di {@code HttpRequest} o orchestration.</p>
     */
    private CompletableFuture<HttpResponse<String>> attemptAsync(
            HttpClient client, HttpRequest req, int attempt) {

        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .handle((res, ex) -> {
                    // Branch eccezione
                    if (ex != null) {
                        if (attempt < this.retryMaxAttempts) {
                            long delay = computeDelayMs(attempt, null);
                            return schedule(() -> attemptAsync(client, req, attempt + 1), delay);
                        } else {
                            CompletableFuture<HttpResponse<String>> failed = new CompletableFuture<>();
                            failed.completeExceptionally(ex);
                            return failed;
                        }
                    }

                    // Branch risposta
                    boolean retryable = this.retryStatusSet.contains(res.statusCode());
                    if (retryable && attempt < this.retryMaxAttempts) {
                        Long retryAfterMs = parseRetryAfterMillis(res, Instant.now()).orElse(null);
                        long delay = computeDelayMs(attempt, retryAfterMs);
                        return schedule(() -> attemptAsync(client, req, attempt + 1), delay);
                    }

                    // Risposta non ritentabile, o ultimo tentativo -> completa con questa risposta
                    return CompletableFuture.completedFuture(res);
                })
                // handle(...) ritorna CF<CF<HttpResponse>> -> flatten
                .thenCompose(FunctionIdentity());
    }

    // ---- Helpers ----

    // Schedula supplier dopo delayMs usando delayedExecutor; utile a tenere la catena “pulita” su JDK11
    private CompletableFuture<HttpResponse<String>> schedule(Supplier<CompletableFuture<HttpResponse<String>>> supplier, long delayMs) {
        return CompletableFuture
                .completedFuture(null)
                .thenComposeAsync(ignored -> supplier.get(), CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS));
    }

    // Equivalente “pulito” a Function.identity() ma tipizzato per thenCompose
    private static <T> java.util.function.Function<T, T> FunctionIdentity() {
        return t -> t;
    }

    /**
     * Calcolo del delay con:
     * - base * 2^(attempt-1) con cap
     * - jitter moltiplicativo in [jitterMin, jitterMax]
     * - rispetto (entro cap) di Retry-After se fornito (secondi o HTTP-date)
     */
    private long computeDelayMs(int attempt, Long retryAfterMsNullable) {
        // base * 2^(attempt-1) con cap
        double exp = Math.pow(2.0d, Math.max(0, attempt - 1));
        double raw = retryBaseBackoffMs * exp;
        long backoff = (long) Math.min(raw, (double) backoffCapMs);

        // Retry-After (se presente) vince sul base (entro il cap)
        if (retryAfterMsNullable != null && retryAfterMsNullable > 0L) {
            backoff = Math.min(Math.max(backoff, retryAfterMsNullable), backoffCapMs);
        }

        // jitter moltiplicativo
        double jitter = ThreadLocalRandom.current().nextDouble(jitterMin, jitterMax);
        long withJitter = (long) Math.max(1L, Math.floor(backoff * jitter));

        return withJitter;
    }

    /**
     * Parsing header Retry-After:
     * - intero (secondi)
     * - HTTP-date (RFC_1123)
     */
    private Optional<Long> parseRetryAfterMillis(HttpResponse<?> res, Instant now) {
        try {
            Optional<String> v = res.headers().firstValue("Retry-After");
            if (v.isEmpty()) return Optional.empty();
            String s = v.get().trim();
            // Formato numerico (secondi)
            if (s.chars().allMatch(Character::isDigit)) {
                long sec = Long.parseLong(s);
                return Optional.of(Math.max(0L, sec * 1000L));
            }
            // Formato data HTTP (RFC_1123)
            Instant when = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(s));
            long diff = when.toEpochMilli() - now.toEpochMilli();
            return Optional.of(Math.max(0L, diff));
        } catch (DateTimeParseException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Long nullSafeZero() {
        return 0L;
    }
}
