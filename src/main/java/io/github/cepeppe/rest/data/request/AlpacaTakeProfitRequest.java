package io.github.cepeppe.rest.data.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * <h1>AlpacaTakeProfitRequest</h1>
 *
 * Parte della configurazione di un ordine <b>bracket</b> o <b>oco</b>
 * nell'endpoint <b>POST /v2/orders</b>.
 *
 * <p>Contiene il prezzo limite di Take Profit.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaTakeProfitRequest(

        // Prezzo limite di take-profit (limite di uscita).
        @JsonProperty("limit_price")
        BigDecimal limitPrice
) {}
