package io.github.cepeppe.playground;

import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.data.request.AlpacaCreateWatchlistRequest;
import io.github.cepeppe.rest.data.response.*;
import io.github.cepeppe.rest.trading.v2.AlpacaCalendarRestService;
import io.github.cepeppe.rest.trading.v2.AlpacaWatchlistsRestService;
import io.github.cepeppe.rest.trading.v2.account.AlpacaAccountActivitiesRestService;
import io.github.cepeppe.utils.HttpUtils;
import io.github.cepeppe.rest.trading.v2.account.AlpacaAccountActivitiesRestService.AccountActivitiesQuery;
import io.github.cepeppe.rest.trading.v2.account.AlpacaAccountActivitiesRestService.AccountActivitiesByTypeQuery;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static io.github.cepeppe.Constants.ALPACA.DEFAULT_SINGLE_ACC_ID_RATE_LIMIT;

/**
 * <h1>AlpacaTradingApiPlayground_B</h1>
 *
 * Playground dedicato a GET /v2/calendar (Trading API v2).
 *
 * <h2>Convenzioni</h2>
 * <ul>
 *   <li>Prefissi log: [Preflight], [SYNC], [ASYNC-DTO], [EXPECTED-WARN].</li>
 *   <li>Liste dal service sempre immutabili.</li>
 *   <li><b>Nuova regola</b>: le eccezioni <i>attese</i> qui sono loggate a livello <b>WARN</b>,
 *       con una <b>versione breve</b> (no stacktrace), esplicitando che sono attese.</li>
 *   <li>Il DTO ha campi privati → qui usiamo solo i suoi metodi di utilità.</li>
 * </ul>
 *
 * <p><b>Estensione</b>: integra anche i test per <b>/v2/watchlists</b>
 * (Get All, Create, Get by ID, Delete by ID, Get by Name, Delete by Name, Delete Symbol),
 * riusando gli stessi prefissi. La POST <i>non</i> è idempotente:
 * il relativo RestService forza retries=false (qui lo segnaliamo a log).</p>
 *
 * <p><b>Ulteriore estensione</b>: integra i test per <b>/v2/account/activities</b> e
 * <b>/v2/account/activities/{activity_type}</b>, con:
 * <ul>
 *   <li>query di base (senza <code>date</code>) con <code>page_size</code> “stress” (clamp lato service a 100);</li>
 *   <li>query con <code>date</code> (giorno singolo);</li>
 *   <li>query multi-tipo (<code>activity_types</code>), anteprima eterogenea e conteggio per tipo;</li>
 *   <li>endpoint “by type” per <code>FILL</code> e <code>CFEE</code> (sync/async);</li>
 *   <li>validazioni attese (es. <code>date</code> insieme a <code>after</code>, <code>category</code> proibita sul “by type”).</li>
 * </ul>
 * </p>
 */
public class AlpacaTradingApiPlayground_B {

    private static final ApcaRestClientLogger LOG =
            ApcaRestClientLogger.getLogger(AlpacaTradingApiPlayground_B.class);

    /** Max caratteri nelle anteprime di log. */
    private static final int BODY_PREVIEW_LEN = 256;

    public AlpacaTradingApiPlayground_B() {}

    /** Esegue su endpoint PAPER con retry abilitati. */
    public static void run() {
        run(AlpacaRestBaseEndpoints.API_V2_PAPER_TRADING, true);
    }

