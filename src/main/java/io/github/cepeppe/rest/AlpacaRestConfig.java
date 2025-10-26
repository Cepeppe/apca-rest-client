package io.github.cepeppe.rest;

import io.github.cepeppe.Env;
import io.github.cepeppe.logging.ApcaRestClientLogger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static io.github.cepeppe.Constants.ALPACA_API_HEADERS.ALPACA_API_KEY_HEADER_STR;
import static io.github.cepeppe.Constants.ALPACA_API_HEADERS.ALPACA_API_SECRET_HEADER_STR;

/**
 * Configurazione immutabile per l'invocazione delle API REST di Alpaca.
 * <p>
 * La classe incapsula:
 * <ul>
 *   <li>l'endpoint di base (paper/production, trading/market data/broker),</li>
 *   <li>l'URL base risolto,</li>
 *   <li>le credenziali di accesso (API key/secret).</li>
 * </ul>
 *
 * <h3>Origine dei valori</h3>
 * I valori possono essere costruiti:
 * <ul>
 *   <li>direttamente con il factory privato {@link #of(AlpacaRestBaseEndpoints, String, String, String)} (usato internamente),</li>
 *   <li>oppure in modo convenzionale tramite {@link #fromEnvOrDefaultAlpacaRestConfig(AlpacaRestBaseEndpoints)},
 *   che legge le variabili d'ambiente e, se non presenti, usa l'URL di default definito in {@link AlpacaRestBaseEndpoints#baseUrl()}.</li>
 * </ul>
 *
 * <h3>Immutabilità &amp; sicurezza</h3>
 * Tutti i campi sono {@code final}. Il {@code toString()} include solo campi non sensibili;
 * API key e secret non vengono mai loggati/stampati.
 *
 * <h3>Esempio d'uso</h3>
 * <pre>{@code
 * AlpacaRestConfig cfg = AlpacaRestConfig.fromEnvOrDefaultAlpacaRestConfig(
 *         AlpacaRestBaseEndpoints.API_V2_PAPER_TRADING
 * );
 * // usa cfg.getBaseUrl(), cfg.getApiKey(), cfg.getApiSecret()
 * }</pre>
 *
 * @see AlpacaRestBaseEndpoints
 */
