package io.github.cepeppe.rest;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Enum che descrive gli endpoint REST (non WebSocket) di Alpaca usati dall’app.
 *
 * <p>Ogni costante incapsula:
 * <ul>
 *   <li>l’URL base (sempre con slash finale),</li>
 *   <li>il tipo di servizio (TRADING, MARKET_DATA, BROKER),</li>
 *   <li>l’ambiente (SANDBOX, PAPER, PRODUCTION),</li>
 *   <li>la versione dell’API (es. {@code v2}, {@code v1beta3}, {@code v1}).</li>
 * </ul>
 *
 * <p>Che cosa usare nel bot:
 * <ul>
 *   <li><b>TRADING API v2</b> — per ordini, posizioni, account, assets del tuo conto:
 *     <ul>
 *       <li>sviluppo/test: {@code https://paper-api.alpaca.markets/v2/}</li>
 *       <li>produzione:     {@code https://api.alpaca.markets/v2/}</li>
 *     </ul>
 *   </li>
 *   <li><b>MARKET DATA</b> — per dati di mercato storici/real-time:
 *     <ul>
 *       <li>preferisci v2 (nuova), v1beta3 solo per legacy;</li>
 *       <li>sandbox:   {@code https://data.sandbox.alpaca.markets/v2/} o {@code /v1beta3/}</li>
 *       <li>produzione: {@code https://data.alpaca.markets/v2/}        o {@code /v1beta3/}</li>
 *     </ul>
 *   </li>
 *   <li><b>BROKER API v1</b> — per piattaforme partner (onboarding clienti, funding, conti multi-tenant):
 *     <ul>
 *       <li>serve programma/permessi broker;</li>
 *       <li>sandbox:   {@code https://broker-api.sandbox.alpaca.markets/v1/}</li>
 *       <li>produzione: {@code https://broker-api.alpaca.markets/v1/}</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>Esempi d’uso:
 * <pre>{@code
 * // Selezione rapida trading (retail) v2
 * AlpacaRestBaseEndpoints trading = AlpacaRestBaseEndpoints.trading(false); // false -> PAPER
 * String baseUrl = trading.baseUrl(); // "https://paper-api.alpaca.markets/v2/"
 *
 * // Market data v2 in produzione
 * AlpacaRestBaseEndpoints md = AlpacaRestBaseEndpoints.marketData(true, false);
 *
 * // Ricerca generica
 * Optional<AlpacaRestBaseEndpoints> opt =
 *     AlpacaRestBaseEndpoints.find(AlpacaRestBaseEndpoints.Service.TRADING,
 *                                  AlpacaRestBaseEndpoints.Environment.PAPER,
 *                                  "v2");
 * }</pre>
 */
public enum AlpacaRestBaseEndpoints {

    // ===== TRADING (retail) — API v2 =====

    /**
     * Trading API v2 (paper, conto di prova).
     * Base: {@code https://paper-api.alpaca.markets/v2/}
     * <p>Usa per sviluppo/test del bot con denaro virtuale. Stessi path e semantica della produzione.</p>
     */
    API_V2_PAPER_TRADING(
            "https://paper-api.alpaca.markets/v2/",
            Service.TRADING,
            Environment.PAPER,
            "v2"
    ),

    /**
     * Trading API v2 (produzione, conto reale).
     * Base: {@code https://api.alpaca.markets/v2/}
     * <p>Usa quando il bot è in produzione. Gli ordini hanno effetto reale.</p>
     */
    API_V2_PRODUCTION_TRADING(
            "https://api.alpaca.markets/v2/",
            Service.TRADING,
            Environment.PRODUCTION,
            "v2"
    ),

    // ===== MARKET DATA — API v2 / v1beta3 =====

    /**
     * Market Data API v2 (sandbox).
     * Base: {@code https://data.sandbox.alpaca.markets/v2/}
     * <p>Per test d’integrazione dati. Copertura/ritardi possono differire dal live.</p>
     */
    API_V2_SANDBOX_MARKET_DATA(
            "https://data.sandbox.alpaca.markets/v2/",
            Service.MARKET_DATA,
            Environment.SANDBOX,
            "v2"
    ),

    /**
     * Market Data API v2 (produzione).
     * Base: {@code https://data.alpaca.markets/v2/}
     * <p>Per dati affidabili usati dal bot in esercizio.</p>
     */
    API_V2_PRODUCTION_MARKET_DATA(
            "https://data.alpaca.markets/v2/",
            Service.MARKET_DATA,
            Environment.PRODUCTION,
            "v2"
    ),

    /**
     * Market Data API v1beta3 (sandbox, legacy).
     * Base: {@code https://data.sandbox.alpaca.markets/v1beta3/}
     * <p>Usa solo se mantieni integrazioni esistenti; per nuovi sviluppi preferisci v2.</p>
     */
    API_V1BETA3_SANDBOX_MARKET_DATA(
            "https://data.sandbox.alpaca.markets/v1beta3/",
            Service.MARKET_DATA,
            Environment.SANDBOX,
            "v1beta3"
    ),

    /**
     * Market Data API v1beta3 (produzione, legacy).
     * Base: {@code https://data.alpaca.markets/v1beta3/}
     * <p>Compatibilità retro; pianifica migrazione a v2.</p>
     */
    API_V1BETA3_PRODUCTION_MARKET_DATA(
            "https://data.alpaca.markets/v1beta3/",
            Service.MARKET_DATA,
            Environment.PRODUCTION,
            "v1beta3"
    ),

    // ===== BROKER (embedded) — API v1 =====

    /**
     * Broker API v1 (sandbox, partner).
     * Base: {@code https://broker-api.sandbox.alpaca.markets/v1/}
     * <p>Per piattaforme che gestiscono clienti/conti propri. Non per il tuo conto personale.</p>
     */
    API_V1_SANDBOX_BROKER(
            "https://broker-api.sandbox.alpaca.markets/v1/",
            Service.BROKER,
            Environment.SANDBOX,
            "v1"
    ),

    /**
     * Broker API v1 (produzione, partner).
     * Base: {@code https://broker-api.alpaca.markets/v1/}
     * <p>Stesse note della sandbox, ma live.</p>
     */
    API_V1_PRODUCTION_BROKER(
            "https://broker-api.alpaca.markets/v1/",
            Service.BROKER,
            Environment.PRODUCTION,
            "v1"
    );

    // -------------------------
    // Campi e costruttore
    // -------------------------

    private final String baseUrl;         // termina con slash
    private final Service service;        // TRADING / MARKET_DATA / BROKER
    private final Environment environment;// SANDBOX / PAPER / PRODUCTION
    private final String apiVersion;      // "v2" / "v1beta3" / "v1"

    AlpacaRestBaseEndpoints(String baseUrl, Service service, Environment environment, String apiVersion) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.service = Objects.requireNonNull(service, "service");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.apiVersion = Objects.requireNonNull(apiVersion, "apiVersion");
    }

    // -------------------------
    // Getters
    // -------------------------

    /** URL base dell’endpoint (con slash finale). */
    public String baseUrl() {
        return baseUrl;
    }

    /** Tipo di servizio dell’endpoint. */
    public Service service() {
        return service;
    }

    /** Ambiente dell’endpoint. */
    public Environment environment() {
        return environment;
    }

    /** Versione dell’API. */
    public String apiVersion() {
        return apiVersion;
    }

    // -------------------------
    // Convenienze e selettori
    // -------------------------

    /** @return {@code true} se l’endpoint è Trading. */
    public boolean isTrading() { return service == Service.TRADING; }

    /** @return {@code true} se l’endpoint è Market Data. */
    public boolean isMarketData() { return service == Service.MARKET_DATA; }

    /** @return {@code true} se l’endpoint è Broker. */
    public boolean isBroker() { return service == Service.BROKER; }

    /** @return tag sintetico dell’ambiente (per logging). */
    public String environmentTag() { return environment.name(); }

    /**
     * Selettore rapido Trading v2 (retail).
     * @param production {@code true} per produzione, {@code false} per paper
     */
    public static AlpacaRestBaseEndpoints trading(boolean production) {
        return production ? API_V2_PRODUCTION_TRADING : API_V2_PAPER_TRADING;
    }

    /**
     * Selettore Market Data.
     * @param production {@code true} per produzione, {@code false} per sandbox
     * @param preferV1b3 {@code true} per v1beta3 (legacy), {@code false} per v2 (consigliato)
     */
    public static AlpacaRestBaseEndpoints marketData(boolean production, boolean preferV1b3) {
        if (preferV1b3) {
            return production ? API_V1BETA3_PRODUCTION_MARKET_DATA : API_V1BETA3_SANDBOX_MARKET_DATA;
        }
        return production ? API_V2_PRODUCTION_MARKET_DATA : API_V2_SANDBOX_MARKET_DATA;
    }

    /**
     * Selettore Broker API v1.
     * @param production {@code true} per produzione, {@code false} per sandbox
     */
    public static AlpacaRestBaseEndpoints broker(boolean production) {
        return production ? API_V1_PRODUCTION_BROKER : API_V1_SANDBOX_BROKER;
    }

    /**
     * Trova un endpoint per servizio/ambiente/versione.
     */
    public static Optional<AlpacaRestBaseEndpoints> find(Service service, Environment env, String apiVersion) {
        return Arrays.stream(values())
                .filter(e -> e.service == service
                        && e.environment == env
                        && e.apiVersion.equalsIgnoreCase(apiVersion))
                .findFirst();
    }

    /**
     * Come {@link #find(Service, Environment, String)} ma lancia se non trovato.
     */
    public static AlpacaRestBaseEndpoints require(Service service, Environment env, String apiVersion) {
        return find(service, env, apiVersion)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nessun endpoint per service=" + service + ", env=" + env + ", apiVersion=" + apiVersion));
    }

    @Override
    public String toString() {
        return service + "@" + environment + "(" + apiVersion + "): " + baseUrl;
    }

    // -------------------------
    // Tipi di supporto
    // -------------------------

    /** Famiglia del servizio. */
    public enum Service { TRADING, MARKET_DATA, BROKER }

    /** Ambiente di erogazione. */
    public enum Environment { SANDBOX, PAPER, PRODUCTION }
}
