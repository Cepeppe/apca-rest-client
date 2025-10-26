package io.github.cepeppe.rest.data;

/**
 * <h1>AlpacaRequestConstants</h1>
 *
 * Contenitore delle costanti per i <b>domini di valori</b> usati nelle richieste verso la
 * Trading API v2 (Orders: create/list/get/replace/cancel).
 *
 * <p>Scopo: offrire valori canonicali come {@code String} (no enum) per massima compatibilità
 * con estensioni future lato Alpaca.</p>
 *
 * <h2>Note di validazione (riassunto vincoli più comuni)</h2>
 * <ul>
 *   <li><b>extended_hours</b>: valido solo per <i>equities</i> con {@code type=limit} e {@code time_in_force=day}.</li>
 *   <li><b>trailing_stop</b>: richiede esattamente uno tra {@code trail_price} oppure {@code trail_percent}.</li>
 *   <li><b>client_order_id</b>: max 128 caratteri.</li>
 *   <li><b>qty</b> vs <b>notional</b>: mutuamente esclusivi (uno e un solo campo).</li>
 *   <li><b>options</b> (multi-leg): usare {@code order_class=mleg}, ogni leg può richiedere {@code position_intent} e/o {@code ratio_qty}.</li>
 *   <li><b>crypto</b>: set TIF e tipi order possono differire (es. trailing_stop non supportato in molti casi; TIF più comuni: gtc/ioc).</li>
 * </ul>
 */
public final class AlpacaRequestConstants {
    private AlpacaRequestConstants() {}

    /**
     * <h2>OrderClass</h2>
     * Classi d'ordine consentite in richiesta (coerenti con quelle di risposta).
     */
    public static final class OrderClass {
        private OrderClass() {}
        public static final String SIMPLE  = "simple";
        public static final String BRACKET = "bracket";
        public static final String OCO     = "oco";
        public static final String OTO     = "oto";
        public static final String MLEG    = "mleg";
    }

    /**
     * <h2>OrderType</h2>
     * Tipi di ordine utilizzabili nelle richieste.
     */
    public static final class OrderType {
        private OrderType() {}
        public static final String MARKET       = "market";
        public static final String LIMIT        = "limit";
        public static final String STOP         = "stop";
        public static final String STOP_LIMIT   = "stop_limit";
        public static final String TRAILING_STOP= "trailing_stop"; // equities
    }

    /**
     * <h2>OrderSide</h2>
     * Lati dell'ordine in richiesta.
     */
    public static final class OrderSide {
        private OrderSide() {}
        public static final String BUY  = "buy";
        public static final String SELL = "sell";
    }

    /**
     * <h2>TimeInForce</h2>
     * Valori TIF utilizzabili in richiesta.
     */
    public static final class TimeInForce {
        private TimeInForce() {}
        public static final String DAY = "day";
        public static final String GTC = "gtc";
        public static final String OPG = "opg";
        public static final String CLS = "cls";
        public static final String IOC = "ioc";
        public static final String FOK = "fok";
    }

    /**
     * <h2>AssetClass</h2>
     * Classi di asset accettate nei payload request (quando rilevante).
     */
    public static final class AssetClass {
        private AssetClass() {}
        public static final String US_EQUITY   = "us_equity";
        public static final String US_OPTION   = "us_option";
        public static final String CRYPTO      = "crypto";
        public static final String CRYPTO_PERP = "crypto_perp";
    }

    /**
     * <h2>PositionIntent</h2>
     * Intent per ordini su opzioni (top-level e/o per leg).
     */
    public static final class PositionIntent {
        private PositionIntent() {}
        public static final String BUY_TO_OPEN  = "buy_to_open";
        public static final String BUY_TO_CLOSE = "buy_to_close";
        public static final String SELL_TO_OPEN = "sell_to_open";
        public static final String SELL_TO_CLOSE= "sell_to_close";
    }

    /**
     * <h2>ListOrdersFilter</h2>
     * Domini per i filtri di {@code GET /v2/orders}.
     */
    public static final class ListOrdersFilter {
        private ListOrdersFilter() {}

        /** Stato filtro: {@code open|closed|all}. */
        public static final class Status {
            private Status() {}
            public static final String OPEN   = "open";
            public static final String CLOSED = "closed";
            public static final String ALL    = "all";
        }

        /** Direzione ordinamento: {@code asc|desc} (default: desc). */
        public static final class Direction {
            private Direction() {}
            public static final String ASC  = "asc";
            public static final String DESC = "desc";
        }
    }

    /**
     * <h2>AdvancedInstructions</h2>
     * Istruzioni avanzate supportate su equities (esecuzione algoritmica lato broker).
     * Nota: disponibilità e vincoli possono dipendere dall'account/entitlements.
     */
    public static final class AdvancedInstructions {
        private AdvancedInstructions() {}
        public static final String VWAP = "vwap";
        public static final String TWAP = "twap";
    }
}