@Getter
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class AlpacaRestConfig {

    /** Logger della classe (non stampa mai credenziali). */
    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(AlpacaRestConfig.class);

    // ------------------------------------------------------------
    // Chiavi di configurazione per gli URL degli endpoint Alpaca
    // (eventualmente sovrascrivono gli URL "di default" definiti in AlpacaRestBaseEndpoints).
    // ------------------------------------------------------------
    private static final String KEY_ALPACA_TRADING_PRODUCTION_API_V2_URL          = "ALPACA_TRADING_PRODUCTION_API_V2_URL";
    private static final String KEY_ALPACA_TRADING_PAPER_API_V2_URL               = "ALPACA_TRADING_PAPER_API_V2_URL";
    private static final String KEY_ALPACA_MARKET_DATA_PRODUCTION_API_V2_URL      = "ALPACA_MARKET_DATA_PRODUCTION_API_V2_URL";
    private static final String KEY_ALPACA_MARKET_DATA_SANDBOX_API_V2_URL         = "ALPACA_MARKET_DATA_SANDBOX_API_V2_URL";
    private static final String KEY_ALPACA_MARKET_DATA_PRODUCTION_API_V1BETA3_URL = "ALPACA_MARKET_DATA_PRODUCTION_API_V1BETA3_URL";
    private static final String KEY_ALPACA_MARKET_DATA_SANDBOX_API_V1BETA3_URL    = "ALPACA_MARKET_DATA_SANDBOX_API_V1BETA3_URL";
    private static final String KEY_ALPACA_BROKER_PRODUCTION_API_V1_URL           = "ALPACA_BROKER_PRODUCTION_API_V1_URL";
    private static final String KEY_ALPACA_BROKER_SANDBOX_API_V1_URL              = "ALPACA_BROKER_SANDBOX_API_V1_URL";

    // ------------------------------------------------------------
    // Chiavi di configurazione per le credenziali
    // ------------------------------------------------------------
    private static final String KEY_API_KEY    = "APCA_API_KEY_ID";
    private static final String KEY_API_SECRET = "APCA_API_SECRET_KEY";

    /** URL base risolto dell'endpoint selezionato (può provenire da env o dal default dell'enum). */
    @ToString.Include
    @Getter
    private final String baseUrl;

    /** Endpoint Alpaca desiderato (paper/prod, trading/market data/broker). */
    @ToString.Include
    @Getter
    private final AlpacaRestBaseEndpoints desiredEndpoint;

    /** API key Alpaca (attenzione a non loggarla). */
    private final String apiKey;

    /** API secret Alpaca (attenzione a non loggarla). */
    private final String apiSecret;

    /**
     * Genera Map con header auth params per Alpaca API
     */
     public Map<String, String> getAuthHeaderParams(){
        Map<String, String> auth = new HashMap<>();
        if (apiKey!=null && !apiKey.isBlank())
            auth.put(ALPACA_API_KEY_HEADER_STR, apiKey);
        if (apiSecret!=null && !apiSecret.isBlank())
            auth.put(ALPACA_API_SECRET_HEADER_STR, apiSecret);
        return auth;
    }

    /**
     * Factory interna con validazione minima dei parametri.
     *
     * @param desiredEndpoint endpoint Alpaca da utilizzare (non {@code null})
     * @param baseUrl         URL base già risolto (non {@code null}/blank)
     * @param key             API key (non {@code null}/blank)
     * @param secret          API secret (non {@code null}/blank)
     * @return istanza immutabile di {@link AlpacaRestConfig}
     * @throws IllegalArgumentException se uno dei parametri obbligatori è mancante o vuoto
     */
    private static AlpacaRestConfig of(AlpacaRestBaseEndpoints desiredEndpoint, String baseUrl, String key, String secret) {
        // Piccola validazione minima (messaggi in italiano per coerenza)
        if (Objects.isNull(desiredEndpoint)){
            throw new IllegalArgumentException("desiredEndpoint null non consentito");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl mancante o vuoto non consentito");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("apiKey mancante o vuota");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("apiSecret mancante o vuota");
        }

        return AlpacaRestConfig.builder()
                .desiredEndpoint(desiredEndpoint)
                .baseUrl(baseUrl)
                .apiKey(key)
                .apiSecret(secret)
                .build();
    }

    /**
     * Costruisce la configurazione leggendo le credenziali e l'URL dall'ambiente.
     * <p>
     * Le credenziali sono lette tramite:
     * <ul>
     *   <li>{@code APCA_API_KEY_ID}</li>
     *   <li>{@code APCA_API_SECRET_KEY}</li>
     * </ul>
     * L'URL è risolto così:
     * <ol>
     *   <li>si tenta di leggere una variabile ad hoc in base a {@code desiredEndpoint}
     *       (es. {@code ALPACA_TRADING_PAPER_API_V2_URL}),</li>
     *   <li>se assente, si usa il valore di default fornito da {@link AlpacaRestBaseEndpoints#baseUrl()}.</li>
     * </ol>
     *
     * @param desiredEndpoint endpoint Alpaca desiderato
     * @return configurazione popolata con base URL e credenziali
     * @throws IllegalArgumentException se l'URL risolto è {@code null} o vuoto, oppure se mancano le credenziali richieste da {@link Env#require(String)}
     */
    public static AlpacaRestConfig fromEnvOrDefaultAlpacaRestConfig(AlpacaRestBaseEndpoints desiredEndpoint){
        // Lettura credenziali (require -> eccezione se mancanti)
        String key    = Env.require(KEY_API_KEY);
        String secret = Env.require(KEY_API_SECRET);

        // URL di default per l'endpoint scelto (definito dall'enum)
        String url = "";
        String defaultUrl = desiredEndpoint.baseUrl();

        // Se presente una override via env, la usiamo, altrimenti defaultUrl.
        switch (desiredEndpoint) {
            case API_V2_PAPER_TRADING -> {
                url = Env.getOr(KEY_ALPACA_TRADING_PAPER_API_V2_URL, defaultUrl);
            }
            case API_V2_PRODUCTION_TRADING -> {
                url = Env.getOr(KEY_ALPACA_TRADING_PRODUCTION_API_V2_URL, defaultUrl);
            }
            case API_V2_SANDBOX_MARKET_DATA -> {
                url = Env.getOr(KEY_ALPACA_MARKET_DATA_SANDBOX_API_V2_URL, defaultUrl);
            }
            case API_V2_PRODUCTION_MARKET_DATA -> {
                url = Env.getOr(KEY_ALPACA_MARKET_DATA_PRODUCTION_API_V2_URL, defaultUrl);
            }
            case API_V1BETA3_SANDBOX_MARKET_DATA -> {
                url = Env.getOr(KEY_ALPACA_MARKET_DATA_SANDBOX_API_V1BETA3_URL, defaultUrl);
            }
            case API_V1BETA3_PRODUCTION_MARKET_DATA -> {
                url = Env.getOr(KEY_ALPACA_MARKET_DATA_PRODUCTION_API_V1BETA3_URL, defaultUrl);
            }
            case API_V1_SANDBOX_BROKER -> {
                url = Env.getOr(KEY_ALPACA_BROKER_SANDBOX_API_V1_URL, defaultUrl);
            }
            case API_V1_PRODUCTION_BROKER -> {
                url = Env.getOr(KEY_ALPACA_BROKER_PRODUCTION_API_V1_URL, defaultUrl);
            }
        }

        // Nota: se l'enum copre tutti i casi, 'url' non dovrebbe mai restare null,
        // ma se Env.getOr(...) restituisce null/blank lo intercettiamo qui.
        if (url == null){
            throw new IllegalArgumentException("An invalid AlpacaRestBaseEndpoints was provided ");
        } else if (url.isBlank()){
            throw new IllegalArgumentException("An invalid AlpacaRestBaseEndpoints was provided ");
        }

        // Log non sensibile (non stampa key/secret).
        LOG.info("AlpacaRestConfig loaded, env is {}: url={}", desiredEndpoint.environment(), url);
        return AlpacaRestConfig.of(desiredEndpoint, url, key, secret);
    }
}