    /**
     * Esegue il playground.
     *
     * @param desiredEndpoint endpoint di base (PAPER/PRODUCTION Trading v2)
     * @param enableRetries   abilita le policy di retry per chiamate idempotenti
     */
    public static void run(AlpacaRestBaseEndpoints desiredEndpoint, boolean enableRetries) {
        Objects.requireNonNull(desiredEndpoint, "desiredEndpoint");

        LOG.info("=== TradingApiPlayground_B START === (endpoint={}, retries={})",
                desiredEndpoint.name(), enableRetries);

        final AlpacaCalendarRestService calendarService     = new AlpacaCalendarRestService(desiredEndpoint);
        final AlpacaWatchlistsRestService watchlistService  = new AlpacaWatchlistsRestService(desiredEndpoint);
        final AlpacaAccountActivitiesRestService activitiesService = new AlpacaAccountActivitiesRestService(desiredEndpoint);

        LOG.debug("[Preflight] Calendar baseUrl = {}", calendarService.getAlpacaRestConfig().getBaseUrl());
        LOG.debug("[Preflight] Watchlists baseUrl = {}", watchlistService.getAlpacaRestConfig().getBaseUrl());
        LOG.debug("[Preflight] Activities baseUrl = {}", activitiesService.getAlpacaRestConfig().getBaseUrl());
        LOG.debug("[Preflight] Orario mercato in fuso {}", AlpacaMarketCalendarDayResponse.US_EASTERN);

        try {
            // ========================================================================
            // CALENDAR (codice esistente, INVARIATO salvo micro-ritocchi stilistici)
            // ========================================================================

            // ------------------------------------------------------------
            // [SYNC] Range: oggi → +7 giorni (fuso US_EASTERN)
            // ------------------------------------------------------------
            LocalDate start = LocalDate.now(AlpacaMarketCalendarDayResponse.US_EASTERN);
            LocalDate end   = start.plusDays(7);

            LOG.debug("[SYNC] getMarketCalendarDays({}, {})", start, end);
            long t0 = System.nanoTime();
            List<AlpacaMarketCalendarDayResponse> days =
                    calendarService.getCalendarDays(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, start, end);
            long ms = elapsedMs(t0);

            LOG.info("[SYNC] SUCCESS in {} ms | count={} | range=[{}, {}]", ms, days.size(), start, end);
            LOG.info("[SYNC] Preview (first up to 3): {}", safePreviewDays(days, 3));

            // Esempio d’uso dei metodi utility del DTO (no accesso ai campi privati)
            if (!days.isEmpty()) {
                AlpacaMarketCalendarDayResponse d0 = days.getFirst();
                ZonedDateTime openET  = d0.openAtZone();
                ZonedDateTime closeET = d0.closeAtZone();

                LOG.info("[SYNC] Day#0 computed: openET={} | closeET={} | ext={}–{} (ET) | openUTC={} | closeUTC={}",
                        nullSafe(openET),
                        nullSafe(closeET),
                        nullSafe(d0.sessionOpenTime()),
                        nullSafe(d0.sessionCloseTime()),
                        nullSafe(d0.openInstantUtc()),
                        nullSafe(d0.closeInstantUtc()));
            }

            // ------------------------------------------------------------
            // [ASYNC-DTO] Giorno singolo: domani
            // ------------------------------------------------------------
            LocalDate day = start.plusDays(1);
            LOG.debug("[ASYNC-DTO] getAsyncMarketCalendarDays({}, {})", day, day);

            long t1 = System.nanoTime();
            CompletableFuture<List<AlpacaMarketCalendarDayResponse>> fut =
                    calendarService.getAsyncCalendarDays(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, day, day);

            List<AlpacaMarketCalendarDayResponse> asyncDays = fut.join();
            long ms2 = elapsedMs(t1);

            LOG.info("[ASYNC-DTO] SUCCESS in {} ms | count={} | day={}", ms2, asyncDays.size(), day);
            LOG.info("[ASYNC-DTO] Preview: {}", safePreviewDays(asyncDays, 1));

            // ------------------------------------------------------------
            // [EXPECTED-WARN] Esempio di eccezione attesa: end < start
            // ------------------------------------------------------------
            try {
                LOG.debug("[EXPECTED-WARN] Calling with end < start to demonstrate validation WARN");
                LocalDate badStart = start;
                LocalDate badEnd   = start.minusDays(1);
                calendarService.getCalendarDays(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, badStart, badEnd);
                LOG.warn("[EXPECTED-WARN] Nessuna eccezione con end<start (verificare validazioni).");
            } catch (IllegalArgumentException expected) {
                LOG.warn("[EXPECTED] {}", briefExpected(expected));
            }

            // ========================================================================
            // WATCHLISTS (NUOVO) — test end-to-end degli endpoint richiesti
            // ========================================================================

            // ------------------------------------------------------------
            // [SYNC] GET /v2/watchlists — elenco attuale
            // ------------------------------------------------------------
            LOG.debug("[SYNC] getWatchlists()");
            long t2 = System.nanoTime();
            List<AlpacaWatchlistResponse> all0 = watchlistService.getWatchlists(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT);
            long ms3 = elapsedMs(t2);
            LOG.info("[SYNC] Watchlists: SUCCESS in {} ms | count={}", ms3, all0.size());
            LOG.info("[SYNC] Watchlists: Preview (up to 3) {}", safePreviewWatchlists(all0, 3));

            // ------------------------------------------------------------
            // [SYNC] POST /v2/watchlists — creazione (POST non idempotente → retries=false nel service)
            // ------------------------------------------------------------
            String wlName1 = "MM-Playground-WL-" + System.currentTimeMillis();
            AlpacaCreateWatchlistRequest createReq1 = new AlpacaCreateWatchlistRequest(wlName1, List.of("AAPL", "MSFT"));

            LOG.debug("[SYNC] createWatchlist(name={}, symbols={})", wlName1, createReq1.symbols());
            long t3 = System.nanoTime();
            AlpacaWatchlistResponse wl1 = watchlistService.createWatchlist(/*enableRetries ignored*/ true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, createReq1);
            long ms4 = elapsedMs(t3);
            LOG.info("[SYNC] Created watchlist in {} ms | id={} | name={}", ms4, nullSafe(wl1 != null ? wl1.id() : null), wlName1);

            // ------------------------------------------------------------
            // [SYNC] GET /v2/watchlists/{id} — dettaglio per ID (404 → null)
            // ------------------------------------------------------------
            LOG.debug("[SYNC] getWatchlistById(id={})", wl1.id());
            long t4 = System.nanoTime();
            AlpacaWatchlistResponse byId = watchlistService.getWatchlistById(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, wl1.id());
            long ms5 = elapsedMs(t4);
            LOG.info("[SYNC] getWatchlistById: SUCCESS in {} ms | found={}, name={}, assets={}",
                    ms5, (byId != null), nullSafe(byId != null ? byId.name() : null),
                    (byId != null && byId.assets() != null ? byId.assets().size() : 0));

            // ------------------------------------------------------------
            // [ASYNC-DTO] GET /v2/watchlists:by_name?name=... — dettaglio per nome (404 → null)
            // ------------------------------------------------------------
            LOG.debug("[ASYNC-DTO] getAsyncWatchlistByName(name={})", wlName1);
            long t5 = System.nanoTime();
            AlpacaWatchlistResponse byName = watchlistService
                    .getAsyncWatchlistByName(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, wlName1)
                    .join();
            long ms6 = elapsedMs(t5);
            LOG.info("[ASYNC-DTO] getWatchlistByName: SUCCESS in {} ms | found={}, id={}",
                    ms6, (byName != null), nullSafe(byName != null ? byName.id() : null));

            // ------------------------------------------------------------
            // [SYNC] DELETE /v2/watchlists/{id}/{symbol} — rimuove un simbolo e ritorna la watchlist aggiornata
            // ------------------------------------------------------------
            String symbolToDelete = "MSFT";
            LOG.debug("[SYNC] deleteSymbolFromWatchlist(id={}, symbol={})", wl1.id(), symbolToDelete);
            long t6 = System.nanoTime();
            AlpacaWatchlistResponse wl1AfterDel = watchlistService.deleteSymbolFromWatchlist(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, wl1.id(), symbolToDelete);
            long ms7 = elapsedMs(t6);
            LOG.info("[SYNC] deleteSymbolFromWatchlist: SUCCESS in {} ms | id={} | remainingAssets={}",
                    ms7, wl1AfterDel.id(), (wl1AfterDel.assets() != null ? wl1AfterDel.assets().size() : 0));

            // ------------------------------------------------------------
            // [SYNC] DELETE /v2/watchlists/{id} — elimina per ID (idempotente)
            // ------------------------------------------------------------
            LOG.debug("[SYNC] deleteWatchlistById(id={})", wl1.id());
            long t7 = System.nanoTime();
            boolean deletedById = watchlistService.deleteWatchlistById(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, wl1.id());
            long ms8 = elapsedMs(t7);
            LOG.info("[SYNC] deleteWatchlistById: SUCCESS={} in {} ms | id={}", deletedById, ms8, wl1.id());

            // Verifica 404 → null
            AlpacaWatchlistResponse check404 = watchlistService.getWatchlistById(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, wl1.id());
            LOG.info("[SYNC] getWatchlistById post-delete: found={} | expected=false", (check404 != null));

            // ------------------------------------------------------------
            // [SYNC] Flusso BY_NAME (creo e poi cancello per nome)
            // ------------------------------------------------------------
            String wlName2 = "MM-Playground-WL-" + (System.currentTimeMillis() + 1); // nome diverso
            AlpacaCreateWatchlistRequest createReq2 = new AlpacaCreateWatchlistRequest(wlName2, List.of("TSLA"));

            LOG.debug("[SYNC] createWatchlist(name={}, symbols={})", wlName2, createReq2.symbols());
            AlpacaWatchlistResponse wl2 = watchlistService.createWatchlist(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, createReq2);
            LOG.info("[SYNC] Created watchlist #2 | id={} | name={}", nullSafe(wl2 != null ? wl2.id() : null), wlName2);

            // Delete by NAME
            LOG.debug("[SYNC] deleteWatchlistByName(name={})", wlName2);
            long t8 = System.nanoTime();
            boolean deletedByName = watchlistService.deleteWatchlistByName(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, wlName2);
            long ms9 = elapsedMs(t8);
            LOG.info("[SYNC] deleteWatchlistByName: SUCCESS={} in {} ms | name={}", deletedByName, ms9, wlName2);

            // Conferma 404 by name
            AlpacaWatchlistResponse shouldBeNull = watchlistService.getWatchlistByName(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, wlName2);
            LOG.info("[SYNC] getWatchlistByName post-delete: found={} | expected=false", (shouldBeNull != null));

            // ------------------------------------------------------------
            // [ASYNC-DTO] GET /v2/watchlists — elenco finale
            // ------------------------------------------------------------
            LOG.debug("[ASYNC-DTO] getAsyncWatchlists()");
            long t9 = System.nanoTime();
            List<AlpacaWatchlistResponse> all1 = watchlistService.getAsyncWatchlists(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT).join();
            long ms10 = elapsedMs(t9);
            LOG.info("[ASYNC-DTO] Watchlists final list: SUCCESS in {} ms | count={}", ms10, all1.size());
            LOG.info("[ASYNC-DTO] Watchlists final: Preview {}", safePreviewWatchlists(all1, 3));

            // ------------------------------------------------------------
            // [EXPECTED-WARN] Validazioni lato client (WATCHLISTS)
            // ------------------------------------------------------------
            try {
                LOG.debug("[EXPECTED-WARN] createWatchlist with blank name (should fail fast)");
                watchlistService.createWatchlist(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, new AlpacaCreateWatchlistRequest("   ", List.of("AAPL")));
                LOG.warn("[EXPECTED-WARN] Nessuna eccezione con name blank (verificare validazioni).");
            } catch (IllegalArgumentException expected) {
                LOG.warn("[EXPECTED] {}", briefExpected(expected));
            }

            try {
                LOG.debug("[EXPECTED-WARN] getWatchlistByName with blank name (should fail fast)");
                watchlistService.getWatchlistByName(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, " ");
                LOG.warn("[EXPECTED-WARN] Nessuna eccezione con name blank (verificare validazioni).");
            } catch (IllegalArgumentException expected) {
                LOG.warn("[EXPECTED] {}", briefExpected(expected));
            }

            try {
                LOG.debug("[EXPECTED-WARN] deleteSymbolFromWatchlist with blank symbol (should fail fast)");
                String wlName3 = "MM-Playground-WL-" + (System.currentTimeMillis() + 2);
                AlpacaWatchlistResponse wl3 = watchlistService.createWatchlist(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, new AlpacaCreateWatchlistRequest(wlName3, List.of("AAPL")));
                watchlistService.deleteSymbolFromWatchlist(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, wl3.id(), " "); // deve lanciare IAE
                LOG.warn("[EXPECTED-WARN] Nessuna eccezione con symbol blank (verificare validazioni).");
            } catch (IllegalArgumentException expected) {
                LOG.warn("[EXPECTED] {}", briefExpected(expected));
            } catch (Exception unexpected) {
                LOG.warn("[EXPECTED?] deleteSymbolFromWatchlist produced non-IAE: {}", briefExpected(unexpected));
            }

            // ========================================================================
            // ACCOUNT ACTIVITIES — NUOVO BLOCCO DI TEST
            // ========================================================================

            // [Preflight] info servizio
            LOG.debug("[Preflight] Activities service ready. URL={}", activitiesService.getAlpacaRestConfig().getBaseUrl());

            // ------------------------------------------------------------
            // [SYNC] GET /v2/account/activities — richiesta “stress” senza date
            // (page_size richiesto=500 → il service clampa a 100 quando date è assente)
            // ------------------------------------------------------------
            AccountActivitiesQuery q0 = new AccountActivitiesQuery.Builder()
                    .pageSize(500)                 // stress: clamp atteso a 100 se date assente
                    .direction("desc")
                    .build();

            LOG.debug("[SYNC][ACTIVITIES] getAccountActivities(page_size=500, direction=desc)");
            long ta0 = System.nanoTime();
            List<AlpacaAccountActivityResponse> a0 =
                    activitiesService.getAccountActivities(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, q0);
            long ams0 = elapsedMs(ta0);

            LOG.info("[SYNC][ACTIVITIES] SUCCESS in {} ms | count={} | preview(first 3)={}",
                    ams0, a0.size(), safePreviewActivities(a0, 3));
            LOG.info("[SYNC][ACTIVITIES] Summary: {}", summarizeActivities(a0, 8));

            // ------------------------------------------------------------
            // [SYNC] GET /v2/account/activities — giorno singolo (date)
            // ------------------------------------------------------------
            LocalDate actDate = LocalDate.now(); // giorno odierno; regola server: può restituire “tutti” del giorno
            AccountActivitiesQuery q1 = new AccountActivitiesQuery.Builder()
                    .date(actDate)
                    .direction("desc")
                    .build();

            LOG.debug("[SYNC][ACTIVITIES] getAccountActivities(date={})", actDate);
            long ta1 = System.nanoTime();
            List<AlpacaAccountActivityResponse> a1 =
                    activitiesService.getAccountActivities(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, q1);
            long ams1 = elapsedMs(ta1);

            LOG.info("[SYNC][ACTIVITIES] SUCCESS in {} ms | count={} | date={} | summary={}",
                    ams1, a1.size(), actDate, summarizeActivities(a1, 6));

            // ------------------------------------------------------------
            // [ASYNC-DTO] GET /v2/account/activities — multi-type (FILL,CFEE,DIV)
            // ------------------------------------------------------------
            AccountActivitiesQuery q2 = new AccountActivitiesQuery.Builder()
                    .activityTypes(List.of("FILL", "CFEE", "DIV"))
                    .pageSize(50)
                    .direction("desc")
                    .build();

            LOG.debug("[ASYNC-DTO][ACTIVITIES] getAccountActivitiesAsync(types=FILL,CFEE,DIV, page_size=50)");
            long ta2 = System.nanoTime();
            List<AlpacaAccountActivityResponse> a2 =
                    activitiesService.getAsyncAccountActivities(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, q2).join();
            long ams2 = elapsedMs(ta2);

            LOG.info("[ASYNC-DTO][ACTIVITIES] SUCCESS in {} ms | count={} | preview(first 5)={}",
                    ams2, a2.size(), safePreviewActivities(a2, 5));
            LOG.info("[ASYNC-DTO][ACTIVITIES] Summary: {}", summarizeActivities(a2, 10));

            // ------------------------------------------------------------
            // [SYNC] GET /v2/account/activities/{activity_type} — FILL
            // ------------------------------------------------------------
            AccountActivitiesByTypeQuery btFill = new AccountActivitiesByTypeQuery.Builder()
                    .direction("desc")
                    .pageSize(50)
                    .build();

            LOG.debug("[SYNC][ACTIVITIES] getAccountActivitiesByType(type=FILL)");
            long tb0 = System.nanoTime();
            List<AlpacaAccountActivityResponse> bt0 =
                    activitiesService.getAccountActivitiesByType(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, "FILL", btFill);
            long bms0 = elapsedMs(tb0);

            LOG.info("[SYNC][ACTIVITIES] ByType FILL SUCCESS in {} ms | count={} | tradeCount={} | preview(first 3)={}",
                    bms0, bt0.size(), countTrades(bt0), safePreviewActivities(bt0, 3));

            // ------------------------------------------------------------
            // [ASYNC-DTO] GET /v2/account/activities/{activity_type} — CFEE
            // ------------------------------------------------------------
            AccountActivitiesByTypeQuery btCfee = new AccountActivitiesByTypeQuery.Builder()
                    .direction("desc")
                    .pageSize(50)
                    .build();

            LOG.debug("[ASYNC-DTO][ACTIVITIES] getAccountActivitiesByTypeAsync(type=CFEE)");
            long tb1 = System.nanoTime();
            List<AlpacaAccountActivityResponse> bt1 =
                    activitiesService.getAsyncAccountActivitiesByType(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, "CFEE", btCfee).join();
            long bms1 = elapsedMs(tb1);

            LOG.info("[ASYNC-DTO][ACTIVITIES] ByType CFEE SUCCESS in {} ms | count={} | nonTradeCount={} | preview(first 3)={}",
                    bms1, bt1.size(), countNonTrades(bt1), safePreviewActivities(bt1, 3));

            // ------------------------------------------------------------
            // [EXPECTED-WARN] Validazioni lato client (ACTIVITIES)
            // ------------------------------------------------------------

            // (1) date insieme a after → IAE attesa
            try {
                LOG.debug("[EXPECTED-WARN][ACTIVITIES] date with after (should fail)");
                AccountActivitiesQuery bad = new AccountActivitiesQuery.Builder()
                        .date(LocalDate.now())
                        .after("2025-01-01")
                        .build();
                activitiesService.getAccountActivities(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, bad);
                LOG.warn("[EXPECTED-WARN][ACTIVITIES] Nessuna eccezione con date+after (verificare validazioni).");
            } catch (IllegalArgumentException expected) {
                LOG.warn("[EXPECTED] {}", briefExpected(expected));
            }

            // (2) category su endpoint byType → IAE attesa
            try {
                LOG.debug("[EXPECTED-WARN][ACTIVITIES] category on byType (should fail)");
                AccountActivitiesByTypeQuery badBt = new AccountActivitiesByTypeQuery.Builder()
                        .category("crypto") // non ammesso in byType
                        .build();
                activitiesService.getAccountActivitiesByType(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, "CFEE", badBt);
                LOG.warn("[EXPECTED-WARN][ACTIVITIES] Nessuna eccezione con category in byType (verificare validazioni).");
            } catch (IllegalArgumentException expected) {
                LOG.warn("[EXPECTED] {}", briefExpected(expected));
            }

            // (3) direction invalida → IAE attesa
            try {
                LOG.debug("[EXPECTED-WARN][ACTIVITIES] invalid direction (should fail)");
                AccountActivitiesQuery badDir = new AccountActivitiesQuery.Builder()
                        .direction("sideways")
                        .build();
                activitiesService.getAccountActivities(enableRetries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, badDir);
                LOG.warn("[EXPECTED-WARN][ACTIVITIES] Nessuna eccezione con direction invalida (verificare validazioni).");
            } catch (IllegalArgumentException expected) {
                LOG.warn("[EXPECTED] {}", briefExpected(expected));
            }

        } catch (Exception e) {
            // Errori non attesi nel playground → ERROR (stacktrace completo)
            LOG.error("Playground_B failed: {}", e.getMessage(), e);
        } finally {
            LOG.info("=== TradingApiPlayground_B END ===");
        }
    }

