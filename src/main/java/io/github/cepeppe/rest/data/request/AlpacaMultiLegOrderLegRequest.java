package io.github.cepeppe.rest.data.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * <h1>AlpacaMultiLegOrderLegRequest</h1>
 *
 * Descrive una singola <b>gamba</b> di un ordine <b>mleg</b> all'interno
 * della richiesta <b>POST /v2/orders</b> ({@code order_class=mleg}).
 *
 * <h2>Note</h2>
 * <ul>
 *   <li>{@code ratio_qty}: quantità proporzionale della gamba rispetto all'ordine complessivo.</li>
 *   <li>Per opzioni, valorizzare {@code position_intent} (buy_to_open, ...).</li>
 *   <li>Campi {@code type/limit_price/stop_price} sono opzionali e usati se la singola leg lo richiede.</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaMultiLegOrderLegRequest(

        // Simbolo/contratto della gamba (opzione, ecc.).
        @JsonProperty("symbol")
        String symbol,

        // Rapporto di quantità per la gamba (es. 1, 2, ...).
        @JsonProperty("ratio_qty")
        BigDecimal ratioQty,

        // Lato della gamba (buy | sell). Alternativo/affiancabile a position_intent per opzioni.
        @JsonProperty("side")
        String side,

        // Intent per opzioni: buy_to_open | buy_to_close | sell_to_open | sell_to_close
        @JsonProperty("position_intent")
        String positionIntent,

        // Tipo di ordine per la singola gamba (se richiesto).
        @JsonProperty("type")
        String type,

        // Prezzo limite per la singola gamba (se type=limit/stop_limit).
        @JsonProperty("limit_price")
        BigDecimal limitPrice,

        // Prezzo stop per la singola gamba (se type=stop/stop_limit).
        @JsonProperty("stop_price")
        BigDecimal stopPrice
) {}
