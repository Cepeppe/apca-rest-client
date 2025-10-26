package io.github.cepeppe;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;

public class Constants {

    // =====================================================================
    // HTTP CLIENT
    // =====================================================================

    public static final class Http {
        private Http() { /* no-op */ }

        // If a new connection does not need to be established,
        // for example if a connection can be reused from a previous request,
        // then this timeout duration has no effect.
        public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

        // Default Timeout for a single HttpRequest
        public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(5);

        // HTTP protocol version
        public static final HttpClient.Version DEFAULT_HTTP_VERSION = HttpClient.Version.HTTP_2;

        // HTTP Response status che portano a retry
        public static final Set<Integer> DEFAULT_RETRY_STATUS_SET = Set.of(429, 500, 502, 503, 504);

        // OK HTTP Response status (quando la richiesta ha avuto successo)
        public static final Set<Integer> DEFAULT_OK_STATUS_SET = Set.of(200, 201, 202, 204, 205, 207);

        // Default Base backoff exp
        public static final long DEFAULT_BASE_BACKOFF_MS = 200L;

        // Default retry max attempts
        public static final int DEFAULT_MAX_ATTEMPTS = 5;
    }

    // =====================================================================
    // DECIMALS
    // =====================================================================

    public static final class Decimals {
        private Decimals() { /* no-op */ }

        // Scale predefinite (override possibili via Env/System props)
        public static final int PRICE_SCALE      = 8;
        public static final int VOLUME_SCALE     = 8;
        public static final int INDICATOR_SCALE  = 8;
        public static final int PERCENT_SCALE    = 6;

        // Rounding/MathContext predefiniti
        public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;
        public static final MathContext MC_12 = new MathContext(12, DEFAULT_ROUNDING);

        // Comodi BigDecimal comuni
        public static final BigDecimal HUNDRED = new BigDecimal("100");
        public static final BigDecimal TWO     = new BigDecimal("2");
        public static final BigDecimal THREE   = new BigDecimal("3");

        // Chiavi di override (Env / System Properties → vedi DecimalPolicy)
        public static final String PROP_PRICE_SCALE     = "mm.decimal.price.scale";
        public static final String PROP_VOLUME_SCALE    = "mm.decimal.volume.scale";
        public static final String PROP_INDICATOR_SCALE = "mm.decimal.indicator.scale";
        public static final String PROP_PERCENT_SCALE   = "mm.decimal.percent.scale";
        public static final String PROP_ROUNDING_MODE   = "mm.decimal.rounding";    // es: HALF_UP
        public static final String PROP_MC_PRECISION    = "mm.decimal.mc.precision"; // es: 12
    }

    // =====================================================================
    // ALPACA API GENERICS
    // =====================================================================

    public static final class ALPACA {
        private ALPACA() { /* no instances */ }

        /**
         * Default single account registration
         * key in rate limit multition
         */
        public static final String DEFAULT_SINGLE_ACC_ID_RATE_LIMIT = "APCA_UNIQUE_ACC";
    }

    // =====================================================================
    // ALPACA API HEADERS
    // =====================================================================

    public static final class ALPACA_API_HEADERS {
        private ALPACA_API_HEADERS() { /* no instances */ }

        /**
         * Header HTTP per l'API Key di Alpaca.
         * <p>
         * Utilizzato nelle richieste REST per autenticazione con Alpaca.
         * Deve essere impostato su tutte le chiamate API di trading e market data.
         *
         * @see <a href="https://alpaca.markets/docs/api-documentation/api-v2/">Alpaca API Documentation</a>
         */
        public static final String ALPACA_API_KEY_HEADER_STR = "APCA-API-KEY-ID";

        /**
         * Header HTTP per l'API Secret di Alpaca.
         * <p>
         * Utilizzato nelle richieste REST per autenticazione con Alpaca.
         * Deve essere impostato su tutte le chiamate API di trading e market data.
         *
         * @see <a href="https://alpaca.markets/docs/api-documentation/api-v2/">Alpaca API Documentation</a>
         */
        public static final String ALPACA_API_SECRET_HEADER_STR = "APCA-API-SECRET-KEY";
    }


    private Constants() {}
}
