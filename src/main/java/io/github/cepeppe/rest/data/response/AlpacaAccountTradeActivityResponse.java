package io.github.cepeppe.rest.data.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * <h1>AlpacaAccountTradeActivityResponse</h1>
 *
 * Rappresenta una <b>Trade Activity</b> ({@code activity_type="FILL"}) dai seguenti endpoint:
 * <ul>
 *   <li><b>GET /v2/account/activities</b> — insieme eterogeneo (FILL + NTAs)</li>
 *   <li><b>GET /v2/account/activities/FILL</b> — insieme omogeneo</li>
 * </ul>
 *
 * <h3>Proprietà principali</h3>
 * <ul>
 *   <li><b>activity_type</b>: sempre "FILL". Vedi anche {@code AlpacaResponseConstants.AlpacaActivityTypes}.</li>
 *   <li><b>type</b>: "fill" | "partial_fill". Vedi {@code AlpacaResponseConstants.AlpacaFillEventTypes}.</li>
 *   <li><b>side</b>: "buy" | "sell". Vedi {@code AlpacaResponseConstants.AlpacaTradeSide}.</li>
 *   <li><b>transaction_time</b>: {@link Instant} UTC ISO-8601.</li>
 *   <li><b>price</b>, <b>qty</b>, <b>cum_qty</b>, <b>leaves_qty</b>: {@link BigDecimal} (precisione preservata).</li>
 *   <li><b>order_status</b> (opz.): uno dei valori catalogati in {@code AlpacaResponseConstants.AlpacaOrderStatus}.</li>
 *   <li><b>swap_rate</b> (opz.): presente in alcuni mercati (es. crypto), spesso "1".</li>
 * </ul>
 *
 * <p><b>Nota simboli:</b> {@code symbol} è riportato come fornito da Alpaca (es. "BTC/USD").</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AlpacaAccountTradeActivityResponse(

        /** Identificativo univoco dell’attività (usato anche come page_token nella paginazione). */
        @JsonProperty("id")
        String id,

        /** Sempre "FILL" per le trade activity. */
        @JsonProperty("activity_type")
        String activityType,

        /** Istante (UTC) dell’esecuzione. */
        @JsonProperty("transaction_time")
        Instant transactionTime,

        /** Dettaglio evento: "fill" | "partial_fill". */
        @JsonProperty("type")
        String type,

        /** Prezzo di esecuzione (per-unità). */
        @JsonProperty("price")
        BigDecimal price,

        /** Quantità eseguita in questa activity. */
        @JsonProperty("qty")
        BigDecimal qty,

        /** Lato dell’ordine: "buy" | "sell". */
        @JsonProperty("side")
        String side,

        /** Simbolo così come fornito da Alpaca. */
        @JsonProperty("symbol")
        String symbol,

        /** Quantità residua (0 su fill finale). */
        @JsonProperty("leaves_qty")
        BigDecimal leavesQty,

        /** UUID dell’ordine che ha generato la fill. */
        @JsonProperty("order_id")
        String orderId,

        /** Quantità cumulata eseguita finora sull’ordine. */
        @JsonProperty("cum_qty")
        BigDecimal cumQty,

        /**
         * Stato dell’ordine al momento della fill (può non esserci sempre).
         * Vedi catalogo {@code AlpacaResponseConstants.AlpacaOrderStatus}.
         */
        @JsonProperty("order_status")
        String orderStatus,

        /**
         * Tasso/flag swap (alcuni mercati/asset). Spesso "1" per crypto.
         * Assente negli equity standard.
         */
        @JsonProperty("swap_rate")
        BigDecimal swapRate

) implements AlpacaAccountActivityResponse { }
