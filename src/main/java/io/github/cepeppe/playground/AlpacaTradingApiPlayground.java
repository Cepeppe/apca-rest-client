package io.github.cepeppe.playground;

import io.github.cepeppe.Env;
import io.github.cepeppe.exception.ApcaRestClientException;
import io.github.cepeppe.json.JsonCodec;
import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.data.response.*;
import io.github.cepeppe.rest.trading.v2.AlpacaAssetsRestService;
import io.github.cepeppe.rest.trading.v2.AlpacaClockRestService;
import io.github.cepeppe.rest.trading.v2.AlpacaPositionsRestService;
import io.github.cepeppe.rest.trading.v2.account.AlpacaAccountRestService;
import io.github.cepeppe.utils.HttpUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.github.cepeppe.Constants.ALPACA.DEFAULT_SINGLE_ACC_ID_RATE_LIMIT;

/**
 * AlpacaTradingApiPlayground
 * --------------------------
 * Scopo:
 * - Esercitare **Trading API v2** di Alpaca per tre risorse chiave:
 * • <b>/v2/clock</b>   → stato orologio di mercato (aperto/chiuso, prossime aperture/chiusure).
 * • <b>/v2/account</b> → dettagli del conto (cash, equity, buying power, flag PDT, ecc.).
 * • <b>/v2/assets</b>  → lista degli asset tradabili/consultabili con filtri (status, class, exchange, attributes).
 * - Diagnosticare rapidamente 401/403 tramite **sonde HTTP raw** che inviano esplicitamente gli header attesi,
 * isolando eventuali problemi di propagazione header nello stack `HttpRestClient`.
 * <p>
 * Stile:
 * - Entry point: {@link #run()} senza argomenti, con toggle (flag statici) per attivare/disattivare le singole demo.
 * - Sezioni ben separate: PRECHECKS → RAW PROBE → DEMO SYNC/ASYNC (CLOCK) → DEMO SYNC/ASYNC (ACCOUNT)
 * → DEMO SYNC/ASYNC (ASSETS list) → DEMO SYNC/ASYNC (ASSET detail)
 * → **DEMO SYNC/ASYNC (POSITIONS list) → DEMO SYNC/ASYNC (POSITION detail)**.
 * <p>
 * Requisiti ENV:
 * - Obbligatori:  APCA_API_KEY_ID, APCA_API_SECRET_KEY
 * - Opzionali:    ALPACA_TRADING_PAPER_API_V2_URL, ALPACA_TRADING_PRODUCTION_API_V2_URL  (override del baseUrl)
 * <p>
 * Sicurezza:
 * - Le credenziali vengono loggate solo offuscate (mai stampare i segreti in chiaro).
 * <p>
 * Note operative:
 * - Le chiamate di servizio (sync/async) utilizzano i nostri adapter `AlpacaClockRestService` / `AlpacaAccountRestService` / `AlpacaAssetsRestService`
 * che a loro volta delegano a `HttpRestClient` (JDK HttpClient) con retry opzionale per metodi idempotenti (GET).
 * - Le sonde "raw" bypassano il nostro stack REST per verificare con certezza se l'errore provenga dalle credenziali/permessi
 * o da un uso scorretto del client (header mancanti, baseUrl errato, ecc.).
 * - /v2/assets**: le API restituiscono una **lista** di elementi (AlpacaAssetResponse). Le demo qui sotto sono adeguate a tale forma.
 * - /v2/assets/{idOrSymbol}: le API restituiscono un **singolo** elemento (AlpacaAssetResponse). Aggiunte demo dedicate (sync/async + raw opzionale).
 * - /v2/positions**: la lista di posizioni aperte restituisce List<AlpacaPositionResponse>; il dettaglio /v2/positions/{idOrSymbol}
 * restituisce un singolo AlpacaPositionResponse oppure 404 se non esiste posizione aperta → mappato a null dal nostro adapter.
 */
public class AlpacaTradingApiPlayground {

    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(AlpacaTradingApiPlayground.class);

            /* =========================
               Configurazione Playground
               ========================= */

    /**
     * Target (cambiare in API_V2_PRODUCTION_TRADING per testare in PROD).
     */
    private static final AlpacaRestBaseEndpoints ENDPOINT = AlpacaRestBaseEndpoints.API_V2_PAPER_TRADING;

    /**
     * Abilita/disabilita retry a livello HTTP client delle service. (Idempotente per GET: OK)
     */
    private static final boolean ENABLE_RETRIES = true;

    /**
     * Toggle generali e per ogni risorsa (CLOCK/ACCOUNT/ASSETS/POSITIONS).
     */
    private static final boolean RUN_PRECHECKS = true;   // controlli ENV e hint iniziali

    // CLOCK
    private static final boolean RUN_RAW_PROBE_CLOCK = true;   // sonda diretta /v2/clock (diagnostica 401)
    private static final boolean RUN_SYNC_CLOCK = true;   // service sync -> DTO Clock
    private static final boolean RUN_ASYNC_CLOCK_DTO = true;   // service async -> DTO Clock
    private static final boolean RUN_ASYNC_CLOCK_RAW = false;  // service async -> HttpResponse<String> (grezzo)

    // ACCOUNT
    private static final boolean RUN_RAW_PROBE_ACCOUNT = true;   // sonda diretta /v2/account (diagnostica 401)
    private static final boolean RUN_SYNC_ACCOUNT_DTO = true;   // service sync -> DTO Account
    private static final boolean RUN_ASYNC_ACCOUNT_DTO = true;   // service async -> DTO Account
    private static final boolean RUN_ASYNC_ACCOUNT_RAW = false;  // service async -> HttpResponse<String> (grezzo)

    // ACCOUNT - PORTFOLIO HISTORY
    private static final boolean RUN_RAW_PROBE_PORTFOLIO_HISTORY = true;   // sonda diretta /v2/account/portfolio/history
    private static final boolean RUN_SYNC_PORTFOLIO_HISTORY_DEFAULT = true;        // sync, default server
    private static final boolean RUN_SYNC_PORTFOLIO_HISTORY_INTRADAY_CRYPTO = true; // sync, es. 7D/15Min continuous + no_reset + cashflow
    private static final boolean RUN_ASYNC_PORTFOLIO_HISTORY_CUSTOM = true;         // async, es. 1D con start/end + cashflow types

    /** Abilita verifiche specifiche sul decoder “tollerante” (Instant/BigDecimal, cardinalità allineate). */
    private static final boolean RUN_DECODER_CHECKS_PORTFOLIO_HISTORY = true;

    // ASSETS (LIST)
    private static final boolean RUN_RAW_PROBE_ASSETS = true;   // sonda diretta /v2/assets (diagnostica 401/filtri)
    private static final boolean RUN_SYNC_ASSETS_DTO = true;   // service sync -> DTO Assets (LISTA)
    private static final boolean RUN_ASYNC_ASSETS_DTO = true;   // service async -> DTO Assets (LISTA)

    // ASSET (DETAIL)
    private static final boolean RUN_RAW_PROBE_ASSET_DETAIL = true;   // sonda diretta /v2/assets/{idOrSymbol}
    private static final boolean RUN_SYNC_ASSET_DETAIL_DTO = true;   // service sync -> DTO Asset singolo
    private static final boolean RUN_ASYNC_ASSET_DETAIL_DTO = true;   // service async -> DTO Asset singolo

    // POSITIONS (LIST) — nuovi test integrati
    private static final boolean RUN_RAW_PROBE_POSITIONS = true;   // sonda diretta /v2/positions
    private static final boolean RUN_SYNC_POSITIONS_DTO = true;   // service sync -> DTO Positions (LISTA)
    private static final boolean RUN_ASYNC_POSITIONS_DTO = true;   // service async -> DTO Positions (LISTA)

    // POSITION (DETAIL) — nuovi test integrati
    private static final boolean RUN_RAW_PROBE_POSITION_DETAIL = true;   // sonda diretta /v2/positions/{idOrSymbol}
    private static final boolean RUN_SYNC_POSITION_DETAIL_DTO = true;   // service sync -> DTO Position singola (o null se 404)
    private static final boolean RUN_ASYNC_POSITION_DETAIL_DTO = true;   // service async -> DTO Position singola (o null se 404)

    // CLOSE ALL POSITIONS (DANGER)
    private static final boolean RUN_SYNC_CLOSE_ALL_POSITIONS = true;  // liquida TUTTE le posizioni (sync)
    private static final boolean RUN_ASYNC_CLOSE_ALL_POSITIONS = true;  // liquida TUTTE le posizioni (async)
    private static final boolean CLOSE_ALL_CANCEL_ORDERS = true;   // consigliare true: cancella ordini aperti prima della liquidazione (param cancel_orders)

    // CLOSE SINGLE POSITION (DANGER)
    private static final boolean RUN_SYNC_CLOSE_POSITION_BY_QTY = false;
    private static final boolean RUN_ASYNC_CLOSE_POSITION_BY_QTY = false;
    private static final boolean RUN_SYNC_CLOSE_POSITION_BY_PERCENTAGE = false;
    private static final boolean RUN_ASYNC_CLOSE_POSITION_BY_PERCENTAGE = false;

    /**
     * Asset di test per il dettaglio (/v2/assets/{idOrSymbol}).
     */
    private static final String DEFAULT_ASSET_ID_OR_SYMBOL = "AAPL"; // Cambiare se necessario per l'ambiente

    /**
     * Simbolo o assetId per il dettaglio posizione (/v2/positions/{idOrSymbol}).
     */
    private static final String DEFAULT_POSITION_ID_OR_SYMBOL = "AAPL"; // Se non hai posizioni aperte su AAPL riceverai 404 → mappato a null

    // Valori di default per i test di chiusura singola
    private static final String DEFAULT_CLOSE_SYMBOL = "AAPL"; // riuso del simbolo già definito
    private static final BigDecimal DEFAULT_CLOSE_QTY = new BigDecimal("1");           // attenzione a frazionari / asset class
    private static final BigDecimal DEFAULT_CLOSE_PERCENTAGE = new BigDecimal("100");         // 100% → chiusura totale

    /**
     * Filtri di default per /v2/assets (modificabili qui per i test).
     */
    private static final String DEFAULT_ASSETS_STATUS = "active";         // es: "active" | "inactive" | null
    private static final String DEFAULT_ASSETS_CLASS = "us_equity";      // es: "us_equity" | "crypto" | null
    private static final String DEFAULT_ASSETS_EXCHANGE = null;             // es: "NASDAQ" | "NYSE" | "OTC" | null
    private static final List<String> DEFAULT_ASSETS_ATTRIBUTES = List.of();       // es: List.of("has_options")

