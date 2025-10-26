package io.github.cepeppe.playground;



import io.github.cepeppe.exception.ApcaRestClientException;
import io.github.cepeppe.json.JsonCodec;
import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.data.request.AlpacaCreateOrderRequest;
import io.github.cepeppe.rest.data.request.AlpacaReplaceOrderRequest;
import io.github.cepeppe.rest.data.response.AlpacaDeleteAllOrdersResponse;
import io.github.cepeppe.rest.data.response.AlpacaOrderResponse;
import io.github.cepeppe.rest.trading.v2.AlpacaOrdersRestService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.github.cepeppe.Constants.ALPACA.DEFAULT_SINGLE_ACC_ID_RATE_LIMIT;

/**
 * <h1>AlpacaOrdersRestServicePlayground</h1>
 *
 * <p>Playground/manual test per verificare rapidamente TUTTE le funzionalità dell’{@link AlpacaOrdersRestService}:</p>
 * <ul>
 *   <li>create (POST) – <b>disabilitato</b> di default; abilitalo con proprietà/ENV (vedi sotto)</li>
 *   <li>list (GET) con vari filtri, sync/async</li>
 *   <li>get by id / by client_order_id, sync/async (404 → null)</li>
 *   <li>replace (PATCH) – eseguito solo se è stato creato un ordine live</li>
 *   <li>cancel by id (DELETE; 204→true, 404→false), sync/async</li>
 *   <li>cancel all (DELETE) sync/async</li>
 * </ul>
 *
 * <h2>Come abilitare ordini live (ambiente <i>paper</i>)</h2>
 * <p>Per evitare ordini involontari, il playground è in modalità <b>DRY RUN</b> di default.</p>
 * <ul>
 *   <li>System property: <code>-Dmm.playground.liveOrders=true</code></li>
 *   <li>oppure variabile d’ambiente: <code>MM_PLAYGROUND_LIVE_ORDERS=true</code></li>
 * </ul>
 * <p>Parametri utili (system properties con fallback ENV):</p>
 * <ul>
 *   <li><code>mm.playground.symbol.crypto</code> (default: BTCUSD)</li>
 *   <li><code>mm.playground.notional</code>  (default: 1.00 USD)</li>
 *   <li><code>mm.playground.limitPrice</code> (default: 100.00 – volutamente lontano dal mercato per rimanere “open”)</li>
 * </ul>
 *
 * <p><b>Nota</b>: il playground assume che la configurazione credenziali/endpoint venga gestita
 * dal layer base {@link rest.AlpacaRestService}
 * (es. chiavi lette da ENV/Config del progetto).</p>
 */
public class AlpacaOrdersRestServicePlayground {

    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(AlpacaOrdersRestServicePlayground.class);

    private final AlpacaOrdersRestService svc;

    public AlpacaOrdersRestServicePlayground() {
        this.svc = new AlpacaOrdersRestService(AlpacaRestBaseEndpoints.API_V2_PAPER_TRADING);
    }

