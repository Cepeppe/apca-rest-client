package io.github.cepeppe.rest.data.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * <h1>AlpacaReplaceOrderRequest</h1>
 *
 * DTO (record) per la richiesta di <b>replace</b>:
 * endpoint <b>PATCH /v2/orders/{order_id}</b>.
 *
 * <h2>Note di utilizzo</h2>
 * <ul>
 *   <li>Può aggiornare quantità, TIF e prezzi limite/stop.</li>
 *   <li>Per ordini trailing si usa il campo {@code trail} (nuovo valore coerente con l'ordine originale:
 *       se l'ordine era per percentuale, qui si invia la nuova percentuale; se era per prezzo, il nuovo prezzo).</li>
 *   <li>È possibile aggiornare il {@code client_order_id}.</li>
 * </ul>
 *
 * <p><b>Campi temporali:</b> nessun timestamp nel body; i nuovi valori saranno riflessi in response.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaReplaceOrderRequest(

        // Nuova quantità desiderata.
        @JsonProperty("qty")
        BigDecimal qty,

        // Nuovo Time In Force.
        @JsonProperty("time_in_force")
        String timeInForce,

        // Nuovo prezzo limite (se pertinente).
        @JsonProperty("limit_price")
        BigDecimal limitPrice,

        // Nuovo prezzo stop (se pertinente).
        @JsonProperty("stop_price")
        BigDecimal stopPrice,

        // Nuovo valore di trailing (prezzo o percentuale coerente con la configurazione originale).
        @JsonProperty("trail")
        BigDecimal trail,

        // Nuovo client order id (opzionale).
        @JsonProperty("client_order_id")
        String clientOrderId
) {}