    /**
     * Entry point del playground (senza argomenti).
     * Esegue: PRECHECKS → RAW probes → demo CLOCK → demo ACCOUNT → demo **PORTFOLIO HISTORY** → demo ASSETS (list) → demo ASSET (detail)
     * → **demo POSITIONS (list) → demo POSITION (detail)**, secondo i toggle.
     */
    public void run() {
        LOG.info("=== TradingApiPlayground START === (endpoint={}, retries={})", ENDPOINT, ENABLE_RETRIES);

        if (RUN_PRECHECKS) {
            preflight(); // verifica credenziali e baseUrl (override o enum)
        }

        // Sonde dirette (bypassano HttpRestClient) per diagnosticare subito eventuali 401/403 indipendenti dallo stack
        if (RUN_RAW_PROBE_CLOCK) rawProbeClock();
        if (RUN_RAW_PROBE_ACCOUNT) rawProbeAccount();
        if (RUN_RAW_PROBE_PORTFOLIO_HISTORY) rawProbePortfolioHistoryDefault(); // <-- NUOVO: probe su portfolio/history
        if (RUN_RAW_PROBE_ASSETS)
            rawProbeAssets(DEFAULT_ASSETS_STATUS, DEFAULT_ASSETS_CLASS, DEFAULT_ASSETS_EXCHANGE, DEFAULT_ASSETS_ATTRIBUTES);
        if (RUN_RAW_PROBE_ASSET_DETAIL) rawProbeAssetDetail(DEFAULT_ASSET_ID_OR_SYMBOL);
        if (RUN_RAW_PROBE_POSITIONS) rawProbePositions();
        if (RUN_RAW_PROBE_POSITION_DETAIL) rawProbePositionDetail(DEFAULT_POSITION_ID_OR_SYMBOL);

        // Services per le risorse (riutilizzabili su più thread; stateless)
        AlpacaClockRestService clockService = new AlpacaClockRestService(ENDPOINT);
        AlpacaAccountRestService accountService = new AlpacaAccountRestService(ENDPOINT);
        AlpacaAssetsRestService assetsService = new AlpacaAssetsRestService(ENDPOINT);
        AlpacaPositionsRestService positionsService = new AlpacaPositionsRestService(ENDPOINT);

        /* === CLOCK === */
        if (RUN_SYNC_CLOCK) demoSyncClock(clockService);
        if (RUN_ASYNC_CLOCK_DTO) demoAsyncClockDecoded(clockService);

        /* === ACCOUNT === */
        if (RUN_SYNC_ACCOUNT_DTO) demoSyncAccount(accountService);
        if (RUN_ASYNC_ACCOUNT_DTO) demoAsyncAccountDecoded(accountService);

        /* === PORTFOLIO HISTORY (NUOVO BLOCCO) ===
           Tre micro-demo:
           1) DEFAULT (sync): nessun query param → periodo 1M, timeframe auto; stampa conteggi e sample.
           2) INTRADAY CRYPTO-STYLE (sync): period=7D, timeframe=15Min, intraday_reporting=continuous, pnl_reset=no_reset, cashflow_types=ALL.
           3) CUSTOM (async): timeframe=1D con start/end (ultimi 21 giorni), cashflow_types=["CFEE","DIV"].
           Nota: la API permette solo 2 tra {start,end,period}. */
        if (RUN_SYNC_PORTFOLIO_HISTORY_DEFAULT) demoSyncPortfolioHistoryDefault(accountService);
        if (RUN_SYNC_PORTFOLIO_HISTORY_INTRADAY_CRYPTO) demoSyncPortfolioHistoryIntradayCrypto(accountService);
        if (RUN_ASYNC_PORTFOLIO_HISTORY_CUSTOM) demoAsyncPortfolioHistoryCustom(accountService);

        LOG.info("[Decoder] Portfolio History: i metodi getAccountPortfolioHistory* utilizzano il nuovo decoder tollerante (epoch seconds/millis e ISO → Instant; numerici → BigDecimal; date-only → Instant alle 00:00Z).");
        /* === ASSETS (LIST) === */
        if (RUN_SYNC_ASSETS_DTO) demoSyncAssets(assetsService,
                DEFAULT_ASSETS_STATUS, DEFAULT_ASSETS_CLASS, DEFAULT_ASSETS_EXCHANGE, DEFAULT_ASSETS_ATTRIBUTES);
        if (RUN_ASYNC_ASSETS_DTO) demoAsyncAssetsDecoded(assetsService,
                DEFAULT_ASSETS_STATUS, DEFAULT_ASSETS_CLASS, DEFAULT_ASSETS_EXCHANGE, DEFAULT_ASSETS_ATTRIBUTES);

        /* === ASSET (DETAIL) === */
        if (RUN_SYNC_ASSET_DETAIL_DTO) demoSyncAssetDetail(assetsService, DEFAULT_ASSET_ID_OR_SYMBOL);
        if (RUN_ASYNC_ASSET_DETAIL_DTO) demoAsyncAssetDetailDecoded(assetsService, DEFAULT_ASSET_ID_OR_SYMBOL);

                /* === POSITIONS (LIST) ===
                   - Valida la lista posizioni aperte (eventualmente vuota).
                   - Effettua controlli "sanity" sui campi numerici (qty, marketValue, ecc.) senza assumere presenza di posizioni. */
        if (RUN_SYNC_POSITIONS_DTO) demoSyncPositions(positionsService);
        if (RUN_ASYNC_POSITIONS_DTO) demoAsyncPositionsDecoded(positionsService);

                /* === POSITION (DETAIL)
                   - Recupera la singola posizione per simbolo/assetId.
                   - Gestisce correttamente il caso 404 (null dal service) senza generare errori. */
        if (RUN_SYNC_POSITION_DETAIL_DTO) demoSyncPositionDetail(positionsService, DEFAULT_POSITION_ID_OR_SYMBOL);
        if (RUN_ASYNC_POSITION_DETAIL_DTO)
            demoAsyncPositionDetailDecoded(positionsService, DEFAULT_POSITION_ID_OR_SYMBOL);

                /* === DANGER ZONE: CLOSE OPERATIONS ===
                Queste operazioni producono side-effect (liquidazioni). Attivarle consapevolmente. */
        if (RUN_SYNC_CLOSE_ALL_POSITIONS) {
            LOG.warn("[DANGER] Esecuzione demoSyncCloseAllPositions: verranno liquidate tutte le posizioni (cancel_orders={})", CLOSE_ALL_CANCEL_ORDERS);
            demoSyncCloseAllPositions(positionsService, CLOSE_ALL_CANCEL_ORDERS);
        }
        if (RUN_ASYNC_CLOSE_ALL_POSITIONS) {
            LOG.warn("[DANGER] Esecuzione demoAsyncCloseAllPositions: verranno liquidate tutte le posizioni (cancel_orders={})", CLOSE_ALL_CANCEL_ORDERS);
            demoAsyncCloseAllPositions(positionsService, CLOSE_ALL_CANCEL_ORDERS);
        }

        if (RUN_SYNC_CLOSE_POSITION_BY_QTY) {
            LOG.warn("[DANGER] Esecuzione demoSyncClosePositionByQty: symbol={}, qty={}", DEFAULT_CLOSE_SYMBOL, DEFAULT_CLOSE_QTY);
            demoSyncClosePositionByQty(positionsService, DEFAULT_CLOSE_SYMBOL, DEFAULT_CLOSE_QTY);
        }
        if (RUN_ASYNC_CLOSE_POSITION_BY_QTY) {
            LOG.warn("[DANGER] Esecuzione demoAsyncClosePositionByQty: symbol={}, qty={}", DEFAULT_CLOSE_SYMBOL, DEFAULT_CLOSE_QTY);
            demoAsyncClosePositionByQty(positionsService, DEFAULT_CLOSE_SYMBOL, DEFAULT_CLOSE_QTY);
        }

        if (RUN_SYNC_CLOSE_POSITION_BY_PERCENTAGE) {
            LOG.warn("[DANGER] Esecuzione demoSyncClosePositionByPercentage: symbol={}, percentage={}", DEFAULT_CLOSE_SYMBOL, DEFAULT_CLOSE_PERCENTAGE);
            demoSyncClosePositionByPercentage(positionsService, DEFAULT_CLOSE_SYMBOL, DEFAULT_CLOSE_PERCENTAGE);
        }
        if (RUN_ASYNC_CLOSE_POSITION_BY_PERCENTAGE) {
            LOG.warn("[DANGER] Esecuzione demoAsyncClosePositionByPercentage: symbol={}, percentage={}", DEFAULT_CLOSE_SYMBOL, DEFAULT_CLOSE_PERCENTAGE);
            demoAsyncClosePositionByPercentage(positionsService, DEFAULT_CLOSE_SYMBOL, DEFAULT_CLOSE_PERCENTAGE);
        }

        LOG.info("=== TradingApiPlayground END ===");
    }

            /* ======================================================================
               PRECHECKS
               ====================================================================== */

    /**
     * Preflight:
     * - Controlla presenza credenziali (API key/secret) e le logga offuscate.
     * - Evidenzia eventuale override di baseUrl via ENV (altrimenti usa l'URL dell'enum ENDPOINT).
     * - Ricorda di far combaciare **ambiente** e **chiavi** (PAPER ↔ PAPER, PROD ↔ PROD).
     */
    private static void preflight() {
        String key = trimToNull(Env.get("APCA_API_KEY_ID"));
        String secret = trimToNull(Env.get("APCA_API_SECRET_KEY"));

        LOG.info("[Preflight] Expected auth headers = [APCA-API-KEY-ID, APCA-API-SECRET-KEY]");

        if (key == null || secret == null) {
            LOG.warn("[Preflight] Missing API credentials: APCA_API_KEY_ID={}  APCA_API_SECRET_KEY={}",
                    present(key), present(secret));
        } else {
            LOG.info("[Preflight] Credentials present: key={}, secret={}", mask(key), mask(secret));
        }

        // Se presente override ENV, lo segnaliamo; altrimenti useremo l'URL previsto dall'enum.
        String overrideKey = switch (ENDPOINT) {
            case API_V2_PAPER_TRADING -> "ALPACA_TRADING_PAPER_API_V2_URL";
            case API_V2_PRODUCTION_TRADING -> "ALPACA_TRADING_PRODUCTION_API_V2_URL";
            default -> null;
        };
        if (overrideKey != null) {
            String overrideVal = trimToNull(Env.get(overrideKey));
            if (overrideVal == null) {
                LOG.info("[Preflight] No baseUrl override (enum default will be used): {}", ENDPOINT.baseUrl());
            } else {
                LOG.info("[Preflight] baseUrl override: {} = {}", overrideKey, overrideVal);
            }
        }

        if (key != null && secret != null) {
            if (ENDPOINT == AlpacaRestBaseEndpoints.API_V2_PAPER_TRADING) {
                LOG.info("[Preflight] PAPER endpoint selezionato -> usa chiavi PAPER.");
            } else {
                LOG.info("[Preflight] PRODUCTION endpoint selezionato -> usa chiavi PROD.");
            }
        }
    }