    /**
     * Esegue una batteria di test su tutti i metodi del servizio ordini.
     *
     * <p>Per default non crea/sostituisce/cancella ordini reali. Abilita ordini con
     * <code>-Dmm.playground.liveOrders=true</code> o <code>MM_PLAYGROUND_LIVE_ORDERS=true</code>.</p>
     */
    public void run() {
        final boolean LIVE = readFlag("mm.playground.liveOrders", "MM_PLAYGROUND_LIVE_ORDERS", false);

        final String SYMBOL_CRYPTO = readStr("mm.playground.symbol.crypto", "MM_PLAYGROUND_SYMBOL_CRYPTO", "BTCUSD");
        final BigDecimal NOTIONAL   = readDecimal("mm.playground.notional", "MM_PLAYGROUND_NOTIONAL", new BigDecimal("1.00"));
        final BigDecimal LIMIT_PX   = readDecimal("mm.playground.limitPrice", "MM_PLAYGROUND_LIMIT_PRICE", new BigDecimal("100.00"));

        LOG.info("=== AlpacaOrdersRestServicePlayground START ===");
        LOG.info("LIVE_ORDERS={} | SYMBOL_CRYPTO={} | NOTIONAL={} | LIMIT_PX={}", LIVE, SYMBOL_CRYPTO, NOTIONAL, LIMIT_PX);

        // -----------------------------------------------------------------------------------------------------------------
        // 1) LIST ORDERS (sync) – open, nested=true, direction=desc
        // -----------------------------------------------------------------------------------------------------------------
        List<AlpacaOrderResponse> openDesc = svc.getOrders(
                /* enableRetries */ true,
                DEFAULT_SINGLE_ACC_ID_RATE_LIMIT,
                /* status */ "open",
                /* limit */ 50,
                /* after */ null,
                /* until */ null,
                /* direction */ "desc",
                /* nested */ true,
                /* side */ null,
                /* symbols */ null
        );
        LOG.info("LIST open (sync): size={} | sample={}", openDesc.size(), preview(openDesc));

        // 1b) LIST ORDERS (async) – all, asc, filtered by symbols (if you want to test)
        List<AlpacaOrderResponse> allAscAsync = svc.getAsyncOrders(
                true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, "all", 25,
                Instant.now().minusSeconds(7 * 24 * 3600), // after last 7 days
                null, "asc", false, null, List.of(SYMBOL_CRYPTO)
        ).orTimeout(30, TimeUnit.SECONDS).join();
        LOG.info("LIST all (async) asc symbols=[{}]: size={} | sample={}", SYMBOL_CRYPTO, allAscAsync.size(), preview(allAscAsync));

        // -----------------------------------------------------------------------------------------------------------------
        // 2) GET by bogus ID (sync/async) – aspettativa: 404 → null
        // -----------------------------------------------------------------------------------------------------------------
        String bogusOrderId = UUID.randomUUID().toString();
        AlpacaOrderResponse bogus = svc.getOrder(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT,bogusOrderId);
        assertNull(bogus, "Expected null for non-existing orderId (404).");
        LOG.info("GET by ID (bogus) OK: id={} → null", bogusOrderId);

        AlpacaOrderResponse bogusAsync = svc.getAsyncOrder(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT,bogusOrderId)
                .orTimeout(30, TimeUnit.SECONDS).join();
        assertNull(bogusAsync, "Expected null for non-existing orderId (404) [async].");
        LOG.info("GET-async by ID (bogus) OK: id={} → null", bogusOrderId);

        // 2b) GET by bogus client_order_id – aspettativa: 404 → null
        String bogusClientId = "MM-BOGUS-" + System.currentTimeMillis();
        AlpacaOrderResponse bogusByClient = svc.getOrderByClientOrderId(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT,bogusClientId);
        assertNull(bogusByClient, "Expected null for non-existing client_order_id (404).");
        LOG.info("GET by client_order_id (bogus) OK: id={} → null", bogusClientId);

        AlpacaOrderResponse bogusByClientAsync = svc.getAsyncOrderByClientOrderId(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT,bogusClientId)
                .orTimeout(30, TimeUnit.SECONDS).join();
        assertNull(bogusByClientAsync, "Expected null for non-existing client_order_id (404) [async].");
        LOG.info("GET-async by client_order_id (bogus) OK: id={} → null", bogusClientId);

        // -----------------------------------------------------------------------------------------------------------------
        // 3) CANCEL by ID su ID inesistente (sync/async) – aspettativa: false
        // -----------------------------------------------------------------------------------------------------------------
        boolean cancelledBogus = svc.cancelOrder(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT,bogusOrderId);
        assertTrue(!cancelledBogus, "Expected false (404) on cancel of non-existing order.");
        LOG.info("CANCEL by ID (bogus) OK: id={} → false", bogusOrderId);

        boolean cancelledBogusAsync = svc.cancelOrderAsync(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT,bogusOrderId)
                .orTimeout(30, TimeUnit.SECONDS).join();
        assertTrue(!cancelledBogusAsync, "Expected false (404) on cancel of non-existing order [async].");
        LOG.info("CANCEL-async by ID (bogus) OK: id={} → false", bogusOrderId);

        // -----------------------------------------------------------------------------------------------------------------
        // 4) CREATE / REPLACE / GETs / CANCEL by ID – eseguiti solo in LIVE=true
        //    Strategia: ordine crypto BTCUSD a limite molto lontano (rimane "open")
        // -----------------------------------------------------------------------------------------------------------------
        String createdOrderId = null;
        String createdClientOrderId = null;

        if (LIVE) {
            LOG.warn("LIVE mode ON → invio ordini sul PAPER endpoint.");
            // Costruisco il request via JSON (robusto rispetto a costruttori/record). Campi minimi validi.
            AlpacaCreateOrderRequest createReq = JsonCodec.fromJson("""
                {
                  "symbol": "%s",
                  "side": "buy",
                  "type": "limit",
                  "time_in_force": "gtc",
                  "notional": %s,
                  "limit_price": %s,
                  "client_order_id": "MM-PLAY-%d"
                }
                """.formatted(SYMBOL_CRYPTO, NOTIONAL.toPlainString(), LIMIT_PX.toPlainString(), System.currentTimeMillis()),
                    AlpacaCreateOrderRequest.class
            );

            AlpacaOrderResponse created = svc.createOrder(DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, createReq);
            assertNotNull(created, "createOrder returned null");
            createdOrderId = created.id();
            createdClientOrderId = created.clientOrderId();
            LOG.info("CREATE OK | id={} | client_order_id={} | status={} | type={} | tif={}",
                    createdOrderId, createdClientOrderId, created.status(), created.type(), created.timeInForce());

            // GET by id / by client_order_id (sync & async)
            AlpacaOrderResponse byId = svc.getOrder(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, createdOrderId);
            assertNotNull(byId, "getOrder (by created id) returned null");
            LOG.info("GET by ID OK | id={} | status={}", byId.id(), byId.status());

            AlpacaOrderResponse byClient = svc.getOrderByClientOrderId(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, createdClientOrderId);
            assertNotNull(byClient, "getOrderByClientOrderId returned null");
            LOG.info("GET by client_order_id OK | id={} | status={}", byClient.id(), byClient.status());

            AlpacaOrderResponse byIdAsync = svc.getAsyncOrder(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, createdOrderId)
                    .orTimeout(30, TimeUnit.SECONDS).join();
            assertNotNull(byIdAsync, "getAsyncOrder returned null");
            LOG.info("GET-async by ID OK | id={} | status={}", byIdAsync.id(), byIdAsync.status());

            // REPLACE: alzo leggermente il limit price (rimane open ma cambia l’ordine)
            BigDecimal newLimit = LIMIT_PX.add(new BigDecimal("10.00"));
            AlpacaReplaceOrderRequest replaceReq = JsonCodec.fromJson("""
                {
                  "limit_price": %s
                }
                """.formatted(newLimit.toPlainString()),
                    AlpacaReplaceOrderRequest.class
            );

            AlpacaOrderResponse replaced = svc.replaceOrder(DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, createdOrderId, replaceReq);
            assertNotNull(replaced, "replaceOrder returned null");
            LOG.info("REPLACE OK | old_id={} | new_id={} | new_limit={}",
                    createdOrderId, replaced.id(), newLimit.toPlainString());

            // CANCEL by ID (nuovo id)
            boolean cancelOk = svc.cancelOrder(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, replaced.id());
            assertTrue(cancelOk, "Expected true (204) on cancel of existing order.");
            LOG.info("CANCEL by ID OK | id={} → true", replaced.id());

            // REPLACE su ordine cancellato → deve fallire (422/400…). Testiamo che arrivi eccezione.
            try {
                svc.replaceOrder(DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, replaced.id(), replaceReq);
                throw new AssertionError("Expected ApcaRestClientException when replacing a canceled/invalid order.");
            } catch (ApcaRestClientException expected) {
                LOG.info("REPLACE on canceled order → got expected exception: {}", expected.getMessage());
            }
        } else {
            LOG.warn("LIVE mode OFF → salta CREATE/REPLACE/CANCEL-by-ID reali. (Imposta mm.playground.liveOrders=true per abilitarli.)");
        }

        // -----------------------------------------------------------------------------------------------------------------
        // 5) CANCEL ALL (sync/async) – sempre idempotente (usalo con prudenza in LIVE)
        // -----------------------------------------------------------------------------------------------------------------
        List<AlpacaDeleteAllOrdersResponse> delAll = svc.cancelAllOrders(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT);
        LOG.info("CANCEL ALL (sync) OK | count={} | sample={}", delAll.size(), preview(delAll));

        List<AlpacaDeleteAllOrdersResponse> delAllAsync = svc.cancelAllOrdersAsync(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT)
                .orTimeout(30, TimeUnit.SECONDS).join();
        LOG.info("CANCEL ALL (async) OK | count={} | sample={}", delAllAsync.size(), preview(delAllAsync));

        // -----------------------------------------------------------------------------------------------------------------
        // 6) LIST closed/all dopo cleanup (solo per vedere che le chiamate funzionino)
        // -----------------------------------------------------------------------------------------------------------------
        List<AlpacaOrderResponse> closed = svc.getOrders(true,DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, "closed", 20, null, null, "desc", false, null, null);
        LOG.info("LIST closed (sync): size={} | sample={}", closed.size(), preview(closed));

        List<AlpacaOrderResponse> allAgainAsync = svc.getAsyncOrders(true,DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, "all", 20, null, null, "desc", false, null, null)
                .orTimeout(30, TimeUnit.SECONDS).join();
        LOG.info("LIST all (async): size={} | sample={}", allAgainAsync.size(), preview(allAgainAsync));

        LOG.info("=== AlpacaOrdersRestServicePlayground END ===");
    }