    /* -------------------------------------
       Helpers di logging / preview
       ------------------------------------- */

    private static long elapsedMs(long t0Nano) {
        return (System.nanoTime() - t0Nano) / 1_000_000;
    }

    /**
     * Anteprima compatta dei primi {@code maxItems} elementi.
     * Usa toString() del DTO (safe, no accesso ai campi privati).
     */
    private static String safePreviewDays(List<AlpacaMarketCalendarDayResponse> days, int maxItems) {
        if (days == null || days.isEmpty() || maxItems <= 0) return "[]";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(maxItems, days.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(", ");
            sb.append(days.get(i).toString());
        }
        return HttpUtils.safePreview(sb.toString(), BODY_PREVIEW_LEN);
    }

    private static String nullSafe(Object o) {
        return (o == null) ? "-" : String.valueOf(o);
    }

    /**
     * Formatter brevissimo per eccezioni attese.
     * Esempio: "EXPECTED IllegalArgumentException: end must be >= start @ com.moneymachine...AlpacaCalendarRestService.getMarketCalendarDays(..)"
     */
    private static String briefExpected(Throwable t) {
        if (t == null) return "EXPECTED (no throwable)";
        String type = t.getClass().getSimpleName();
        String msg  = HttpUtils.safePreview(String.valueOf(t.getMessage()), 160);

        // Cerca il primo frame dell'app; fallback al primo frame disponibile
        StackTraceElement[] st = t.getStackTrace();
        String at = "";
        if (st != null && st.length > 0) {
            StackTraceElement chosen = st[0];
            for (StackTraceElement el : st) {
                if (el.getClassName() != null && el.getClassName().startsWith("com.moneymachine")) {
                    chosen = el; break;
                }
            }
            at = " @ " + chosen.getClassName() + "." + chosen.getMethodName() + "(:" +
                    (chosen.getLineNumber() >= 0 ? chosen.getLineNumber() : "?") + ")";
        }

        return "EXPECTED " + type + ": " + msg + at;
    }

