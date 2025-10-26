package io.github.cepeppe.rest.data.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * <h1>AlpacaDeleteAllOrdersResponse</h1>
 *
 * DTO (record) per rappresentare l'esito per-ordine della chiamata
 * <b>DELETE /v2/orders</b> (cancel-all). L'endpoint restituisce una lista
 * di questi risultati, uno per ciascun ordine tentato.
 *
 * <h2>Note</h2>
 * <ul>
 *   <li>{@code status} è lo <i>status HTTP</i> restituito per la cancellazione di quel singolo ordine.</li>
 *   <li>{@code body} può contenere un breve messaggio di errore/diagnostica quando disponibile; può essere {@code null}.</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaDeleteAllOrdersResponse(

        // ID dell'ordine per cui è stato tentato il cancel.
        @JsonProperty("id")
        String id,

        // HTTP status (per-ordine) restituito dal backend (es. 204, 422, 500, ...).
        @JsonProperty("status")
        Integer status,

        // Messaggio/anteprima body restituito (se presente) per diagnosticare errori specifici.
        @JsonProperty("body")
        String body
) {}