    // =========================================================================================
    // Helpers
    // =========================================================================================

    private static boolean readFlag(String sysProp, String env, boolean def) {
        String sp = System.getProperty(sysProp);
        if (sp != null) return sp.equalsIgnoreCase("true");
        String ev = System.getenv(env);
        if (ev != null) return ev.equalsIgnoreCase("true");
        return def;
    }

    private static String readStr(String sysProp, String env, String def) {
        String sp = System.getProperty(sysProp);
        if (sp != null && !sp.isBlank()) return sp;
        String ev = System.getenv(env);
        if (ev != null && !ev.isBlank()) return ev;
        return def;
    }

    private static BigDecimal readDecimal(String sysProp, String env, BigDecimal def) {
        try {
            String sp = System.getProperty(sysProp);
            if (sp != null && !sp.isBlank()) return new BigDecimal(sp);
            String ev = System.getenv(env);
            if (ev != null && !ev.isBlank()) return new BigDecimal(ev);
        } catch (Exception e) {
            LOG.warn("Invalid decimal for {} or {} → using default {}", sysProp, env, def);
        }
        return def;
    }

    private static void assertNotNull(Object o, String msg) {
        if (o == null) throw new AssertionError(msg);
    }

    private static void assertNull(Object o, String msg) {
        if (o != null) throw new AssertionError(msg);
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private static String preview(Object o) {
        if (o == null) return "null";
        if (o instanceof Collection<?> c) {
            return c.stream().limit(1).findFirst()
                    .map(JsonCodec::toJson)
                    .orElse("[]");
        }
        return JsonCodec.toJson(o);
    }
}
