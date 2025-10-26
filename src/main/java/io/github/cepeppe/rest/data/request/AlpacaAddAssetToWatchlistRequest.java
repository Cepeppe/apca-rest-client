package io.github.cepeppe.rest.data.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * <h1>AlpacaAddAssetToWatchlistRequest</h1>
 * Body per POST /v2/watchlists/{id} e POST /v2/watchlists:by_name (aggiungi simbolo singolo).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaAddAssetToWatchlistRequest(

        @JsonProperty("symbol")
        String symbol
) {}
