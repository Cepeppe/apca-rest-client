package io.github.cepeppe.rest.data.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * <h1>AlpacaAccountNonTradeActivityResponse</h1>
 *
 * Qualsiasi activity con {@code activity_type != "FILL"}:
 * dividendi, interessi, fee, journal, corporate actions, opzioni (assignment/exercise/expiry), trasferimenti cash, ecc.
 *
 * <h3>Endpoint</h3>
 * <ul>
 *   <li><b>GET /v2/account/activities</b> — lista eterogenea</li>
 *   <li><b>GET /v2/account/activities/{activity_type}</b> — lista omogenea per singolo codice (es. CFEE, DIV, JNLC…)</li>
 * </ul>
 *
 * <h3>Proprietà</h3>
 * <ul>
 *   <li><b>activity_type</b>: uno dei valori catalogati in
 *       {@code AlpacaResponseConstants.AlpacaActivityTypes}.</li>
 *   <li><b>date</b>: {@link LocalDate} di competenza; <b>created_at</b> (opz.) come {@link Instant} UTC.</li>
 *   <li><b>net_amount</b> (opz.), <b>symbol</b> (opz.), <b>cusip</b> (opz.).</li>
 *   <li><b>qty</b>, <b>price</b> (opz., es. CFEE Non USD), <b>per_share_amount</b> (opz., es. DIV).</li>
 *   <li><b>status</b> (opz., spesso "executed").</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AlpacaAccountNonTradeActivityResponse(

        /** Identificativo univoco dell’attività (riutilizzabile come page_token). */
        @JsonProperty("id")
        String id,

        /** Codice attività (DIV, CFEE, JNLC, …). */
        @JsonProperty("activity_type")
        String activityType,

        /** Data di competenza (calendario). */
        @JsonProperty("date")
        LocalDate date,

        /** Timestamp UTC di creazione/elaborazione (se disponibile). */
        @JsonProperty("created_at")
        Instant createdAt,

        /** Importo netto movimentato (positivo/negativo). */
        @JsonProperty("net_amount")
        BigDecimal netAmount,

        /** Descrizione lato broker/backoffice (se presente). */
        @JsonProperty("description")
        String description,

        /** Simbolo del titolo/contratto (se presente). */
        @JsonProperty("symbol")
        String symbol,

        /** CUSIP/ID titolo (se presente). */
        @JsonProperty("cusip")
        String cusip,

        /** Quantità associata all’attività (se presente). */
        @JsonProperty("qty")
        BigDecimal qty,

        /** Prezzo unitario associato (se presente). */
        @JsonProperty("price")
        BigDecimal price,

        /** Importo per-azione (tipico per i DIV). */
        @JsonProperty("per_share_amount")
        BigDecimal perShareAmount,

        /** Stato dell’attività (es. "executed" per molte NTAs; opzionale). */
        @JsonProperty("status")
        String status

) implements AlpacaAccountActivityResponse { }