    /** Anteprima compatta delle watchlists (solo id|name e numero asset). */
    private static String safePreviewWatchlists(List<AlpacaWatchlistResponse> wls, int maxItems) {
        if (wls == null || wls.isEmpty() || maxItems <= 0) return "[]";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(maxItems, wls.size());
        for (int i = 0; i < n; i++) {
            AlpacaWatchlistResponse w = wls.get(i);
            if (i > 0) sb.append(" | ");
            sb.append("[id=")
                    .append(nullSafe(w.id()))
                    .append(", name=")
                    .append(nullSafe(w.name()))
                    .append(", assets=")
                    .append(w.assets() != null ? w.assets().size() : 0)
                    .append("]");
        }
        return HttpUtils.safePreview(sb.toString(), BODY_PREVIEW_LEN);
    }

    /** Anteprima compatta delle activities (toString() dei primi maxItems). */
    private static String safePreviewActivities(List<AlpacaAccountActivityResponse> acts, int maxItems) {
        if (acts == null || acts.isEmpty() || maxItems <= 0) return "[]";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(maxItems, acts.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(" || ");
            sb.append(acts.get(i).toString());
        }
        return HttpUtils.safePreview(sb.toString(), BODY_PREVIEW_LEN);
    }

    /** Riepilogo: numero di Trade vs Non-Trade e top N activity_type con conteggio. */
    private static String summarizeActivities(List<AlpacaAccountActivityResponse> acts, int topN) {
        if (acts == null || acts.isEmpty()) return "{empty}";
        int trades = 0, nonTrades = 0;
        Map<String, Integer> byType = new HashMap<>();

        for (AlpacaAccountActivityResponse a : acts) {
            String at;
            if (a instanceof AlpacaAccountTradeActivityResponse t) {
                trades++;
                at = t.activityType();
            } else if (a instanceof AlpacaAccountNonTradeActivityResponse n) {
                nonTrades++;
                at = n.activityType();
            } else {
                at = "UNKNOWN";
            }
            byType.merge(at, 1, Integer::sum);
        }

        // Ordina per conteggio desc e prendi topN
        List<Map.Entry<String, Integer>> list = new ArrayList<>(byType.entrySet());
        list.sort((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()));
        int limit = Math.min(topN, list.size());

        StringBuilder sb = new StringBuilder();
        sb.append("{trades=").append(trades).append(", nonTrades=").append(nonTrades).append(", types=[");
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(", ");
            Map.Entry<String, Integer> e = list.get(i);
            sb.append(e.getKey()).append(":").append(e.getValue());
        }
        if (list.size() > limit) sb.append(", ...");
        sb.append("]}");
        return sb.toString();
    }

    private static long countTrades(List<AlpacaAccountActivityResponse> acts) {
        if (acts == null) return 0;
        return acts.stream().filter(a -> a instanceof AlpacaAccountTradeActivityResponse).count();
    }

    private static long countNonTrades(List<AlpacaAccountActivityResponse> acts) {
        if (acts == null) return 0;
        return acts.stream().filter(a -> a instanceof AlpacaAccountNonTradeActivityResponse).count();
    }
}
