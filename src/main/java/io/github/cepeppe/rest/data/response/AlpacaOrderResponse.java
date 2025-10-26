package io.github.cepeppe.rest.data.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * <h1>AlpacaOrderResponse</h1>
 *
 * DTO (record) che modella un <b>ordine</b> Alpaca così come descritto dalla
 * "Orders API" (monitoraggio, inserimento, cancellazione).
 *
 * <h2>Linee guida di modellazione</h2>
 * <ul>
 *   <li><b>Numerici</b> (notional, qty, filledQty, prezzi, percentuali): {@link BigDecimal} per coerenza/precisione
 *       (Alpaca spesso invia stringhe numeriche).</li>
 *   <li><b>Temporali</b>: {@link Instant} per tutti i campi {@code *_at} ({@code created_at}, {@code filled_at}, ...)
 *       in formato ISO-8601 UTC (gestito dalla configurazione Jackson del progetto).</li>
 *   <li><b>Campi categorici</b> (es. {@code type}, {@code side}, {@code time_in_force}, {@code status}, {@code asset_class}):
 *       modellati come {@link String} (no enum) per permettere estensioni backward-compatible.</li>
 *   <li><b>Strutture annidate</b>: {@code legs} mappato come lista di {@link AlpacaOrderResponse} per ordini non-semplici
 *       (mleg/bracket/oco/oto) quando restituiti in forma annidata.</li>
 *   <li><b>Compatibilità futura</b>: {@link JsonIgnoreProperties#ignoreUnknown()} = true.</li>
 * </ul>
 *
 * <p><b>Deprecazioni</b>: il campo {@code order_type} è deprecato a favore di {@code type};
 * è comunque esposto per compatibilità con payload legacy.</p>
 *
 * <p><b>Nota sui campi omessi con ordini mleg</b>: per ordini multi-leg (order_class = {@code mleg}) alcuni campi
 * possono essere omessi a livello top-level (es. {@code symbol}, {@code asset_id}, {@code asset_class}, {@code type});
 * in tali casi i dettagli sono presenti all’interno delle {@code legs}.</p>
 *
 * <p><b>Sezione campi Crypto/Broker-only (opzionali)</b>: alcuni ambienti Broker/crypto popolano campi aggiuntivi come
 * {@code commission}, {@code commission_type}, {@code subtag}, {@code source}. Quando non presenti nel payload, restano {@code null}.
 * La commissione è tipicamente espressa nella <i>valuta di quotazione</i> della coppia (BigDecimal).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaOrderResponse(

        // Identificativo univoco dell’ordine (UUID lato Alpaca).
        @JsonProperty("id")
        String id,

        // Identificativo client-side (massimo 128 caratteri). Se non fornito dal client, generato dal sistema.
        @JsonProperty("client_order_id")
        String clientOrderId,

        // Timestamp creazione ordine.
        @JsonProperty("created_at")
        Instant createdAt,

        // Timestamp ultimo aggiornamento (può essere nullo).
        @JsonProperty("updated_at")
        Instant updatedAt,

        // Timestamp sottomissione/routing (può essere nullo).
        @JsonProperty("submitted_at")
        Instant submittedAt,

        // Timestamp di fill completo (se e quando avviene).
        @JsonProperty("filled_at")
        Instant filledAt,

        // Timestamp di scadenza effettiva (ordine scaduto).
        @JsonProperty("expired_at")
        Instant expiredAt,

        // Timestamp oltre il quale viene inviato un auto-cancel (policy “aged orders”/auto-cancel).
        // N.B. diverso da expired_at: indica la data/ora in cui Alpaca programma l’auto-cancel.
        @JsonProperty("expires_at")
        Instant expiresAt,

        // Timestamp di cancellazione (se cancellato).
        @JsonProperty("canceled_at")
        Instant canceledAt,

        // Timestamp di fallimento (se rifiutato o fallito).
        @JsonProperty("failed_at")
        Instant failedAt,

        // Timestamp di sostituzione (se rimpiazzato).
        @JsonProperty("replaced_at")
        Instant replacedAt,

        // ID dell’ordine che ha sostituito questo (se presente).
        @JsonProperty("replaced_by")
        String replacedBy,

        // ID dell’ordine che questo ordine sostituisce (se presente).
        @JsonProperty("replaces")
        String replaces,

        // Identificativo univoco dell’asset (per le opzioni è l’ID del contratto).
        @JsonProperty("asset_id")
        String assetId,

        // Simbolo/ticker (obbligatorio per tutte le classi di ordine tranne mleg).
        @JsonProperty("symbol")
        String symbol,

        // Classe dell’asset (es. "us_equity", "us_option", "crypto").
        @JsonProperty("asset_class")
        String assetClass,

        // Notional ordinato; alternativo a qty (uno dei due sarà nullo). Fino a 9 decimali.
        @JsonProperty("notional")
        BigDecimal notional,

        // Quantità ordinata; alternativa a notional (uno dei due sarà nullo). Fino a 9 decimali.
        @JsonProperty("qty")
        BigDecimal qty,

        // Quantità eseguita finora.
        @JsonProperty("filled_qty")
        BigDecimal filledQty,

        // Prezzo medio di esecuzione (se disponibile).
        @JsonProperty("filled_avg_price")
        BigDecimal filledAvgPrice,

        // Classe dell'ordine (simple | bracket | oco | oto | mleg).
        @JsonProperty("order_class")
        String orderClass,

        /**
         * <b>DEPRECATO</b>: tipo ordine legacy; usare {@link #type}.
         * Mantenuto per compatibilità con payload meno recenti.
         */
        @JsonProperty("order_type")
        @Deprecated
        String orderType,

        // Tipo ordine (market | limit | stop | stop_limit | trailing_stop per equities; varianti per options/crypto).
        @JsonProperty("type")
        String type,

        // Lato (buy | sell).
        @JsonProperty("side")
        String side,

        // Time In Force (es. day | gtc | opg | cls | ioc | fok).
        @JsonProperty("time_in_force")
        String timeInForce,

        // Prezzo limite (se ordine limit/stop_limit).
        @JsonProperty("limit_price")
        BigDecimal limitPrice,

        // Prezzo stop (se ordine stop/stop_limit).
        @JsonProperty("stop_price")
        BigDecimal stopPrice,

        // Stato attuale dell’ordine (new, partially_filled, filled, done_for_day, canceled, expired, replaced, ...).
        @JsonProperty("status")
        String status,

        // Se true, l'ordine è eleggibile per esecuzione fuori RTH (extended hours).
        @JsonProperty("extended_hours")
        Boolean extendedHours,

        /**
         * Ordini "figli" in caso di classi non-semplici (mleg / bracket / oco / oto) quando richiesti in forma annidata.
         * Può essere null se non applicabile o se non richiesti in forma nested.
         */
        @JsonProperty("legs")
        List<AlpacaOrderResponse> legs,

        // % di trailing dal "high-water mark" (trailing stop); frazione decimale (es. 0.02 = 2%).
        @JsonProperty("trail_percent")
        BigDecimal trailPercent,

        // Valore assoluto di trailing dal "high-water mark".
        @JsonProperty("trail_price")
        BigDecimal trailPrice,

        // High-Water Mark corrente (o low-water per sell) osservato dall'invio del trailing.
        @JsonProperty("hwm")
        BigDecimal hwm,

        // Intent della posizione (buy_to_open | buy_to_close | sell_to_open | sell_to_close), quando applicabile.
        @JsonProperty("position_intent")
        String positionIntent,

        // Quantità proporzionale della singola gamba rispetto alla quantità complessiva dell’ordine multi-leg (mleg).
        // Presente tipicamente nelle legs; a livello top-level può essere nullo.
        @JsonProperty("ratio_qty")
        BigDecimal ratioQty,

        // ===== Campi Crypto/Broker-only (opzionali) =====

        // Commissione applicata all’ordine (quote currency per crypto; pro-rata sugli execution).
        @JsonProperty("commission")
        BigDecimal commission,

        // Metodo di interpretazione della commissione: notional | qty | bps.
        @JsonProperty("commission_type")
        String commissionType,

        // Sub-tag di instradamento/omnibroker (omnibus partners).
        @JsonProperty("subtag")
        String subtag,

        // Sorgente dell’ordine (es. "correspondent", ecc.).
        @JsonProperty("source")
        String source
) {}
