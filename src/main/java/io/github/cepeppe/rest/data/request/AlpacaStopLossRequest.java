package io.github.cepeppe.rest.data.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * <h1>AlpacaStopLossRequest</h1>
 *
 * Parte della configurazione di un ordine <b>bracket</b> o <b>oco</b>
 * nell'endpoint <b>POST /v2/orders</b>.
 *
 * <h2>Note</h2>
 * <ul>
 *   <li>{@code stop_price} è obbligatorio.</li>
 *   <li>{@code limit_price} è opzionale: se presente la gamba di uscita è una stop-limit.</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaStopLossRequest(

        // Prezzo stop (obbligatorio).
        @JsonProperty("stop_price")
        BigDecimal stopPrice,

        // Prezzo limite (opzionale): se presente ⇒ stop-limit.
        @JsonProperty("limit_price")
        BigDecimal limitPrice
) {}
