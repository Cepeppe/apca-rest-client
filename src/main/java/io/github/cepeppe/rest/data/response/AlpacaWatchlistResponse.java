package io.github.cepeppe.rest.data.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * <h1>AlpacaWatchlistResponse</h1>
 * DTO per le watchlist Alpaca (GET/POST/PUT).
 *
 * Note:
 * - Alcuni endpoint NON includono il campo "assets"; quando presente, è una lista di asset Alpaca.
 * - Riutilizziamo {@link AlpacaAssetResponse} per gli elementi della lista.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaWatchlistResponse(

        @JsonProperty("id")
        String id,

        @JsonProperty("account_id")
        String accountId,

        @JsonProperty("name")
        String name,

        @JsonProperty("created_at")
        Instant createdAt,

        @JsonProperty("updated_at")
        Instant updatedAt,

        @JsonProperty("assets")
        List<AlpacaAssetResponse> assets
) {}
