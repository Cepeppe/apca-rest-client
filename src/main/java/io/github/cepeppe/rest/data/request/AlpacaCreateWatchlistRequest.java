package io.github.cepeppe.rest.data.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * <h1>AlpacaCreateWatchlistRequest</h1>
 * Body per POST /v2/watchlists.
 * Campi allineati alla reference ufficiale: name + lista di symbols.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaCreateWatchlistRequest(

        @JsonProperty("name")
        String name,

        @JsonProperty("symbols")
        List<String> symbols
) {}
