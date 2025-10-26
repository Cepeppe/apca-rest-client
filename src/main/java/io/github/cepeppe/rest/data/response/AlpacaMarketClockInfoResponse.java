package io.github.cepeppe.rest.data.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * DTO per /v2/clock.
 * Usa un record: Jackson 2.13+ supporta i record senza costruttore no-args.
 * Mappiamo i campi snake_case con @JsonProperty.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaMarketClockInfoResponse(
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("is_open")    boolean isOpen,
        @JsonProperty("next_open")  Instant nextOpen,
        @JsonProperty("next_close") Instant nextClose
) {}
