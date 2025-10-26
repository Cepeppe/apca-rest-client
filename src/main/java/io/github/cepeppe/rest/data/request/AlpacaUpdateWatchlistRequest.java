package io.github.cepeppe.rest.data.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * <h1>AlpacaUpdateWatchlistRequest</h1>
 * Body per PUT /v2/watchlists/{id} e PUT /v2/watchlists:by_name.
 * Entrambi i campi sono opzionali; se "symbols" è fornito, sostituisce l'elenco.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AlpacaUpdateWatchlistRequest(

        @JsonProperty("name")
        String name,

        @JsonProperty("symbols")
        List<String> symbols
) {}
