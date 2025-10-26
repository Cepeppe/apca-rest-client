package io.github.cepeppe.rest.data.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * <h1>AlpacaCreateOrderRequest</h1>
 *
 * DTO (record) per la richiesta di creazione ordine:
 * endpoint <b>POST /v2/orders</b>.
 *
 * <h2>Note di validazione (riassunto)</h2>
 * <ul>
 *   <li><b>qty</b> e <b>notional</b> sono <i>mutuamente esclusivi</i> (indicare uno e un solo campo).</li>
 *   <li><b>extended_hours</b> (equities) è valido solo con {@code type=limit} e {@code time_in_force=day}.</li>
 *   <li><b>trailing_stop</b>: indicare esattamente uno tra {@code trail_price} o {@code trail_percent}.</li>
 *   <li><b>mleg</b>: usare {@code order_class=mleg} e popolare {@code legs} con le singole gambe.</li>
 *   <li><b>client_order_id</b> max 128 caratteri.</li>
 * </ul>
 *
 * <p><b>Campi temporali:</b> non sono presenti campi temporali nel body di create; i timestamp compaiono solo in response.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaCreateOrderRequest(

        // Ticker/simbolo (non richiesto per mleg: in quel caso è sulle legs).
        @JsonProperty("symbol")
        String symbol,

        // Quantità richiesta (mutuamente esclusiva con notional).
        @JsonProperty("qty")
        BigDecimal qty,

        // Notional in valuta (mutuamente esclusivo con qty).
        @JsonProperty("notional")
        BigDecimal notional,

        // Lato ordine: buy | sell
        @JsonProperty("side")
        String side,

        // Tipo ordine: market | limit | stop | stop_limit | trailing_stop (supporto dipende dall'asset class).
        @JsonProperty("type")
        String type,

        // Time In Force: day | gtc | opg | cls | ioc | fok
        @JsonProperty("time_in_force")
        String timeInForce,

        // Prezzo limite (per limit/stop_limit).
        @JsonProperty("limit_price")
        BigDecimal limitPrice,

        // Prezzo stop (per stop/stop_limit).
        @JsonProperty("stop_price")
        BigDecimal stopPrice,

        // Classe d'ordine: simple | bracket | oco | oto | mleg
        @JsonProperty("order_class")
        String orderClass,

        // True per consentire esecuzione fuori RTH (vincoli: equities, type=limit, tif=day).
        @JsonProperty("extended_hours")
        Boolean extendedHours,

        // Identificativo lato cliente (max 128 char).
        @JsonProperty("client_order_id")
        String clientOrderId,

        // Configurazione Take Profit (per bracket/oco).
        @JsonProperty("take_profit")
        AlpacaTakeProfitRequest takeProfit,

        // Configurazione Stop Loss (per bracket/oco).
        @JsonProperty("stop_loss")
        AlpacaStopLossRequest stopLoss,

        // Trailing assoluto (per trailing_stop). Mutuamente esclusivo con trail_percent.
        @JsonProperty("trail_price")
        BigDecimal trailPrice,

        // Trailing percentuale frazionale (0.02 = 2%). Mutuamente esclusivo con trail_price.
        @JsonProperty("trail_percent")
        BigDecimal trailPercent,

        // Intent posizione per opzioni: buy_to_open | buy_to_close | sell_to_open | sell_to_close
        @JsonProperty("position_intent")
        String positionIntent,

        // Gambe per ordini multi-leg (mleg).
        @JsonProperty("legs")
        List<AlpacaMultiLegOrderLegRequest> legs,

        // Istruzioni avanzate (equities): es. vwap, twap (se abilitato sull'account).
        @JsonProperty("advanced_instructions")
        String advancedInstructions
) {}
