package io.github.cepeppe.rest.data;

/**
 * <h1>AlpacaResponseConstants</h1>
 *
 * Contenitore unico per le costanti legate ai payload di risposta Alpaca (Trading API v2),
 * in particolare per gli endpoint di:
 * <ul>
 *   <li><b>/v2/account/activities</b> (Account Activities)</li>
 *   <li><b>/v2/orders*</b> (Orders API: create/list/get/replace/cancel)</li>
 * </ul>
 *
 * <p>Questa classe raggruppa sotto-classi <i>namespaced</i> per categorie:
 * <ul>
 *   <li>{@link AlpacaActivityTypes} — tutti i possibili valori di {@code activity_type} (Activities)</li>
 *   <li>{@link AlpacaFillEventTypes} — i valori del campo {@code type} per le FILL (Activities)</li>
 *   <li>{@link AlpacaTradeSide} — i valori del campo {@code side} (comune: trade/orders)</li>
 *   <li>{@link AlpacaOrderStatus} — gli stati d'ordine (Orders API / stream trade_updates)</li>
 *   <li>{@link AlpacaAssetClass} — le classi di asset presenti nei payload (Orders/Positions/Assets)</li>
 *   <li>{@link AlpacaOrderClass} — le classi d'ordine (simple/bracket/oco/oto/mleg)</li>
 *   <li>{@link AlpacaOrderType} — i tipi di ordine restituiti (market/limit/…)</li>
 *   <li>{@link AlpacaTimeInForce} — i valori TIF restituiti (day/gtc/opg/cls/ioc/fok)</li>
 *   <li>{@link AlpacaPositionIntent} — intent per le opzioni (buy_to_open/…)</li>
 * </ul>
 *
 * <p><b>Nota d'uso:</b> mantenere questi elenchi come <i>String constants</i> (non enum) per garantire
 * forward-compatibilità nel caso Alpaca introduca nuovi valori senza breaking change lato client.</p>
 */
public final class AlpacaResponseConstants {
    private AlpacaResponseConstants() {}

    /**
     * <h2>AlpacaActivityTypes</h2>
     * Catalogo completo dei valori possibili del campo {@code activity_type}
     * (Trade + Non-Trade) per le Account Activities.
     */
    public static final class AlpacaActivityTypes {
        private AlpacaActivityTypes() {}

        // Trade activity
        public static final String FILL   = "FILL";

        // Macro-categorie / gruppi
        public static final String TRANS  = "TRANS";   // Cash transactions (CSD/CSW)
        public static final String MISC   = "MISC";    // Varie & non categorizzate

        // ACATS / Trasferimenti
        public static final String ACATC  = "ACATC";   // ACATS cash
        public static final String ACATS  = "ACATS";   // ACATS securities

        // Crypto fees
        public static final String CFEE   = "CFEE";

        // Cash in/out
        public static final String CSD    = "CSD";     // Cash deposit
        public static final String CSW    = "CSW";     // Cash withdrawal

        // Dividendi (comprese varianti fiscali)
        public static final String DIV      = "DIV";
        public static final String DIVCGL   = "DIVCGL";
        public static final String DIVCGS   = "DIVCGS";
        public static final String DIVFEE   = "DIVFEE";
        public static final String DIVFT    = "DIVFT";
        public static final String DIVNRA   = "DIVNRA";
        public static final String DIVROC   = "DIVROC";
        public static final String DIVTW    = "DIVTW";
        public static final String DIVTXEX  = "DIVTXEX";

        // Interessi
        public static final String INT    = "INT";
        public static final String INTNRA = "INTNRA";
        public static final String INTTW  = "INTTW";

        // Journal (giri contabili)
        public static final String JNL   = "JNL";
        public static final String JNLC  = "JNLC";     // Journal cash
        public static final String JNLS  = "JNLS";     // Journal stock

        // Corporate Actions / Eventi vari
        public static final String MA    = "MA";       // Merger/Acquisition
        public static final String NC    = "NC";       // Name change
        public static final String REORG = "REORG";    // Reorganization
        public static final String SC    = "SC";       // Symbol change
        public static final String SSO   = "SSO";      // Stock spinoff
        public static final String SSP   = "SSP";      // Stock split

        // Opzioni
        public static final String OPASN = "OPASN";    // Option assignment
        public static final String OPEXP = "OPEXP";    // Option expiration
        public static final String OPXRC = "OPXRC";    // Option exercise

        // Fees generiche
        public static final String FEE   = "FEE";

        // Pass-through charges/rebates
        public static final String PTC   = "PTC";      // Pass Thru Charge
        public static final String PTR   = "PTR";      // Pass Thru Rebate
    }

