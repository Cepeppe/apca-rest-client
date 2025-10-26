package io.github.cepeppe.rest.data.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * <h1>AlpacaClosePositionResponse</h1>
 *
 * DTO (record) che modella l'elemento di risposta prodotto da Alpaca quando si
 * richiede la chiusura di una o più posizioni (es. {@code DELETE /v2/positions}).
 * In tali casi l'API può restituire, per ciascun simbolo, un oggetto "wrapper"
 * con:
 * <ul>
 *   <li>{@code symbol}: il ticker/asset interessato;</li>
 *   <li>{@code status}: "HTTP status code" dell'esito tentato (spesso numerico; qui modellato come String per tolleranza);</li>
 *   <li>{@code body}: l'oggetto Ordine risultante (se l'operazione ha generato/coinvolto un ordine),
 *       modellato come {@link AlpacaOrderResponse}.</li>
 * </ul>
 *
 * <h2>Scelte di modellazione</h2>
 * <ul>
 *   <li><b>Numerici</b>: {@link BigDecimal} per notional/qty/prezzi/percentuali, perché Alpaca spesso
 *       serializza numeri come stringhe e vogliamo precisione.</li>
 *   <li><b>Temporali</b>: {@link Instant} per mappare i campi {@code *at} (ISO-8601 UTC), come da policy del progetto.</li>
 *   <li><b>Categorici</b> (es. {@code side}, {@code type}, {@code time_in_force}): {@link String} per
 *       tollerare nuovi valori senza rilasci breaking.</li>
 *   <li><b>Ignora campi futuri</b>: {@link JsonIgnoreProperties#ignoreUnknown()} = true per forward-compatibility.</li>
 * </ul>
 *
 * <p><b>Nota</b>: Per la chiusura <i>singola</i> di una posizione (es. {@code DELETE /v2/positions/{symbol}})
 * l'API può restituire direttamente un {@link AlpacaOrderResponse} senza wrapper; questo record
 * si applica invece al formato "per-simbolo" dei risultati batch.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaClosePositionResponse(

        /**
         * Simbolo/ticker dell'asset (es. "AAPL", "BTCUSD").
         */
        @JsonProperty("symbol")
        String symbol,

        /**
         * Codice di stato HTTP dell'esito (talvolta numerico); mantenuto come String per tollerare
         * formati eterogenei e per non vincolare la deserializzazione.
         * Esempi: "200", "404".
         */
        @JsonProperty("status")
        String status,

        /**
         * Ordine risultante dall'operazione di chiusura (se disponibile).
         * Quando la chiusura non genera un ordine o fallisce prima della creazione dell'ordine,
         * questo campo può essere nullo o contenere un payload con solo informazioni di errore.
         */
        @JsonProperty("body")
        AlpacaOrderResponse body
) {}