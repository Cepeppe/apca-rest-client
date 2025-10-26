package io.github.cepeppe.rest.trading.v2.account;

import io.github.cepeppe.Constants;
import io.github.cepeppe.exception.ApcaRestClientException;
import io.github.cepeppe.http.HttpMethod;
import io.github.cepeppe.json.JsonCodec;
import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.AlpacaRestService;
import io.github.cepeppe.rest.data.response.AlpacaAccountActivityResponse;
import io.github.cepeppe.rest.data.response.AlpacaAccountNonTradeActivityResponse;
import io.github.cepeppe.rest.data.response.AlpacaAccountTradeActivityResponse;
import io.github.cepeppe.rest.limits.AlpacaRateLimitMultiton;
import io.github.cepeppe.utils.HttpUtils;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * <h1>AlpacaAccountActivitiesRestService</h1>
 *
 *  <h2>DISCLAIMER</h2>
 *  It was the Projectist's choice to separate these endpoints from {@link AlpacaAccountRestService} ones, even if the endpoints contained here are under /account</h2>
 *
 *  </br></br>
 * Servizio REST per interrogare le "Account Activities" (Trading API v2).
 * <ul>
 *   <li><b>GET /v2/account/activities</b> – elenco eterogeneo di attività (trade e non-trade).</li>
 *   <li><b>GET /v2/account/activities/{activity_type}</b> – elenco per singolo tipo.</li>
 * </ul>
 *
 * <h2>Caratteristiche & Convenzioni</h2>
 * <ul>
 *   <li><b>Firme base</b>: solo metodi con parametri completi (nessun overload con sottoinsiemi).</li>
 *   <li><b>Retry</b>: firma con flag {@code enableRetries} coerente con le nostre policy client.</li>
 *   <li><b>Liste immutabili</b>: sempre {@code List.copyOf(...)} / {@code List.of()}.</li>
 *   <li><b>Body null/senza contenuto</b>: restituisce {@code List.of()}.</li>
 *   <li><b>Parsing eterogeneo</b>: interno alla classe, senza modifiche a {@code JsonCodec}.</li>
 *   <li><b>Validazioni</b>:
 *     <ul>
 *       <li>{@code date} mutuamente esclusiva con {@code after}/{@code until}.</li>
 *       <li>Per endpoint <i>by type</i>, il query param {@code category} non è ammesso.</li>
 *       <li>{@code direction} ∈ {asc, desc} (case-insensitive; default: desc).</li>
 *       <li>{@code page_size}: se {@code date} è assente, Alpaca massimizza a 100 (noi lo clampiamo a 100).</li>
 *     </ul>
 *   </li>
 *   <li><b>Status</b>: considerati OK i 2xx (il servizio tratta 200/204 come esito regolare con eventuale lista vuota).</li>
 *   <li><b>Log</b>: debug su chiamate, info su successi, warn su status non OK con body preview limitata.</li>
 * </ul>
 *
 * <h2>Riferimenti</h2>
 * Vedi "Retrieve Account Activities" e "Retrieve Account Activities of Specific Type" nella doc ufficiale.
 */
public class AlpacaAccountActivitiesRestService extends AlpacaRestService {

    /** Path risorsa: /v2/<b>account/activities</b>. */
    private static final String ACTIVITIES_SUFFIX = "account/activities";

    /** Anteprima massima body in log/exception. */
    private static final int BODY_PREVIEW_LEN = 256;

    /** Logger di progetto. */
    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(AlpacaAccountActivitiesRestService.class);

    public AlpacaAccountActivitiesRestService(AlpacaRestBaseEndpoints desiredEndpoint) {
        super(desiredEndpoint);
    }

    // ========================================================================
    //  PUBLIC API — GET /v2/account/activities
    // ========================================================================