    /**
     * <h2>AlpacaFillEventTypes</h2>
     * Valori del campo {@code type} quando {@code activity_type == "FILL"}.
     */
    public static final class AlpacaFillEventTypes {
        private AlpacaFillEventTypes() {}
        public static final String FILL         = "fill";
        public static final String PARTIAL_FILL = "partial_fill";
    }

    /**
     * <h2>AlpacaTradeSide</h2>
     * Valori del campo {@code side} per le FILL (trade) e ricorrenti anche nei payload ordini.
     */
    public static final class AlpacaTradeSide {
        private AlpacaTradeSide() {}
        public static final String BUY  = "buy";
        public static final String SELL = "sell";
    }

    /**
     * <h2>AlpacaOrderStatus</h2>
     * Stati d'ordine che possono apparire in {@code status} (Orders API) o in {@code order_status} (Activities).
     * Unione di stati frequenti + rari dal lifecycle ordini Alpaca / stream trade_updates.
     */
    public static final class AlpacaOrderStatus {
        private AlpacaOrderStatus() {}

        public static final String NEW                    = "new";
        public static final String PARTIALLY_FILLED       = "partially_filled";
        public static final String FILLED                 = "filled";
        public static final String DONE_FOR_DAY           = "done_for_day";
        public static final String CANCELED               = "canceled";
        public static final String EXPIRED                = "expired";
        public static final String REPLACED               = "replaced";
        public static final String PENDING_CANCEL         = "pending_cancel";
        public static final String PENDING_REPLACE        = "pending_replace";
        public static final String ACCEPTED               = "accepted";
        public static final String PENDING_NEW            = "pending_new";
        public static final String ACCEPTED_FOR_BIDDING   = "accepted_for_bidding";
        public static final String STOPPED                = "stopped";
        public static final String REJECTED               = "rejected";
        public static final String SUSPENDED              = "suspended";
        public static final String CALCULATED             = "calculated";

        // Casi rari ma visti in natura (es. bracket/leg in holding)
        public static final String HELD                   = "held";

        // Alcuni broker/venues forniscono una revisione manuale prima dell'accettazione
        public static final String PENDING_REVIEW         = "pending_review";

        // Esiti negativi di richieste di replace/cancel
        public static final String ORDER_REPLACE_REJECTED = "order_replace_rejected";
        public static final String ORDER_CANCEL_REJECTED  = "order_cancel_rejected";
    }

    /**
     * <h2>AlpacaAssetClass</h2>
     * Classi di asset che possono apparire nei payload (Orders/Positions/Assets).
     */
    public static final class AlpacaAssetClass {
        private AlpacaAssetClass() {}
        public static final String US_EQUITY   = "us_equity";
        public static final String US_OPTION   = "us_option";
        public static final String CRYPTO      = "crypto";
        public static final String CRYPTO_PERP = "crypto_perp"; // presente in SDK/nuovi cataloghi
    }

    /**
     * <h2>AlpacaOrderClass</h2>
     * Classi d'ordine (semplice e composti).
     */
    public static final class AlpacaOrderClass {
        private AlpacaOrderClass() {}
        public static final String SIMPLE  = "simple";
        public static final String BRACKET = "bracket";
        public static final String OCO     = "oco";
        public static final String OTO     = "oto";
        public static final String MLEG    = "mleg";
    }

    /**
     * <h2>AlpacaOrderType</h2>
     * Tipi di ordine restituiti nei payload.
     */
    public static final class AlpacaOrderType {
        private AlpacaOrderType() {}
        public static final String MARKET       = "market";
        public static final String LIMIT        = "limit";
        public static final String STOP         = "stop";
        public static final String STOP_LIMIT   = "stop_limit";
        public static final String TRAILING_STOP= "trailing_stop"; // equities
    }

    /**
     * <h2>AlpacaTimeInForce</h2>
     * Valori di Time-In-Force (TIF) restituiti nei payload.
     */
    public static final class AlpacaTimeInForce {
        private AlpacaTimeInForce() {}
        public static final String DAY = "day";
        public static final String GTC = "gtc";
        public static final String OPG = "opg";
        public static final String CLS = "cls";
        public static final String IOC = "ioc";
        public static final String FOK = "fok";
    }

    /**
     * <h2>AlpacaPositionIntent</h2>
     * Intent della posizione per ordini su opzioni (top-level o per leg).
     */
    public static final class AlpacaPositionIntent {
        private AlpacaPositionIntent() {}
        public static final String BUY_TO_OPEN  = "buy_to_open";
        public static final String BUY_TO_CLOSE = "buy_to_close";
        public static final String SELL_TO_OPEN = "sell_to_open";
        public static final String SELL_TO_CLOSE= "sell_to_close";
    }
}