    /* ======================================================================
       RAW PROBE (diagnostica 401 fuori dal tuo httpRestClient)
       ====================================================================== */

    /**
     * Sonda diretta a <b>/v2/clock</b> con Java 21 HttpClient:
     * - Imposta manualmente gli header APCA-API-KEY-ID / APCA-API-SECRET-KEY / Accept / User-Agent.
     * - Se ottieni 200/JSON qui, ma 401 via service, il problema è nel client o negli header iniettati.
     */
    private static void rawProbeClock() {
        String key = trimToNull(Env.get("APCA_API_KEY_ID"));
        String secret = trimToNull(Env.get("APCA_API_SECRET_KEY"));
        if (key == null || secret == null) {
            LOG.warn("[RawProbeClock] Skip: mancano credenziali.");
            return;
        }

        String base = effectiveBaseUrl();
        String url = HttpUtils.joinUrl(base, "clock");

        LOG.info("[RawProbeClock] GET {}", url);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("APCA-API-KEY-ID", key)
                    .header("APCA-API-SECRET-KEY", secret)
                    .header("Accept", "application/json")
                    .header("User-Agent", "ApcaRestClient/Playground")
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            int sc = resp.statusCode();
            String preview = safePreview(resp.body(), 256);

            LOG.info("[RawProbeClock] status={}  bodyPreview={}", sc, preview);

            if (sc == 401) {
                LOG.warn("""
                        [RawProbeClock] 401 Unauthorized anche con sonda diretta.
                          -> Possibili cause:
                             1) Chiavi non valide per l'ambiente selezionato (PAPER vs PROD).
                             2) Chiavi revocate/ruotate sulla dashboard.
                             3) IP whitelisting attivo per la key e IP locale non ammesso.
                             4) Account/permission non abilitate al Trading API.
                        """);
            } else if (sc >= 400) {
                LOG.warn("[RawProbeClock] Non-OK status={} (controlla messaggio/JSON dell'errore nell'anteprima)", sc);
            }
        } catch (Exception e) {
            LOG.error("[RawProbeClock] Errore nella chiamata diretta: {}", e.toString(), e);
        }
    }

    /**
     * Sonda diretta a <b>/v2/account</b> con Java 21 HttpClient:
     * - Identica alla probe su clock ma cambia la risorsa; utile per discriminare errori di permessi lato account.
     */
    private static void rawProbeAccount() {
        String key = trimToNull(Env.get("APCA_API_KEY_ID"));
        String secret = trimToNull(Env.get("APCA_API_SECRET_KEY"));
        if (key == null || secret == null) {
            LOG.warn("[RawProbeAccount] Skip: mancano credenziali.");
            return;
        }

        String base = effectiveBaseUrl();
        String url = HttpUtils.joinUrl(base, "account");

        LOG.info("[RawProbeAccount] GET {}", url);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("APCA-API-KEY-ID", key)
                    .header("APCA-API-SECRET-KEY", secret)
                    .header("Accept", "application/json")
                    .header("User-Agent", "ApcaRestClient/Playground")
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            int sc = resp.statusCode();
            String preview = safePreview(resp.body(), 256);

            LOG.info("[RawProbeAccount] status={}  bodyPreview={}", sc, preview);

            if (sc == 401) {
                LOG.warn("""
                        [RawProbeAccount] 401 Unauthorized anche con sonda diretta.
                          -> Possibili cause:
                             1) Chiavi non valide per l'ambiente selezionato (PAPER vs PROD).
                             2) Chiavi revocate/ruotate sulla dashboard.
                             3) IP whitelisting attivo per la key e IP locale non ammesso.
                             4) Account/permission non abilitate alle Trading API.
                        """);
            } else if (sc >= 400) {
                LOG.warn("[RawProbeAccount] Non-OK status={} (controlla messaggio/JSON dell'errore nell'anteprima)", sc);
            }
        } catch (Exception e) {
            LOG.error("[RawProbeAccount] Errore nella chiamata diretta: {}", e.toString(), e);
        }
    }

    /**
     * Sonda diretta a <b>/v2/account/portfolio/history</b>:
     * - Utile per verificare 200/401 e la presenza degli array attesi (timestamp/equity/profit_loss/...).
     * - Qui includiamo cashflow_types=ALL per testare anche la sezione cashflow.
     */
    private static void rawProbePortfolioHistoryDefault() {
        String key = trimToNull(Env.get("APCA_API_KEY_ID"));
        String secret = trimToNull(Env.get("APCA_API_SECRET_KEY"));
        if (key == null || secret == null) {
            LOG.warn("[RawProbePortfolioHistory] Skip: mancano credenziali.");
            return;
        }

        String base = effectiveBaseUrl();
        String url = HttpUtils.joinUrl(base, "account/portfolio/history");

        Map<String, String> q = new HashMap<>();
        // default periodo 1M; chiediamo anche cashflow per arricchire la verifica
        q.put("cashflow_types", "ALL");
        url = HttpUtils.setQueryParams(url, q);

        LOG.info("[RawProbePortfolioHistory] GET {}", url);

        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("APCA-API-KEY-ID", key)
                    .header("APCA-API-SECRET-KEY", secret)
                    .header("Accept", "application/json")
                    .header("User-Agent", "ApcaRestClient/Playground")
                    .GET().build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            int sc = resp.statusCode();
            String preview = safePreview(resp.body(), 256);
            LOG.info("[RawProbePortfolioHistory] status={} bodyPreview={}", sc, preview);

            if (sc == 401) {
                LOG.warn("""
                        [RawProbePortfolioHistory] 401 Unauthorized anche con sonda diretta.
                          -> Possibili cause:
                             1) Chiavi non valide per l'ambiente selezionato (PAPER vs PROD).
                             2) Chiavi revocate/ruotate sulla dashboard.
                             3) IP whitelisting attivo per la key e IP locale non ammesso.
                             4) Account/permission non abilitate alle Trading API.
                        """);
            } else if (sc >= 400) {
                LOG.warn("[RawProbePortfolioHistory] Non-OK status={} (controlla messaggio/JSON dell'errore nell'anteprima)", sc);
            }
        } catch (Exception e) {
            LOG.error("[RawProbePortfolioHistory] Errore nella chiamata diretta: {}", e.toString(), e);
        }
    }

    /**
     * Sonda diretta a <b>/v2/assets</b> con Java 21 HttpClient:
     * - Imposta gli stessi header d'autenticazione e consente filtri via querystring (status, asset_class, exchange, attributes).
     * - Utile per verificare 401/403 e la corretta composizione dei parametri prima di passare dallo stack REST.
     */
    private static void rawProbeAssets(String status, String assetClass, String exchange, List<String> attributes) {
        String key = trimToNull(Env.get("APCA_API_KEY_ID"));
        String secret = trimToNull(Env.get("APCA_API_SECRET_KEY"));
        if (key == null || secret == null) {
            LOG.warn("[RawProbeAssets] Skip: mancano credenziali.");
            return;
        }

        String base = effectiveBaseUrl();
        String url = HttpUtils.joinUrl(base, "assets");
        String qs = buildAssetsQueryString(status, assetClass, exchange, attributes);
        if (!qs.isBlank()) url = url + "?" + qs;

        LOG.info("[RawProbeAssets] GET {}", url);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("APCA-API-KEY-ID", key)
                    .header("APCA-API-SECRET-KEY", secret)
                    .header("Accept", "application/json")
                    .header("User-Agent", "ApcaRestClient/Playground")
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            int sc = resp.statusCode();
            String preview = safePreview(resp.body(), 256);

            LOG.info("[RawProbeAssets] status={}  bodyPreview={}", sc, preview);

            if (sc == 401) {
                LOG.warn("""
                        [RawProbeAssets] 401 Unauthorized anche con sonda diretta.
                          -> Possibili cause:
                             1) Chiavi non valide per l'ambiente selezionato (PAPER vs PROD).
                             2) Chiavi revocate/ruotate sulla dashboard.
                             3) IP whitelisting attivo per la key e IP locale non ammesso.
                             4) Account/permission non abilitate alle Trading API.
                        """);
            } else if (sc >= 400) {
                LOG.warn("[RawProbeAssets] Non-OK status={} (controlla messaggio/JSON dell'errore nell'anteprima)", sc);
            }
        } catch (Exception e) {
            LOG.error("[RawProbeAssets] Errore nella chiamata diretta: {}", e.toString(), e);
        }
    }

    /**
     * Sonda diretta a <b>/v2/assets/{idOrSymbol}</b> con Java 21 HttpClient:
     * - Utile per verificare 404/not found (asset inesistente) o 401/403.
     * - Consente di discriminare problemi sui path param rispetto ai filtri di lista.
     */
    private static void rawProbeAssetDetail(String idOrSymbol) {
        String key = trimToNull(Env.get("APCA_API_KEY_ID"));
        String secret = trimToNull(Env.get("APCA_API_SECRET_KEY"));
        if (key == null || secret == null) {
            LOG.warn("[RawProbeAssetDetail] Skip: mancano credenziali.");
            return;
        }

        if (idOrSymbol == null || idOrSymbol.isBlank()) {
            LOG.warn("[RawProbeAssetDetail] Skip: idOrSymbol non valorizzato.");
            return;
        }

        String base = effectiveBaseUrl();
        String url = HttpUtils.joinUrl(base, "assets/" + encode(idOrSymbol));

        LOG.info("[RawProbeAssetDetail] GET {}", url);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("APCA-API-KEY-ID", key)
                    .header("APCA-API-SECRET-KEY", secret)
                    .header("Accept", "application/json")
                    .header("User-Agent", "ApcaRestClient/Playground")
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            int sc = resp.statusCode();
            String preview = safePreview(resp.body(), 256);

            LOG.info("[RawProbeAssetDetail] status={}  bodyPreview={}", sc, preview);

            if (sc == 401) {
                LOG.warn("""
                        [RawProbeAssetDetail] 401 Unauthorized anche con sonda diretta.
                          -> Possibili cause:
                             1) Chiavi non valide per l'ambiente selezionato (PAPER vs PROD).
                             2) Chiavi revocate/ruotate sulla dashboard.
                             3) IP whitelisting attivo per la key e IP locale non ammesso.
                             4) Account/permission non abilitate alle Trading API.
                        """);
            } else if (sc == 404) {
                LOG.warn("[RawProbeAssetDetail] 404 Not Found per idOrSymbol='{}'. Verifica simbolo/ID e ambiente.", idOrSymbol);
            } else if (sc >= 400) {
                LOG.warn("[RawProbeAssetDetail] Non-OK status={} (controlla messaggio/JSON dell'errore nell'anteprima)", sc);
            }
        } catch (Exception e) {
            LOG.error("[RawProbeAssetDetail] Errore nella chiamata diretta: {}", e.toString(), e);
        }
    }

    /**
     * Sonda diretta a <b>/v2/positions</b>:
     * - Verifica rapidamente 200/401 e struttura generica della risposta.
     * - Non assume la presenza di posizioni (lista potrebbe essere vuota).
     */
    private static void rawProbePositions() {
        String key = trimToNull(Env.get("APCA_API_KEY_ID"));
        String secret = trimToNull(Env.get("APCA_API_SECRET_KEY"));
        if (key == null || secret == null) {
            LOG.warn("[RawProbePositions] Skip: mancano credenziali.");
            return;
        }

        String base = effectiveBaseUrl();
        String url = HttpUtils.joinUrl(base, "positions");

        LOG.info("[RawProbePositions] GET {}", url);

        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("APCA-API-KEY-ID", key)
                    .header("APCA-API-SECRET-KEY", secret)
                    .header("Accept", "application/json")
                    .header("User-Agent", "ApcaRestClient/Playground")
                    .GET().build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            int sc = resp.statusCode();
            String preview = safePreview(resp.body(), 256);
            LOG.info("[RawProbePositions] status={} bodyPreview={}", sc, preview);

            if (sc == 401) {
                LOG.warn("""
                        [RawProbePositions] 401 Unauthorized anche con sonda diretta.
                          -> Possibili cause:
                             1) Chiavi non valide per l'ambiente selezionato (PAPER vs PROD).
                             2) Chiavi revocate/ruotate sulla dashboard.
                             3) IP whitelisting attivo per la key e IP locale non ammesso.
                             4) Account/permission non abilitate alle Trading API.
                        """);
            } else if (sc >= 400) {
                LOG.warn("[RawProbePositions] Non-OK status={} (controlla messaggio/JSON dell'errore nell'anteprima)", sc);
            }
        } catch (Exception e) {
            LOG.error("[RawProbePositions] Errore nella chiamata diretta: {}", e.toString(), e);
        }
    }

    /**
     * Sonda diretta a <b>/v2/positions/{idOrSymbol}</b>:
     * - Utile per verificare la corretta gestione del 404 (nessuna posizione aperta → 404).
     * - Il service mappa 404 a null; qui controlliamo lo status raw.
     */
    private static void rawProbePositionDetail(String idOrSymbol) {
        String key = trimToNull(Env.get("APCA_API_KEY_ID"));
        String secret = trimToNull(Env.get("APCA_API_SECRET_KEY"));
        if (key == null || secret == null) {
            LOG.warn("[RawProbePositionDetail] Skip: mancano credenziali.");
            return;
        }
        if (idOrSymbol == null || idOrSymbol.isBlank()) {
            LOG.warn("[RawProbePositionDetail] Skip: idOrSymbol non valorizzato.");
            return;
        }

        String base = effectiveBaseUrl();
        String url = HttpUtils.joinUrl(base, "positions/" + encode(idOrSymbol));

        LOG.info("[RawProbePositionDetail] GET {}", url);

        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("APCA-API-KEY-ID", key)
                    .header("APCA-API-SECRET-KEY", secret)
                    .header("Accept", "application/json")
                    .header("User-Agent", "ApcaRestClient/Playground")
                    .GET().build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            int sc = resp.statusCode();
            String preview = safePreview(resp.body(), 256);
            LOG.info("[RawProbePositionDetail] status={} bodyPreview={}", sc, preview);

            if (sc == 401) {
                LOG.warn("""
                        [RawProbePositionDetail] 401 Unauthorized anche con sonda diretta.
                          -> Possibili cause:
                             1) Chiavi non valide per l'ambiente selezionato (PAPER vs PROD).
                             2) Chiavi revocate/ruotate sulla dashboard.
                             3) IP whitelisting attivo per la key e IP locale non ammesso.
                             4) Account/permission non abilitate alle Trading API.
                        """);
            } else if (sc == 404) {
                LOG.warn("[RawProbePositionDetail] 404 Not Found per idOrSymbol='{}'. Nessuna posizione aperta per il simbolo/ID.", idOrSymbol);
            } else if (sc >= 400) {
                LOG.warn("[RawProbePositionDetail] Non-OK status={} (controlla messaggio/JSON dell'errore nell'anteprima)", sc);
            }
        } catch (Exception e) {
            LOG.error("[RawProbePositionDetail] Errore nella chiamata diretta: {}", e.toString(), e);
        }
    }

    /**
     * Calcola il baseUrl effettivo: se presente override ENV lo usa, altrimenti ritorna l'URL dall'enum ENDPOINT.
     */
    private static String effectiveBaseUrl() {
        String override = switch (ENDPOINT) {
            case API_V2_PAPER_TRADING -> trimToNull(Env.get("ALPACA_TRADING_PAPER_API_V2_URL"));
            case API_V2_PRODUCTION_TRADING -> trimToNull(Env.get("ALPACA_TRADING_PRODUCTION_API_V2_URL"));
            default -> null;
        };
        return override != null ? override : ENDPOINT.baseUrl();
    }

    /* ======================================================================
       Demo SYNC (service) - CLOCK
       ====================================================================== */

    /**
     * Demo sincrona dell'orologio di mercato:
     * - Usa {@link AlpacaClockRestService#getMarketClockInfo(boolean, String)} con retry opzionale.
     * - Stampa JSON "pretty" e tempi di risposta.
     */
    private static void demoSyncClock(AlpacaClockRestService service) {
        LOG.info("[SYNC] getMarketClockInfo()");
        Instant t0 = Instant.now();
        try {
            AlpacaMarketClockInfoResponse dto = service.getMarketClockInfo(ENABLE_RETRIES,DEFAULT_SINGLE_ACC_ID_RATE_LIMIT);
            Instant t1 = Instant.now();
            LOG.info("[SYNC] SUCCESS in {} ms\n{}", ms(t0, t1), pretty(dto));
        } catch (ApcaRestClientException e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] ERROR in {} ms -> {}", ms(t0, t1), e.getMessage(), e);
            authHintsIf401(e.getMessage());
        } catch (Exception e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] UNEXPECTED ERROR in {} ms", ms(t0, t1), e);
        }
    }

    /* ======================================================================
       Demo ASYNC (service) - CLOCK tipizzata DTO
       ====================================================================== */

    /**
     * Demo asincrona dell'orologio di mercato:
     * - Usa {@link AlpacaClockRestService#getAsyncMarketClockInfo(boolean, String)}.
     * - Collega thenAccept/exceptionally per logging e diagnosi.
     */
    private static void demoAsyncClockDecoded(AlpacaClockRestService service) {
        LOG.info("[ASYNC-DTO] getAsyncMarketClockInfo()");
        Instant t0 = Instant.now();

        CompletableFuture<AlpacaMarketClockInfoResponse> fut =
                service.getAsyncMarketClockInfo(ENABLE_RETRIES, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT);

        fut.thenAccept(dto -> {
                    Instant t1 = Instant.now();
                    LOG.info("[ASYNC-DTO] SUCCESS in {} ms\n{}", ms(t0, t1), pretty(dto));
                })
                .exceptionally(ex -> {
                    Instant t1 = Instant.now();
                    String msg = rootMessage(ex);
                    LOG.error("[ASYNC-DTO] ERROR in {} ms -> {}", ms(t0, t1), msg, ex);
                    authHintsIf401(msg);
                    return null;
                });

        blockForDemo(fut, 10);
    }

    /* ======================================================================
       Demo SYNC (service) - ACCOUNT tipizzata DTO
       ====================================================================== */

    /**
     * Demo sincrona dei dettagli account:
     * - Usa {@link AlpacaAccountRestService#getAccountDetails(boolean, String)} con retry opzionale.
     * - Stampa JSON "pretty" + un riepilogo essenziale (currency/cash/equity/buying power/PDT).
     */
    private static void demoSyncAccount(AlpacaAccountRestService service) {
        LOG.info("[SYNC] getAccountDetails()");
        Instant t0 = Instant.now();
        try {
            AlpacaAccountDetailsResponse dto = service.getAccountDetails(ENABLE_RETRIES, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT);
            Instant t1 = Instant.now();
            LOG.info("[SYNC] SUCCESS in {} ms\n{}", ms(t0, t1), pretty(dto));

            // Estratti salienti utili per diagnostica rapida (valori monetari come BigDecimal)
            BigDecimal cash = dto.cash();
            BigDecimal equity = dto.equity();
            BigDecimal buyingPower = dto.buyingPower();
            LOG.info("[SYNC] Summary: currency={} cash={} equity={} buying_power={} pdt={}",
                    dto.currency(), cash, equity, buyingPower, dto.patternDayTrader());
        } catch (ApcaRestClientException e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] ERROR in {} ms -> {}", ms(t0, t1), e.getMessage(), e);
            authHintsIf401(e.getMessage());
        } catch (Exception e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] UNEXPECTED ERROR in {} ms", ms(t0, t1), e);
        }
    }

            /* ======================================================================
               Demo ASYNC (service) - ACCOUNT tipizzata DTO
               ====================================================================== */

    /**
     * Demo asincrona dei dettagli account (DTO):
     * - Usa {@link AlpacaAccountRestService#getAsyncAccountDetails(boolean, String)}.
     * - Collega thenAccept/exceptionally per logging e diagnosi.
     */
    private static void demoAsyncAccountDecoded(AlpacaAccountRestService service) {
        LOG.info("[ASYNC-DTO] getAsyncAccountDetails()");
        Instant t0 = Instant.now();

        CompletableFuture<AlpacaAccountDetailsResponse> fut =
                service.getAsyncAccountDetails(ENABLE_RETRIES, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT);

        fut.thenAccept(dto -> {
                    Instant t1 = Instant.now();
                    LOG.info("[ASYNC-DTO] SUCCESS in {} ms\n{}", ms(t0, t1), pretty(dto));
                    LOG.info("[ASYNC-DTO] Summary: currency={} cash={} equity={} buying_power={} pdt={}",
                            dto.currency(), dto.cash(), dto.equity(), dto.buyingPower(), dto.patternDayTrader());
                })
                .exceptionally(ex -> {
                    Instant t1 = Instant.now();
                    String msg = rootMessage(ex);
                    LOG.error("[ASYNC-DTO] ERROR in {} ms -> {}", ms(t0, t1), msg, ex);
                    authHintsIf401(msg);
                    return null;
                });

        blockForDemo(fut, 10);
    }

    /* ======================================================================
       Demo SYNC (service) - PORTFOLIO HISTORY (NUOVO)
       ====================================================================== */

    /**
     * Demo sincrona (DEFAULT):
     * - Chiama {@link AlpacaAccountRestService#getAccountPortfolioHistory(boolean, String)} senza parametri → default server (period=1M).
     * - Logga dimensioni serie, timeframe, baseValue/baseValueAsOf e un paio di punti (primo/ultimo).
     * - Richiede cashflow_types=ALL? No: qui volutamente default puro (per confronto con le altre demo).
     */
    private static void demoSyncPortfolioHistoryDefault(AlpacaAccountRestService service) {
        LOG.info("[SYNC] getAccountPortfolioHistory() – DEFAULT server (period=1M, timeframe auto)");
        Instant t0 = Instant.now();
        try {
            AlpacaPortfolioHistoryResponse dto = service.getAccountPortfolioHistory(ENABLE_RETRIES, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT);
            Instant t1 = Instant.now();

            int n = dto.timestamp() != null ? dto.timestamp().size() : -1;
            LOG.info("[SYNC] PORTFOLIO-HISTORY SUCCESS in {} ms (points={}) timeframe={} baseValue={} baseValueAsOf={}",
                    ms(t0, t1), n, dto.timeframe(), dto.baseValue(), dto.baseValueAsOf());

            // Stampa 1° e ultimo punto (se presenti)
            if (n > 0) {
                int last = n - 1;
                LOG.info("[SYNC] First:  t={} eq={} pnl={} pnl%={}",
                        dto.timestamp().get(0), safeList(dto.equity(), 0), safeList(dto.profitLoss(), 0), safeList(dto.profitLossPct(), 0));
                LOG.info("[SYNC] Last:   t={} eq={} pnl={} pnl%={}",
                        dto.timestamp().get(last), safeList(dto.equity(), last), safeList(dto.profitLoss(), last), safeList(dto.profitLossPct(), last));
            }

            LOG.info("[SYNC] Preview payload:\n{}", HttpUtils.safePreview(pretty(dto), 350));
            // Verifiche di sanità sul mapping (Instant/BigDecimal) e sulle cardinalità delle serie
            if (RUN_DECODER_CHECKS_PORTFOLIO_HISTORY) {
                verifyPortfolioHistoryDecoded("DEFAULT", dto);
            }
        } catch (ApcaRestClientException e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] PORTFOLIO-HISTORY ERROR in {} ms -> {}", ms(t0, t1), e.getMessage(), e);
            authHintsIf401(e.getMessage());
        } catch (Exception e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] PORTFOLIO-HISTORY UNEXPECTED ERROR in {} ms", ms(t0, t1), e);
        }
    }

    /**
     * Demo sincrona (INTRADAY stile crypto):
     * - Esempio realistico per portafogli crypto: period=7D, timeframe=15Min, intraday_reporting=continuous, pnl_reset=no_reset.
     * - cashflow_types=ALL per includere i flussi (es. CFEE, DIV, FEE...) allineati alla serie temporale.
     */
    private static void demoSyncPortfolioHistoryIntradayCrypto(AlpacaAccountRestService service) {
        LOG.info("[SYNC] getAccountPortfolioHistory(period=7D, timeframe=15Min, intraday_reporting=continuous, pnl_reset=no_reset, cashflow_types=ALL)");
        Instant t0 = Instant.now();
        try {
            AlpacaPortfolioHistoryResponse dto = service.getAccountPortfolioHistory(
                    ENABLE_RETRIES,
                    DEFAULT_SINGLE_ACC_ID_RATE_LIMIT,
                    "7D",               // period
                    "15Min",            // timeframe (<1D)
                    "continuous",       // intraday_reporting
                    null,               // start
                    null,               // end
                    "no_reset",         // pnl_reset
                    null,               // extended_hours (deprecato)
                    "ALL"               // cashflow_types
            );
            Instant t1 = Instant.now();

            int n = dto.timestamp() != null ? dto.timestamp().size() : -1;
            LOG.info("[SYNC] PORTFOLIO-HISTORY (CRYPTO) SUCCESS in {} ms (points={}) timeframe={} baseValue={} baseValueAsOf={} cashflowKeys={}",
                    ms(t0, t1), n, dto.timeframe(), dto.baseValue(), dto.baseValueAsOf(),
                    dto.cashflow() != null ? dto.cashflow().keySet() : List.of());

            // Se il cashflow contiene CFEE o DIV, mostriamo l'ultimo valore
            if (n > 0 && dto.cashflow() != null && !dto.cashflow().isEmpty()) {
                int last = n - 1;
                for (String k : List.of("CFEE", "DIV", "FEE")) {
                    List<BigDecimal> series = dto.cashflow().get(k);
                    if (series != null && series.size() == n) {
                        LOG.info("[SYNC] cashflow[{}] last={} (aligned to timestamp[{}]={})", k, series.get(last), last, dto.timestamp().get(last));
                    }
                }
            }

            LOG.info("[SYNC] Preview payload:\n{}", HttpUtils.safePreview(pretty(dto), 350));
            // Verifiche di sanità sul mapping (Instant/BigDecimal) e sulle cardinalità delle serie
             if (RUN_DECODER_CHECKS_PORTFOLIO_HISTORY) {
                 verifyPortfolioHistoryDecoded("CRYPTO", dto);
             }
        } catch (ApcaRestClientException e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] PORTFOLIO-HISTORY (CRYPTO) ERROR in {} ms -> {}", ms(t0, t1), e.getMessage(), e);
            authHintsIf401(e.getMessage());
        } catch (Exception e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] PORTFOLIO-HISTORY (CRYPTO) UNEXPECTED ERROR in {} ms", ms(t0, t1), e);
        }
    }

    /* ======================================================================
       Demo ASYNC (service) - PORTFOLIO HISTORY (NUOVO)
       ====================================================================== */

    /**
     * Demo asincrona (CUSTOM):
     * - Esempio timeframe=1D con finestra definita da start/end (ultimi 21 giorni). Nessun 'period' (rispettiamo la regola: max 2 tra start/end/period).
     * - cashflow_types = ["CFEE", "DIV"] per dimostrare l’overload con lista → CSV.
     * - intraday_reporting non ha effetto con timeframe=1D (coerente con doc).
     */
    private static void demoAsyncPortfolioHistoryCustom(AlpacaAccountRestService service) {
        Instant now = Instant.now();
        Instant start = now.minus(Duration.ofDays(21));
        Instant end = now;

        LOG.info("[ASYNC-DTO] getAsyncAccountPortfolioHistory(timeframe=1D, start={}, end={}, cashflow_types=[CFEE,DIV])", start, end);
        Instant t0 = Instant.now();

        CompletableFuture<AlpacaPortfolioHistoryResponse> fut =
                service.getAsyncAccountPortfolioHistory(
                        ENABLE_RETRIES,
                        DEFAULT_SINGLE_ACC_ID_RATE_LIMIT,
                        null,           // period (omesso: usiamo start+end)
                        "1D",           // timeframe
                        null,           // intraday_reporting
                        start,
                        end,
                        null,           // pnl_reset (rilevante solo per <1D)
                        null,           // extended_hours (deprecato)
                        List.of("CFEE", "DIV") // cashflow_types
                );

        fut.thenAccept(dto -> {
                    Instant t1 = Instant.now();
                    int n = dto.timestamp() != null ? dto.timestamp().size() : -1;
                    LOG.info("[ASYNC-DTO] PORTFOLIO-HISTORY (CUSTOM) SUCCESS in {} ms (points={}) timeframe={} baseValue={} baseValueAsOf={} cashflowKeys={}",
                            ms(t0, t1), n, dto.timeframe(), dto.baseValue(), dto.baseValueAsOf(),
                            dto.cashflow() != null ? dto.cashflow().keySet() : List.of());

                    if (n > 0) {
                        int last = n - 1;
                        LOG.info("[ASYNC-DTO] Last: t={} eq={} pnl={} pnl%={}",
                                dto.timestamp().get(last),
                                safeList(dto.equity(), last),
                                safeList(dto.profitLoss(), last),
                                safeList(dto.profitLossPct(), last));
                    }

                    LOG.info("[ASYNC-DTO] Preview payload:\n{}", HttpUtils.safePreview(pretty(dto), 350));

                    // Verifiche di sanità sul mapping (Instant/BigDecimal) e sulle cardinalità delle serie
                     if (RUN_DECODER_CHECKS_PORTFOLIO_HISTORY) {
                        verifyPortfolioHistoryDecoded("CUSTOM-ASYNC", dto);
                     }
                })
                .exceptionally(ex -> {
                    Instant t1 = Instant.now();
                    String msg = rootMessage(ex);
                    LOG.error("[ASYNC-DTO] PORTFOLIO-HISTORY (CUSTOM) ERROR in {} ms -> {}", ms(t0, t1), msg, ex);
                    authHintsIf401(msg);
                    return null;
                });

        blockForDemo(fut, 20);
    }

            /* ======================================================================
               Demo SYNC (service) - ASSETS tipizzata DTO (LIST)
               ====================================================================== */

    /**
     * Demo sincrona degli asset:
     * - Usa {@link AlpacaAssetsRestService#getAssets(boolean, String, String, String, String, List)} con retry opzionale.
     * - Stampa JSON "pretty" e tempi di risposta; logga i filtri utilizzati per la chiamata.
     * - **Nota**: l'endpoint restituisce una **lista** di {@link AlpacaAssetResponse}; qui logghiamo l'intera lista (attenzione al volume).
     */
    private static void demoSyncAssets(AlpacaAssetsRestService service,
                                       String status, String assetClass, String exchange, List<String> attributes) {
        LOG.info("[SYNC] getAssets(status={}, class={}, exch={}, attrs={})",
                status, assetClass, exchange, attributes);
        Instant t0 = Instant.now();
        try {
            List<AlpacaAssetResponse> dto = service.getAssets(ENABLE_RETRIES, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, status, assetClass, exchange, attributes);
            Instant t1 = Instant.now();
            LOG.info("[SYNC] SUCCESS in {} ms (count={})\n{}",
                    ms(t0, t1), dto.size(), HttpUtils.safePreview(pretty(dto), 150));
        } catch (ApcaRestClientException e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] ERROR in {} ms -> {}", ms(t0, t1), e.getMessage(), e);
            authHintsIf401(e.getMessage());
        } catch (Exception e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] UNEXPECTED ERROR in {} ms", ms(t0, t1), e);
        }
    }

            /* ======================================================================
               Demo ASYNC (service) - ASSETS tipizzata DTO (LIST)
               ====================================================================== */

    /**
     * Demo asincrona degli asset (DTO):
     * - Usa {@link AlpacaAssetsRestService#getAsyncAssets(boolean, String, String, String, String, List)}.
     * - Collega thenAccept/exceptionally per logging e diagnosi; logga i filtri usati.
     * - **Nota**: l'endpoint restituisce una **lista** di {@link AlpacaAssetResponse}; qui logghiamo conteggio + payload.
     */
    private static void demoAsyncAssetsDecoded(AlpacaAssetsRestService service,
                                               String status, String assetClass, String exchange, List<String> attributes) {
        LOG.info("[ASYNC-DTO] getAsyncAssets(status={}, class={}, exch={}, attrs={})",
                status, assetClass, exchange, attributes);
        Instant t0 = Instant.now();

        CompletableFuture<List<AlpacaAssetResponse>> fut =
                service.getAsyncAssets(ENABLE_RETRIES, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, status, assetClass, exchange, attributes);

        fut.thenAccept(dto -> {
                    Instant t1 = Instant.now();
                    LOG.info("[ASYNC-DTO] SUCCESS in {} ms (count={})\n{}",
                            ms(t0, t1), dto.size(), HttpUtils.safePreview(pretty(dto), 150));
                })
                .exceptionally(ex -> {
                    Instant t1 = Instant.now();
                    String msg = rootMessage(ex);
                    LOG.error("[ASYNC-DTO] ERROR in {} ms -> {}", ms(t0, t1), msg, ex);
                    authHintsIf401(msg);
                    return null;
                });

        blockForDemo(fut, 15);
    }

            /* ======================================================================
               Demo SYNC (service) - ASSET tipizzata DTO (DETAIL)
               ====================================================================== */

    /**
     * Demo sincrona del dettaglio asset:
     * - Usa {@link AlpacaAssetsRestService#getAsset(boolean, String, String)}.
     * - Logga tempi, JSON pretty e un breve riepilogo (symbol/class/exchange).
     */
    private static void demoSyncAssetDetail(AlpacaAssetsRestService service, String idOrSymbol) {
        LOG.info("[SYNC] getAsset(idOrSymbol={})", idOrSymbol);
        Instant t0 = Instant.now();
        try {
            AlpacaAssetResponse dto = service.getAsset(ENABLE_RETRIES, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, idOrSymbol);
            Instant t1 = Instant.now();
            LOG.info("[SYNC] SUCCESS in {} ms\n{}", ms(t0, t1), pretty(dto));
            LOG.info("[SYNC] Summary: symbol={} class={} exchange={} tradable={} status={}",
                    dto.symbol(), dto.assetClass(), dto.exchange(), dto.tradable(), dto.status());
        } catch (ApcaRestClientException e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] ERROR in {} ms -> {}", ms(t0, t1), e.getMessage(), e);
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                LOG.warn("[SYNC] getAsset: 404 Not Found per idOrSymbol='{}'. Verifica simbolo/ID e ambiente.", idOrSymbol);
            }
            authHintsIf401(e.getMessage());
        } catch (Exception e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] UNEXPECTED ERROR in {} ms", ms(t0, t1), e);
        }
    }

            /* ======================================================================
               Demo ASYNC (service) - ASSET tipizzata DTO (DETAIL)
               ====================================================================== */

    /**
     * Demo asincrona del dettaglio asset:
     * - Usa {@link AlpacaAssetsRestService#getAsyncAsset(boolean, String, String)}.
     * - Collega thenAccept/exceptionally per logging e diagnosi.
     */
    private static void demoAsyncAssetDetailDecoded(AlpacaAssetsRestService service, String idOrSymbol) {
        LOG.info("[ASYNC-DTO] getAsyncAsset(idOrSymbol={})", idOrSymbol);
        Instant t0 = Instant.now();

        CompletableFuture<AlpacaAssetResponse> fut =
                service.getAsyncAsset(ENABLE_RETRIES, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, idOrSymbol);

        fut.thenAccept(dto -> {
                    Instant t1 = Instant.now();
                    LOG.info("[ASYNC-DTO] SUCCESS in {} ms\n{}", ms(t0, t1), pretty(dto));
                    LOG.info("[ASYNC-DTO] Summary: symbol={} class={} exchange={} tradable={} status={}",
                            dto.symbol(), dto.assetClass(), dto.exchange(), dto.tradable(), dto.status());
                })
                .exceptionally(ex -> {
                    Instant t1 = Instant.now();
                    String msg = rootMessage(ex);
                    LOG.error("[ASYNC-DTO] ERROR in {} ms -> {}", ms(t0, t1), msg, ex);
                    if (msg != null && msg.contains("404")) {
                        LOG.warn("[ASYNC-DTO] getAsyncAsset: 404 Not Found per idOrSymbol='{}'.", idOrSymbol);
                    }
                    authHintsIf401(msg);
                    return null;
                });

        blockForDemo(fut, 15);
    }

            /* ======================================================================
               Demo SYNC (service) - POSITIONS tipizzata DTO (LIST) — nuovi
               ====================================================================== */

    /**
     * Demo sincrona delle posizioni aperte:
     * - Usa {@link AlpacaPositionsRestService#getOpenPositions(boolean, String)}.
     * - Non assume la presenza di posizioni: gestisce lista vuota.
     * - Esegue "sanity check" su alcuni campi numerici e logga un piccolo ranking per marketValue.
     */
    private static void demoSyncPositions(AlpacaPositionsRestService service) {
        LOG.info("[SYNC] getOpenPositions()");
        Instant t0 = Instant.now();
        try {
            List<AlpacaPositionResponse> list = service.getOpenPositions(ENABLE_RETRIES, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT);
            Instant t1 = Instant.now();
            LOG.info("[SYNC] POSITIONS SUCCESS in {} ms (count={})", ms(t0, t1), list.size());

            if (list.isEmpty()) {
                LOG.info("[SYNC] Nessuna posizione aperta al momento.");
                return;
            }

            // Piccolo ranking TOP-3 per marketValue (se presente)
            list.stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing((AlpacaPositionResponse p) ->
                            Optional.ofNullable(p.marketValue()).orElse(BigDecimal.ZERO)).reversed())
                    .limit(3)
                    .forEach(p -> LOG.info("[SYNC] TOP MV → symbol={} qty={} mv={} uPL={} side={}",
                            p.symbol(), p.qty(), p.marketValue(), p.unrealizedPl(), p.side()));

            // Sanity check minimo su campi noti (non fallisce: solo warning)
            for (AlpacaPositionResponse p : list) {
                if (p == null) continue;
                if (p.qty() != null && p.qty().compareTo(BigDecimal.ZERO) < 0) {
                    LOG.warn("[SYNC] qty negativa per symbol={} qty={}", p.symbol(), p.qty());
                }
                if (p.marketValue() != null && p.marketValue().compareTo(BigDecimal.ZERO) < 0) {
                    LOG.warn("[SYNC] marketValue negativa per symbol={} mv={}", p.symbol(), p.marketValue());
                }
            }

            LOG.info("[SYNC] Preview payload:\n{}", HttpUtils.safePreview(pretty(list), 300));

        } catch (ApcaRestClientException e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] POSITIONS ERROR in {} ms -> {}", ms(t0, t1), e.getMessage(), e);
            authHintsIf401(e.getMessage());
        } catch (Exception e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] POSITIONS UNEXPECTED ERROR in {} ms", ms(t0, t1), e);
        }
    }

            /* ======================================================================
               Demo ASYNC (service) - POSITIONS tipizzata DTO (LIST) — nuovi
               ====================================================================== */

    /**
     * Demo asincrona delle posizioni aperte:
     * - Usa {@link AlpacaPositionsRestService#getAsyncOpenPosition(boolean, String)}.
     * - Non assume la presenza di posizioni: gestisce lista vuota.
     */
    private static void demoAsyncPositionsDecoded(AlpacaPositionsRestService service) {
        LOG.info("[ASYNC-DTO] getAsyncOpenPositions()");
        Instant t0 = Instant.now();

        CompletableFuture<List<AlpacaPositionResponse>> fut =
                service.getAsyncOpenPosition(ENABLE_RETRIES, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT);

        fut.thenAccept(list -> {
                    Instant t1 = Instant.now();
                    LOG.info("[ASYNC-DTO] POSITIONS SUCCESS in {} ms (count={})",
                            ms(t0, t1), list.size());

                    if (list.isEmpty()) {
                        LOG.info("[ASYNC-DTO] Nessuna posizione aperta al momento.");
                        return;
                    }

                    // Piccola preview e primo elemento "formattato"
                    AlpacaPositionResponse first = list.get(0);
                    LOG.info("[ASYNC-DTO] First → symbol={} side={} qty={} mv={} uPLpc={}",
                            first.symbol(), first.side(), first.qty(), first.marketValue(), first.unrealizedPlpc());

                    LOG.info("[ASYNC-DTO] Preview payload:\n{}",
                            HttpUtils.safePreview(pretty(list), 300));
                })
                .exceptionally(ex -> {
                    Instant t1 = Instant.now();
                    String msg = rootMessage(ex);
                    LOG.error("[ASYNC-DTO] POSITIONS ERROR in {} ms -> {}", ms(t0, t1), msg, ex);
                    authHintsIf401(msg);
                    return null;
                });

        blockForDemo(fut, 15);
    }

            /* ======================================================================
               Demo SYNC (service) - POSITION tipizzata DTO (DETAIL) — nuovi
               ====================================================================== */

    /**
     * Demo sincrona della posizione singola:
     * - Usa {@link AlpacaPositionsRestService#getOpenPosition(boolean, String, String)}.
     * - Gestisce esplicitamente il caso 404 → service ritorna null (nessuna posizione aperta sul simbolo/assetId).
     */
    private static void demoSyncPositionDetail(AlpacaPositionsRestService service, String idOrSymbol) {
        LOG.info("[SYNC] getOpenPosition(idOrSymbol={})", idOrSymbol);
        Instant t0 = Instant.now();
        try {
            AlpacaPositionResponse dto = service.getOpenPosition(ENABLE_RETRIES, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, idOrSymbol);
            Instant t1 = Instant.now();

            if (dto == null) {
                LOG.warn("[SYNC] POSITION detail: nessuna posizione aperta per '{}'. (mappato da HTTP 404) in {} ms",
                        idOrSymbol, ms(t0, t1));
                return;
            }

            LOG.info("[SYNC] POSITION SUCCESS in {} ms\n{}", ms(t0, t1), pretty(dto));
            LOG.info("[SYNC] Summary: symbol={} side={} qty={} avgEntry={} current={} mv={} uPL={} uPLpc={}",
                    dto.symbol(), dto.side(), dto.qty(), dto.avgEntryPrice(), dto.currentPrice(),
                    dto.marketValue(), dto.unrealizedPl(), dto.unrealizedPlpc());

        } catch (ApcaRestClientException e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] POSITION ERROR in {} ms -> {}", ms(t0, t1), e.getMessage(), e);
            authHintsIf401(e.getMessage());
        } catch (Exception e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] POSITION UNEXPECTED ERROR in {} ms", ms(t0, t1), e);
        }
    }

            /* ======================================================================
               Demo ASYNC (service) - POSITION tipizzata DTO (DETAIL) — nuovi
               ====================================================================== */

    /**
     * Demo asincrona della posizione singola:
     * - Usa {@link AlpacaPositionsRestService#getAsyncOpenPosition(boolean, String, String)}.
     * - Gestione 404: il future completa con null (nessuna posizione aperta).
     */
    private static void demoAsyncPositionDetailDecoded(AlpacaPositionsRestService service, String idOrSymbol) {
        LOG.info("[ASYNC-DTO] getAsyncOpenPositions(idOrSymbol={})", idOrSymbol);
        Instant t0 = Instant.now();

        CompletableFuture<AlpacaPositionResponse> fut =
                service.getAsyncOpenPosition(ENABLE_RETRIES, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, idOrSymbol);

        fut.thenAccept(dto -> {
                    Instant t1 = Instant.now();

                    if (dto == null) {
                        LOG.warn("[ASYNC-DTO] POSITION detail: nessuna posizione aperta per '{}'. (mappato da HTTP 404) in {} ms",
                                idOrSymbol, ms(t0, t1));
                        return;
                    }

                    LOG.info("[ASYNC-DTO] POSITION SUCCESS in {} ms\n{}", ms(t0, t1), pretty(dto));
                    LOG.info("[ASYNC-DTO] Summary: symbol={} side={} qty={} avgEntry={} current={} mv={} uPL={} uPLpc={}",
                            dto.symbol(), dto.side(), dto.qty(), dto.avgEntryPrice(), dto.currentPrice(),
                            dto.marketValue(), dto.unrealizedPl(), dto.unrealizedPlpc());
                })
                .exceptionally(ex -> {
                    Instant t1 = Instant.now();
                    String msg = rootMessage(ex);
                    LOG.error("[ASYNC-DTO] POSITION ERROR in {} ms -> {}", ms(t0, t1), msg, ex);
                    authHintsIf401(msg);
                    return null;
                });

        blockForDemo(fut, 15);
    }

        /* ======================================================================
           Demo SYNC – CLOSE ALL POSITIONS
           ====================================================================== */

    /**
     * Demo sincrona: liquida tutte le posizioni aperte (DELETE /v2/positions).
     * Gestisce l’esito 207 Multi-Status e logga il payload sintetico.
     */
    private static void demoSyncCloseAllPositions(AlpacaPositionsRestService service, boolean cancelOrders) {
        LOG.info("[SYNC] closeAllOpenPositions(cancel_orders={})", cancelOrders);
        Instant t0 = Instant.now();
        try {
            List<AlpacaClosePositionResponse> out = service.closeAllOpenPositions(ENABLE_RETRIES, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, cancelOrders);
            Instant t1 = Instant.now();
            LOG.info("[SYNC] CLOSE-ALL SUCCESS in {} ms (items={})", ms(t0, t1), out.size());
            LOG.info("[SYNC] Preview payload:\n{}", HttpUtils.safePreview(pretty(out), 300));
        } catch (ApcaRestClientException e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] CLOSE-ALL ERROR in {} ms -> {}", ms(t0, t1), e.getMessage(), e);
            authHintsIf401(e.getMessage());
        } catch (Exception e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] CLOSE-ALL UNEXPECTED ERROR in {} ms", ms(t0, t1), e);
        }
    }

        /* ======================================================================
           Demo ASYNC – CLOSE ALL POSITIONS
           ====================================================================== */

    /**
     * Demo asincrona: liquida tutte le posizioni aperte (DELETE /v2/positions).
     * Esito atteso: 207 con array di risultati; log sintetico + preview.
     */
    private static void demoAsyncCloseAllPositions(AlpacaPositionsRestService service, boolean cancelOrders) {
        LOG.info("[ASYNC-DTO] closeAllOpenPositionsAsync(cancel_orders={})", cancelOrders);
        Instant t0 = Instant.now();

        CompletableFuture<List<AlpacaClosePositionResponse>> fut =
                service.closeAllOpenPositionsAsync(ENABLE_RETRIES, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, cancelOrders);

        fut.thenAccept(out -> {
                    Instant t1 = Instant.now();
                    LOG.info("[ASYNC-DTO] CLOSE-ALL SUCCESS in {} ms (items={})", ms(t0, t1), out.size());
                    LOG.info("[ASYNC-DTO] Preview payload:\n{}", HttpUtils.safePreview(pretty(out), 300));
                })
                .exceptionally(ex -> {
                    Instant t1 = Instant.now();
                    String msg = rootMessage(ex);
                    LOG.error("[ASYNC-DTO] CLOSE-ALL ERROR in {} ms -> {}", ms(t0, t1), msg, ex);
                    authHintsIf401(msg);
                    return null;
                });

        blockForDemo(fut, 20);
    }

        /* ======================================================================
           Demo SYNC – CLOSE POSITION by QTY
           ====================================================================== */

    /**
     * Demo sincrona: chiude la posizione singola indicando la quantità (DELETE /v2/positions/{id}?qty=...).
     * Response: ordine creato (200 OK) oppure errore se la posizione non esiste/parametri invalidi.
     */
    private static void demoSyncClosePositionByQty(AlpacaPositionsRestService service,
                                                   String symbolOrAssetId,
                                                   BigDecimal qty) {
        LOG.info("[SYNC] closePositionByQty(symbol={}, qty={})", symbolOrAssetId, qty);
        Instant t0 = Instant.now();
        try {
            AlpacaOrderResponse order = service.closePositionByQty(ENABLE_RETRIES, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, symbolOrAssetId, qty);
            Instant t1 = Instant.now();
            LOG.info("[SYNC] CLOSE-POS(QTY) SUCCESS in {} ms | orderId={} status={} type={} side={} filledQty={}/{}",
                    ms(t0, t1), order.id(), order.status(), order.type(), order.side(),
                    order.filledQty(), order.qty());
            LOG.info("[SYNC] Order payload:\n{}", HttpUtils.safePreview(pretty(order), 300));
        } catch (ApcaRestClientException e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] CLOSE-POS(QTY) ERROR in {} ms -> {}", ms(t0, t1), e.getMessage(), e);
            authHintsIf401(e.getMessage());
        } catch (Exception e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] CLOSE-POS(QTY) UNEXPECTED ERROR in {} ms", ms(t0, t1), e);
        }
    }

        /* ======================================================================
           Demo ASYNC – CLOSE POSITION by QTY
           ====================================================================== */

    /**
     * Demo asincrona: chiude la posizione singola indicando la quantità.
     */
    private static void demoAsyncClosePositionByQty(AlpacaPositionsRestService service,
                                                    String symbolOrAssetId,
                                                    BigDecimal qty) {
        LOG.info("[ASYNC-DTO] closePositionByQtyAsync(symbol={}, qty={})", symbolOrAssetId, qty);
        Instant t0 = Instant.now();

        CompletableFuture<AlpacaOrderResponse> fut =
                service.closePositionByQtyAsync(ENABLE_RETRIES, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, symbolOrAssetId, qty);

        fut.thenAccept(order -> {
                    Instant t1 = Instant.now();
                    LOG.info("[ASYNC-DTO] CLOSE-POS(QTY) SUCCESS in {} ms | orderId={} status={} type={} side={} filledQty={}/{}",
                            ms(t0, t1), order.id(), order.status(), order.type(), order.side(),
                            order.filledQty(), order.qty());
                    LOG.info("[ASYNC-DTO] Order payload:\n{}", HttpUtils.safePreview(pretty(order), 300));
                })
                .exceptionally(ex -> {
                    Instant t1 = Instant.now();
                    String msg = rootMessage(ex);
                    LOG.error("[ASYNC-DTO] CLOSE-POS(QTY) ERROR in {} ms -> {}", ms(t0, t1), msg, ex);
                    authHintsIf401(msg);
                    return null;
                });

        blockForDemo(fut, 20);
    }

            /* ======================================================================
           Demo SYNC – CLOSE POSITION by PERCENTAGE
           ====================================================================== */

    /**
     * Demo sincrona: chiude la posizione singola indicando la percentuale (DELETE /v2/positions/{id}?percentage=...).
     * Nota: frazionari venduti solo se la posizione è nata frazionaria (per equities dipende dall’abilitazione fractional).
     */
    private static void demoSyncClosePositionByPercentage(AlpacaPositionsRestService service,
                                                          String symbolOrAssetId,
                                                          BigDecimal percentage) {
        LOG.info("[SYNC] closePositionByPercentage(symbol={}, percentage={})", symbolOrAssetId, percentage);
        Instant t0 = Instant.now();
        try {
            AlpacaOrderResponse order = service.closePositionByPercentage(ENABLE_RETRIES, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, symbolOrAssetId, percentage);
            Instant t1 = Instant.now();
            LOG.info("[SYNC] CLOSE-POS(%) SUCCESS in {} ms | orderId={} status={} type={} side={} filledQty={}/{}",
                    ms(t0, t1), order.id(), order.status(), order.type(), order.side(),
                    order.filledQty(), order.qty());
            LOG.info("[SYNC] Order payload:\n{}", HttpUtils.safePreview(pretty(order), 300));
        } catch (ApcaRestClientException e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] CLOSE-POS(%) ERROR in {} ms -> {}", ms(t0, t1), e.getMessage(), e);
            authHintsIf401(e.getMessage());
        } catch (Exception e) {
            Instant t1 = Instant.now();
            LOG.error("[SYNC] CLOSE-POS(%) UNEXPECTED ERROR in {} ms", ms(t0, t1), e);
        }
    }

            /* ======================================================================
           Demo ASYNC – CLOSE POSITION by PERCENTAGE
           ====================================================================== */

    /**
     * Demo asincrona: chiude la posizione singola indicando la percentuale.
     */
    private static void demoAsyncClosePositionByPercentage(AlpacaPositionsRestService service,
                                                           String symbolOrAssetId,
                                                           BigDecimal percentage) {
        LOG.info("[ASYNC-DTO] closePositionByPercentageAsync(symbol={}, percentage={})", symbolOrAssetId, percentage);
        Instant t0 = Instant.now();

        CompletableFuture<AlpacaOrderResponse> fut =
                service.closePositionByPercentageAsync(ENABLE_RETRIES, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, symbolOrAssetId, percentage);

        fut.thenAccept(order -> {
                    Instant t1 = Instant.now();
                    LOG.info("[ASYNC-DTO] CLOSE-POS(%) SUCCESS in {} ms | orderId={} status={} type={} side={} filledQty={}/{}",
                            ms(t0, t1), order.id(), order.status(), order.type(), order.side(),
                            order.filledQty(), order.qty());
                    LOG.info("[ASYNC-DTO] Order payload:\n{}", HttpUtils.safePreview(pretty(order), 300));
                })
                .exceptionally(ex -> {
                    Instant t1 = Instant.now();
                    String msg = rootMessage(ex);
                    LOG.error("[ASYNC-DTO] CLOSE-POS(%) ERROR in {} ms -> {}", ms(t0, t1), msg, ex);
                    authHintsIf401(msg);
                    return null;
                });

        blockForDemo(fut, 20);
    }


            /* ======================================================================
               Helpers
               ====================================================================== */

    /**
     * Verifica robusta dell'output di Portfolio History decodificato:
     * - Conferma i tipi runtime dei campi (Instant per i timestamp; BigDecimal per i numerici).
     * - Verifica l’allineamento delle cardinalità tra timestamp/equity/profitLoss/profitLossPct (e cashflow).
     * - Logga sample utili per diagnosi (primo/ultimo punto, presenza timeframe/baseValue/baseValueAsOf).
     *
     * Nota: non solleva eccezioni (è un playground). In caso di anomalie fa logging WARN.
     *
     * @param tag etichetta per distinguere i diversi scenari di demo nei log
     * @param dto risposta tipizzata {@link AlpacaPortfolioHistoryResponse}
     */
    private static void verifyPortfolioHistoryDecoded(String tag, AlpacaPortfolioHistoryResponse dto) {
        if (dto == null) {
            LOG.warn("[CHK:{}] DTO nullo", tag);
            return;
        }
        final List<Instant> ts = dto.timestamp();
        final List<BigDecimal> eq = dto.equity();
        final List<BigDecimal> pl = dto.profitLoss();
        final List<BigDecimal> plPct = dto.profitLossPct();

        // 1) Timestamp presenti e di tipo Instant
        if (ts == null || ts.isEmpty()) {
            LOG.warn("[CHK:{}] timestamp mancante o vuoto", tag);
            return;
        }
        Instant t0 = ts.get(0);
        if (t0 == null) {
            LOG.warn("[CHK:{}] timestamp[0] è null (atteso Instant)", tag);
        } else if (!(t0 instanceof Instant)) {
            LOG.warn("[CHK:{}] timestamp[0] non è Instant: {} ", tag, t0.getClass());
        }

        // 2) Cardinalità allineate tra le principali serie
        int n = ts.size();
        boolean okEq   = (eq    == null) || eq.size()    == n;
        boolean okPl   = (pl    == null) || pl.size()    == n;
        boolean okPlPc = (plPct == null) || plPct.size() == n;

        if (!okEq || !okPl || !okPlPc) {
            LOG.warn("[CHK:{}] Cardinalità non allineate: ts={} eq={} pl={} plPct={}",
                    tag, n,
                    (eq == null ? "null" : eq.size()),
                    (pl == null ? "null" : pl.size()),
                    (plPct == null ? "null" : plPct.size()));
        }

        // 3) Tipi numerici: BigDecimal
        if (eq != null && !eq.isEmpty() && eq.get(0) != null && !(eq.get(0) instanceof BigDecimal)) {
            LOG.warn("[CHK:{}] equity[0] non è BigDecimal: {}", tag, eq.get(0).getClass());
        }
        if (pl != null && !pl.isEmpty() && pl.get(0) != null && !(pl.get(0) instanceof BigDecimal)) {
            LOG.warn("[CHK:{}] profitLoss[0] non è BigDecimal: {}", tag, pl.get(0).getClass());
        }
        if (plPct != null && !plPct.isEmpty() && plPct.get(0) != null && !(plPct.get(0) instanceof BigDecimal)) {
            LOG.warn("[CHK:{}] profitLossPct[0] non è BigDecimal: {}", tag, plPct.get(0).getClass());
        }

        // 4) Cashflow (se presente): ogni serie deve avere lunghezza pari a n
        if (dto.cashflow() != null && !dto.cashflow().isEmpty()) {
            for (Map.Entry<String, List<BigDecimal>> e : dto.cashflow().entrySet()) {
                final String key = e.getKey();
                final List<BigDecimal> series = e.getValue();
                if (series == null) {
                    LOG.warn("[CHK:{}] cashflow[{}] = null", tag, key);
                } else if (series.size() != n) {
                    LOG.warn("[CHK:{}] cashflow[{}] cardinalità diversa: {} vs ts={}", tag, key, series.size(), n);
                } else if (!series.isEmpty() && series.get(0) != null && !(series.get(0) instanceof BigDecimal)) {
                    LOG.warn("[CHK:{}] cashflow[{}][0] non è BigDecimal: {}", tag, key, series.get(0).getClass());
                }
            }
        }

        // 5) Metadati: timeframe, baseValue e baseValueAsOf (atteso Instant anche quando il server invia "YYYY-MM-DD")
        LOG.info("[CHK:{}] timeframe={} baseValue={} baseValueAsOf={}", tag, dto.timeframe(), dto.baseValue(), dto.baseValueAsOf());

        // 6) Sample finale
        if (n > 0) {
            int last = n - 1;
            LOG.info("[CHK:{}] sample: t0={} eq0={} pnl0={} pnl%0={} | tN={} eqN={} pnlN={} pnl%N={}",
                    tag,
                    ts.get(0),
                    (eq    != null && !eq.isEmpty()    ? eq.get(0)    : null),
                    (pl    != null && !pl.isEmpty()    ? pl.get(0)    : null),
                    (plPct != null && !plPct.isEmpty() ? plPct.get(0) : null),
                    ts.get(last),
                    (eq    != null && eq.size()    > last ? eq.get(last)    : null),
                    (pl    != null && pl.size()    > last ? pl.get(last)    : null),
                    (plPct != null && pl.size()    > last ? plPct.get(last) : null)
            );
        }
    }


    /**
     * Suggerimenti mirati quando rileviamo 401/Unauthorized nella catena di chiamate.
     * Non esegue side-effect: si limita a loggare hint utili.
     */
    private static void authHintsIf401(String message) {
        if (message == null) return;
        if (message.contains("401") || message.toLowerCase().contains("unauthorized")) {
            LOG.warn("""
                    [Auth Hints] HTTP 401 rilevato. Controlli rapidi:
                      1) APCA_API_KEY_ID / APCA_API_SECRET_KEY valorizzate? (vedi [Preflight])
                      2) Matching ambiente: PAPER con PAPER, PROD con PROD.
                      3) Header esatti: APCA-API-KEY-ID / APCA-API-SECRET-KEY (case-insensitive).
                      4) Chiavi attive/non revocate? IP whitelisting? Permessi Trading abilitati?
                    """);
        }
    }

    /**
     * Attende (best-effort) il completamento della future per rendere visibile l’output nei log del playground.
     */
    private static void blockForDemo(CompletableFuture<?> fut, int seconds) {
        try {
            fut.get(seconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.warn("Attesa completamento async interrotta ({} s): {}", seconds, e.toString());
        }
    }

    /**
     * Millisecondi trascorsi tra due Instant.
     */
    private static long ms(Instant t0, Instant t1) {
        return Duration.between(t0, t1).toMillis();
    }

    /**
     * Serializza in JSON "pretty" (se fallisce, fallback a toString).
     */
    private static String pretty(Object dto) {
        try {
            return JsonCodec.toJson(dto);
        } catch (Exception e) {
            return String.valueOf(dto);
        }
    }

    /**
     * Ritorna il messaggio della root-cause per log sintetici.
     */
    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur != null && cur.getCause() != null) cur = cur.getCause();
        return Optional.ofNullable(cur).map(Throwable::getMessage).orElse("");
    }

    /**
     * Produce un’anteprima sicura di una stringa (troncata a maxLen).
     */
    private static String safePreview(String s, int maxLen) {
        if (s == null) return "null";
        if (maxLen <= 0) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /**
     * Offusca il valore loggando solo le ultime 4 cifre e la lunghezza totale.
     */
    private static String mask(String value) {
        if (value == null || value.isBlank()) return "<missing>";
        int len = value.length();
        String tail = len >= 4 ? value.substring(len - 4) : value;
        return "***" + tail + " (len=" + len + ")";
    }

    /**
     * Indica se una stringa è valorizzata come YES/NO per log di preflight.
     */
    private static String present(String v) {
        return (v == null || v.isBlank()) ? "NO" : "YES";
    }

    /**
     * Trim → null se vuota.
     */
    private static String trimToNull(String v) {
        return Objects.toString(v, "").trim().isEmpty() ? null : v.trim();
    }

    /**
     * Restituisce in sicurezza l'elemento i-esimo di una lista di BigDecimal o null se non disponibile.
     */
    private static BigDecimal safeList(List<BigDecimal> list, int i) {
        if (list == null || i < 0 || i >= list.size()) return null;
        return list.get(i);
    }

    /**
     * Costruisce la querystring per /v2/assets con encoding UTF-8.
     * Regole:
     * - Parametri null/blank sono ignorati.
     * - attributes è una lista che verrà normalizzata a CSV; se vuota o null non viene aggiunta.
     */
    private static String buildAssetsQueryString(String status, String assetClass, String exchange, List<String> attributes) {
        StringBuilder sb = new StringBuilder();

        // helper locale per append con encoding
        java.util.function.BiConsumer<String, String> add = (k, v) -> {
            if (v == null || v.isBlank()) return;
            if (sb.length() > 0) sb.append('&');
            sb.append(encode(k)).append('=').append(encode(v));
        };

        if (status != null && !status.isBlank()) add.accept("status", status);
        if (assetClass != null && !assetClass.isBlank()) add.accept("asset_class", assetClass);
        if (exchange != null && !exchange.isBlank()) add.accept("exchange", exchange);
        if (attributes != null && !attributes.isEmpty()) {
            String csv = String.join(",", attributes);
            if (!csv.isBlank()) add.accept("attributes", csv);
        }

        return sb.toString();
    }

    /**
     * URL-encodes a value using UTF-8.
     */
    private static String encode(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