    /**
     * Recupera le attività dell'account (sincrono).
     *
     * <p><b>Endpoint:</b> {@code GET /v2/account/activities}</p>
     *
     * <h3>Filtri supportati (vedi {@link AccountActivitiesQuery})</h3>
     * <ul>
     *   <li>Se {@code activityTypes} non è vuoto, filtra per la lista di tipi (comma-separated).</li>
     *   <li>{@code date} (YYYY-MM-DD) è mutuamente esclusiva con {@code after}/{@code until}.</li>
     *   <li>{@code direction} = asc|desc; {@code pageSize}, {@code pageToken} per la paginazione.</li>
     *   <li>{@code category} (se supportato da Alpaca) non può essere usato con il path <i>by type</i>.</li>
     * </ul>
     *
     * @param enableRetries abilita/disabilita le policy di retry
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param query parametri di filtro/paginazione (non {@code null})
     * @return lista <b>immutabile</b> di attività (trade e non-trade), mai {@code null}
     * @throws ApcaRestClientException per HTTP non OK o errori di trasporto/parsing
     * @throws IllegalArgumentException per parametri incoerenti
     */
    public List<AlpacaAccountActivityResponse> getAccountActivities(boolean enableRetries,
                                                                    String accIdForRateLimitTracking,
                                                                    AccountActivitiesQuery query) {
        Objects.requireNonNull(query, "query cannot be null");

        // Validazioni e costruzione query string
        final Map<String, String> qp = buildAndValidateQueryMap(query, /*byType*/ false);

        final String base = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ACTIVITIES_SUFFIX);
        final String url  = qp.isEmpty() ? base : HttpUtils.setQueryParams(base, qp);

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling GET /v2/account/activities (sync) | url={} | retries={} | query={}", url, enableRetries, qp);

            HttpResponse<String> resp = this.httpRestClient.sendHttpRequest(
                    url,
                    HttpMethod.GET,
                    enableRetries,
                    this.alpacaRestConfig.getAuthHeaderParams(),
                    "" // GET: no body
            );

            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
            int sc = resp.statusCode();

            AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);

            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc) || sc == 204) {
                List<AlpacaAccountActivityResponse> out = decodeActivitiesArray(resp.body());
                LOG.info("GET /v2/account/activities OK | returned={} | status={} | ms={}", out.size(), sc, elapsedMs);
                return out;
            }

            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("GET /v2/account/activities NOT OK | status={} | ms={} | preview={}", sc, elapsedMs, preview);

            throw new ApcaRestClientException(
                    "getAccountActivities: HTTP status NOT OK: " + sc +
                            " (url=" + url + ", bodyPreview=" + preview + ")"
            );

        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException(
                    "getAccountActivities: error calling Alpaca activities (url=" + url + "): " + e.getMessage(), e
            );
        }
    }

    /**
     * Recupera le attività dell'account (asincrono).
     *
     * <p><b>Endpoint:</b> {@code GET /v2/account/activities}</p>
     *
     * @param enableRetries abilita/disabilita le policy di retry
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param query parametri di filtro/paginazione (non {@code null})
     * @return future completato con lista <b>immutabile</b> (eventualmente vuota)
     */
    public CompletableFuture<List<AlpacaAccountActivityResponse>> getAsyncAccountActivities(boolean enableRetries,
                                                                                            String accIdForRateLimitTracking,
                                                                                            AccountActivitiesQuery query) {
        Objects.requireNonNull(query, "query cannot be null");

        final Map<String, String> qp = buildAndValidateQueryMap(query, /*byType*/ false);
        final String base = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ACTIVITIES_SUFFIX);
        final String url  = qp.isEmpty() ? base : HttpUtils.setQueryParams(base, qp);

        LOG.debug("Calling GET /v2/account/activities (async) | url={} | retries={} | query={}", url, enableRetries, qp);

        return this.httpRestClient.sendAsyncHttpRequest(
                        url,
                        HttpMethod.GET,
                        enableRetries,
                        this.alpacaRestConfig.getAuthHeaderParams(),
                        "" // GET: no body
                )
                .thenApply(resp -> {
                    AlpacaRateLimitMultiton.getInstance().forAccount(accIdForRateLimitTracking).updateFromResponse(resp);
                    int sc = resp.statusCode();
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc) && sc != 204) {
                        String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException(
                                "getAccountActivities (async): HTTP status NOT OK: " + sc +
                                        " (url=" + url + ", bodyPreview=" + preview + ")"
                        );
                    }
                    return resp.body();
                })
                .thenApply(this::decodeActivitiesArray);
    }

    // ========================================================================
    //  PUBLIC API — GET /v2/account/activities/{activity_type}
    // ========================================================================

    /**
     * Recupera le attività per singolo {@code activity_type} (sincrono).
     *
     * <p><b>Endpoint:</b> {@code GET /v2/account/activities/{activity_type}}</p>
     *
     * <h3>Note</h3>
     * <ul>
     *   <li>{@code category} è <b>mutuamente esclusivo</b> con {@code activity_type} nel path (non ammesso qui).</li>
     *   <li>Restano validi i filtri temporali/paginazione {@code date}/{@code after}/{@code until},
     *       {@code direction}, {@code pageSize}, {@code pageToken} (vedi {@link AccountActivitiesByTypeQuery}).</li>
     * </ul>
     *
     * @param enableRetries abilita/disabilita le policy di retry
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param activityType tipo attività (es. "FILL", "DIV", "CFEE", …)
     * @param query filtri temporali/paginazione (non {@code null})
     * @return lista <b>immutabile</b> di attività (solo del tipo richiesto), mai {@code null}
     */
    public List<AlpacaAccountActivityResponse> getAccountActivitiesByType(boolean enableRetries,
                                                                          String accIdForRateLimitTracking,
                                                                          String activityType,
                                                                          AccountActivitiesByTypeQuery query) {
        Objects.requireNonNull(activityType, "activityType cannot be null");
        Objects.requireNonNull(query, "query cannot be null");

        final String normalizedType = activityType.trim().toUpperCase(Locale.ROOT);
        final Map<String, String> qp = buildAndValidateQueryMap(query, /*byType*/ true);

        final String base = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ACTIVITIES_SUFFIX);
        final String withType = HttpUtils.setPathParam(base, normalizedType);
        final String url = qp.isEmpty() ? withType : HttpUtils.setQueryParams(withType, qp);

        long t0 = System.nanoTime();
        try {
            LOG.debug("Calling GET /v2/account/activities/{activity_type} (sync) | url={} | retries={} | type={} | query={}",
                    url, enableRetries, normalizedType, qp);

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

            if (Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc) || sc == 204) {
                List<AlpacaAccountActivityResponse> out = decodeActivitiesArray(resp.body());
                LOG.info("GET /v2/account/activities/{type} OK | type={} | returned={} | status={} | ms={}",
                        normalizedType, out.size(), sc, elapsedMs);
                return out;
            }

            String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
            LOG.warn("GET /v2/account/activities/{type} NOT OK | type={} | status={} | ms={} | preview={}",
                    normalizedType, sc, elapsedMs, preview);

            throw new ApcaRestClientException(
                    "getAccountActivitiesByType: HTTP status NOT OK: " + sc +
                            " (url=" + url + ", bodyPreview=" + preview + ")"
            );

        } catch (ApcaRestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApcaRestClientException(
                    "getAccountActivitiesByType: error calling Alpaca activities by type (url=" + url + "): " + e.getMessage(), e
            );
        }
    }

    /**
     * Recupera le attività per singolo {@code activity_type} (asincrono).
     *
     * <p><b>Endpoint:</b> {@code GET /v2/account/activities/{activity_type}}</p>
     *
     * @param enableRetries abilita/disabilita le policy di retry
     * @param accIdForRateLimitTracking acc id for api calls rate limit tracking, can differ from alpaca account_id as long as used coherently
     * @param activityType tipo attività (es. "FILL", "DIV", "CFEE", …)
     * @param query filtri temporali/paginazione (non {@code null})
     * @return future completato con lista <b>immutabile</b> (eventualmente vuota)
     */
    public CompletableFuture<List<AlpacaAccountActivityResponse>> getAsyncAccountActivitiesByType(boolean enableRetries,
                                                                                                  String accIdForRateLimitTracking,
                                                                                                  String activityType,
                                                                                                  AccountActivitiesByTypeQuery query) {
        Objects.requireNonNull(activityType, "activityType cannot be null");
        Objects.requireNonNull(query, "query cannot be null");

        final String normalizedType = activityType.trim().toUpperCase(Locale.ROOT);
        final Map<String, String> qp = buildAndValidateQueryMap(query, /*byType*/ true);

        final String base = HttpUtils.joinUrl(this.alpacaRestConfig.getBaseUrl(), ACTIVITIES_SUFFIX);
        final String withType = HttpUtils.setPathParam(base, normalizedType);
        final String url = qp.isEmpty() ? withType : HttpUtils.setQueryParams(withType, qp);

        LOG.debug("Calling GET /v2/account/activities/{activity_type} (async) | url={} | retries={} | type={} | query={}",
                url, enableRetries, normalizedType, qp);

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
                    if (!Constants.Http.DEFAULT_OK_STATUS_SET.contains(sc) && sc != 204) {
                        String preview = HttpUtils.safePreview(resp.body(), BODY_PREVIEW_LEN);
                        throw new ApcaRestClientException(
                                "getAccountActivitiesByType (async): HTTP status NOT OK: " + sc +
                                        " (url=" + url + ", bodyPreview=" + preview + ")"
                        );
                    }
                    return resp.body();
                })
                .thenApply(this::decodeActivitiesArray);
    }

    // ========================================================================
    //  PRIVATE — Helpers
    // ========================================================================

    /**
     * Decodifica un array JSON eterogeneo (trade/non-trade) nel relativo super-tipo
     * {@link AlpacaAccountActivityResponse}, scegliendo la sottoclasse in base al campo {@code activity_type}.
     *
     * <p>Regole:</p>
     * <ul>
     *   <li>Se {@code activity_type} è {@code "FILL"} ⇒ {@link AlpacaAccountTradeActivityResponse}.</li>
     *   <li>Altrimenti ⇒ {@link AlpacaAccountNonTradeActivityResponse}.</li>
     * </ul>
     *
     * <p>Body vuoto o {@code null} ⇒ {@code List.of()}.</p>
     */
    private List<AlpacaAccountActivityResponse> decodeActivitiesArray(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }

        // Prima passata: parse in array di mappe per ispezionare activity_type per elemento.
        Map<?, ?>[] elements = JsonCodec.fromJson(body, Map[].class);
        if (elements == null || elements.length == 0) {
            return List.of();
        }

        List<AlpacaAccountActivityResponse> out = new ArrayList<>(elements.length);
        for (Map<?, ?> raw : elements) {
            if (raw == null) continue;

            Object typeVal = raw.get("activity_type");
            String at = (typeVal == null) ? "" : String.valueOf(typeVal);

            // Re-serialize l’elemento (singolo) e decodifica tipizzata.
            String oneJson = JsonCodec.toJson(raw);

            if ("FILL".equalsIgnoreCase(at)) {
                AlpacaAccountTradeActivityResponse ta = JsonCodec.fromJson(oneJson, AlpacaAccountTradeActivityResponse.class);
                if (ta != null) out.add(ta);
            } else {
                AlpacaAccountNonTradeActivityResponse nta = JsonCodec.fromJson(oneJson, AlpacaAccountNonTradeActivityResponse.class);
                if (nta != null) out.add(nta);
            }
        }

        return List.copyOf(out);
    }

    /**
     * Costruisce e valida la mappa dei query param a partire dai DTO.
     * <p>Le regole comuni sono:
     * <ul>
     *   <li>{@code date} XOR ({@code after} | {@code until}).</li>
     *   <li>{@code direction} = asc|desc (di default {@code desc}).</li>
     *   <li>Se {@code date} è assente e {@code page_size} &gt; 100, clamp a 100 (comportamento server).</li>
     * </ul>
     * <p>Regola specifica per {@code byType = true}:</p>
     * <ul>
     *   <li>{@code category} non è ammesso.</li>
     * </ul>
     */
    private Map<String, String> buildAndValidateQueryMap(BaseQuery base, boolean byType) {
        Objects.requireNonNull(base, "base query cannot be null");

        // date XOR (after|until)
        if (base.date != null && (base.after != null || base.until != null)) {
            throw new IllegalArgumentException("date is mutually exclusive with after/until");
        }

        // direction
        String dir = (base.direction == null || base.direction.isBlank())
                ? "desc"
                : base.direction.trim().toLowerCase(Locale.ROOT);
        if (!dir.equals("asc") && !dir.equals("desc")) {
            throw new IllegalArgumentException("direction must be 'asc' or 'desc'");
        }

        // Clamp page_size se date assente
        Integer pageSize = base.pageSize;
        if (base.date == null && pageSize != null && pageSize > 100) {
            pageSize = 100;
        }

        Map<String, String> qp = new LinkedHashMap<>();

        if (base.date != null) qp.put("date", base.date.toString());
        if (base.after != null) qp.put("after", base.after);
        if (base.until != null) qp.put("until", base.until);
        qp.put("direction", dir);

        if (pageSize != null && pageSize > 0) qp.put("page_size", String.valueOf(pageSize));
        if (base.pageToken != null && !base.pageToken.isBlank()) qp.put("page_token", base.pageToken.trim());

        // Parametri specifici dei due endpoint
        if (!byType) {
            AccountActivitiesQuery q = (AccountActivitiesQuery) base;

            // activity_types (comma-separated)
            if (q.activityTypes != null && !q.activityTypes.isEmpty()) {
                String csv = String.join(",", normalizeTypes(q.activityTypes));
                qp.put("activity_types", csv);
            }

            // category (se valorizzato, lo inoltriamo così com'è — la validità/value set è demandata al server/doc)
            if (q.category != null && !q.category.isBlank()) {
                qp.put("category", q.category.trim());
            }
        } else {
            // byType: category non ammesso
            if (base instanceof AccountActivitiesByTypeQuery bt && bt.category != null && !bt.category.isBlank()) {
                throw new IllegalArgumentException("category cannot be provided when activity_type is in the path");
            }
        }

        return qp;
    }

    private static Collection<String> normalizeTypes(Collection<String> types) {
        List<String> out = new ArrayList<>(types.size());
        for (String t : types) {
            if (t == null) continue;
            String s = t.trim();
            if (!s.isEmpty()) out.add(s.toUpperCase(Locale.ROOT));
        }
        return out;
    }

    // ========================================================================
    //  DTO Query (solo firme base – nessun overload con subset)
    // ========================================================================

    /**
     * Base comune per i parametri di filtro/paginazione degli endpoint Activities.
     *
     * <p><b>Regole:</b> {@code date} è mutuamente esclusiva con {@code after}/{@code until}.
     * {@code direction} ∈ {asc, desc}. {@code page_size} clamp a 100 se {@code date} è assente.</p>
     */
    public static class BaseQuery {
        /** Giorno esatto (YYYY-MM-DD). Mutuamente esclusivo con {@link #after}/{@link #until}. */
        public final LocalDate date;

        /**
         * Limite inferiore temporale (in genere timestamp ISO8601 o data 'YYYY-MM-DD' accettata dalla API).
         * Mutuamente esclusivo con {@link #date}.
         */
        public final String after;

        /**
         * Limite superiore temporale (come {@link #after}). Mutuamente esclusivo con {@link #date}.
         */
        public final String until;

        /** Direzione ordinamento/paginazione: {@code "asc"} o {@code "desc"} (default: {@code "desc"}). */
        public final String direction;

        /** Dimensione pagina; se {@link #date} è assente, Alpaca applica massimo 100. */
        public final Integer pageSize;

        /** Token di impaginazione: ID dell’ultima activity della pagina precedente. */
        public final String pageToken;

        protected BaseQuery(Builder<?> b) {
            this.date      = b.date;
            this.after     = b.after;
            this.until     = b.until;
            this.direction = b.direction;
            this.pageSize  = b.pageSize;
            this.pageToken = b.pageToken;
        }

        /** Builder generico (fluente) per {@link BaseQuery} e derivati. */
        @SuppressWarnings("unchecked")
        public static abstract class Builder<T extends Builder<T>> {
            private LocalDate date;
            private String after;
            private String until;
            private String direction;
            private Integer pageSize;
            private String pageToken;

            public T date(LocalDate date)         { this.date = date; return (T) this; }
            public T after(String after)           { this.after = after; return (T) this; }
            public T until(String until)           { this.until = until; return (T) this; }
            public T direction(String direction)   { this.direction = direction; return (T) this; }
            public T pageSize(Integer pageSize)    { this.pageSize = pageSize; return (T) this; }
            public T pageToken(String pageToken)   { this.pageToken = pageToken; return (T) this; }

            public abstract BaseQuery build();
        }
    }

    /**
     * Query per <b>GET /v2/account/activities</b> (filtro multiplo).
     * <p>Supporta:
     * <ul>
     *   <li>{@code activityTypes}: lista di activity type (es. "FILL", "DIV", "CFEE", ...). Verranno inviati come CSV.</li>
     *   <li>{@code category}: filtro opzionale (mutuamente esclusivo con l'endpoint <i>by type</i>).</li>
     * </ul>
     * Oltre ai campi ereditati da {@link BaseQuery} ({@code date}, {@code after}, {@code until}, {@code direction},
     * {@code pageSize}, {@code pageToken}).</p>
     */
    public static class AccountActivitiesQuery extends BaseQuery {
        public final List<String> activityTypes;
        public final String category;

        private AccountActivitiesQuery(Builder b) {
            super(b);
            this.activityTypes = (b.activityTypes == null) ? List.of() : List.copyOf(b.activityTypes);
            this.category = b.category;
        }

        public static class Builder extends BaseQuery.Builder<Builder> {
            private List<String> activityTypes;
            private String category;

            public Builder activityTypes(Collection<String> types) {
                if (types == null || types.isEmpty()) {
                    this.activityTypes = List.of();
                } else {
                    this.activityTypes = new ArrayList<>(types.size());
                    for (String t : types) if (t != null) this.activityTypes.add(t);
                }
                return this;
            }

            public Builder category(String category) {
                this.category = category;
                return this;
            }

            @Override
            public AccountActivitiesQuery build() {
                return new AccountActivitiesQuery(this);
            }
        }
    }

    /**
     * Query per <b>GET /v2/account/activities/{activity_type}</b> (filtro singolo tipo).
     * <p>Non ammette {@code category} (mutuamente esclusivo con il path parameter), ma lo dichiariamo comunque
     * per coerenza col base type e lo validiamo a {@code null}/vuoto.</p>
     */
    public static class AccountActivitiesByTypeQuery extends BaseQuery {
        public final String category; // deve restare null/vuoto

        private AccountActivitiesByTypeQuery(Builder b) {
            super(b);
            this.category = b.category;
        }

        public static class Builder extends BaseQuery.Builder<Builder> {
            private String category;

            /**
             * Setter presente solo per simmetria; <b>NON</b> usare per byType. In caso venga impostato, il service
             * solleverà {@link IllegalArgumentException}.
             */
            public Builder category(String category) {
                this.category = category;
                return this;
            }

            @Override
            public AccountActivitiesByTypeQuery build() {
                return new AccountActivitiesByTypeQuery(this);
            }
        }
    }
}
