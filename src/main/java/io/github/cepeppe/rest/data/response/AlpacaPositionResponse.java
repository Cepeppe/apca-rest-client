package io.github.cepeppe.rest.data.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * <h1>AlpacaPositionResponse</h1>
 *
 * DTO (record) per la rappresentazione di una <b>posizione</b> Alpaca (es. {@code GET /v2/positions}, {@code GET /v2/positions/{symbol}}).
 *
 * <h2>Scelte di modellazione</h2>
 * <ul>
 *   <li><b>Valori numerici/percentuali</b>: {@link BigDecimal} per preservare precisione e coerenza
 *       (i payload Alpaca spesso serializzano numeri come stringhe).</li>
 *   <li><b>Campi temporali</b>: ove presenti nei payload futuri, usare {@code java.time.Instant} (ISO-8601 UTC).
 *       Il payload di esempio qui non contiene timestamp.</li>
 *   <li><b>Campi categorici</b> (es. {@code side}, {@code exchange}, {@code assetClass}): modellati come {@link String}
 *       per tollerare nuovi valori senza rilasci breaking.</li>
 * </ul>
 *
 * <h2>Semantica dei campi principali</h2>
 * <ul>
 *   <li><b>qty</b> – Quantità in posizione (per crypto spesso frazionaria); BigDecimal.</li>
 *   <li><b>avgEntryPrice</b> – Prezzo medio di carico (per unità).</li>
 *   <li><b>marketValue</b> – Valore di mercato della posizione (qty × currentPrice) nel currency quote.</li>
 *   <li><b>costBasis</b> – Costo totale della posizione (qty × avgEntryPrice), segno coerente al lato.</li>
 *   <li><b>unrealizedPl</b> / <b>unrealizedIntradayPl</b> – P&L non realizzato totale / intraday (valore assoluto).</li>
 *   <li><b>unrealizedPlpc</b> / <b>unrealizedIntradayPlpc</b> – P&L non realizzato in <i>proporzione</i> (es. -0.0123 = -1.23%).</li>
 *   <li><b>changeToday</b> – Variazione percentuale (in forma frazionaria) rispetto a <b>lastdayPrice</b>
 *       (es. -0.0227 = -2.27%).</li>
 *   <li><b>qtyAvailable</b> – Quantità disponibile per chiudere/vendere (al netto di blocchi/hold, se applicabili).</li>
 *   <li><b>assetMarginable</b> – {@code true} se l’asset è marginabile (di norma {@code false} per crypto su Alpaca).</li>
 * </ul>
 *
 * <p><b>Nota</b>: i nomi campo Java seguono camelCase; la mappatura è esplicitata tramite {@link JsonProperty} per
 * aderire ai nomi JSON ufficiali.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaPositionResponse(

        // Identificativo univoco dell'asset (UUID nel catalogo Alpaca).
        @JsonProperty("asset_id")
        String assetId,

        // Simbolo/ticker (es. "AAPL", "BTCUSD").
        @JsonProperty("symbol")
        String symbol,

        // Venue/exchange primario (es. "NASDAQ", "NYSE", "OTC", "CRYPTO").
        @JsonProperty("exchange")
        String exchange,

        // Classe dell’asset (es. "us_equity", "crypto", "crypto_perp").
        @JsonProperty("asset_class")
        String assetClass,

        // True se l'asset è marginabile (equities); per crypto in genere false.
        @JsonProperty("asset_marginable")
        boolean assetMarginable,

        // Quantità in posizione (spesso serializzata come stringa da Alpaca).
        @JsonProperty("qty")
        BigDecimal qty,

        // Prezzo medio di carico per unità.
        @JsonProperty("avg_entry_price")
        BigDecimal avgEntryPrice,

        // Lato posizione: "long" o "short". String intenzionale (no enum) per tolleranza futuri valori.
        @JsonProperty("side")
        String side,

        // Valore di mercato della posizione (qty * currentPrice).
        @JsonProperty("market_value")
        BigDecimal marketValue,

        // Costo storico totale della posizione (qty * avgEntryPrice).
        @JsonProperty("cost_basis")
        BigDecimal costBasis,

        // P&L non realizzato totale (valore assoluto, non percentuale).
        @JsonProperty("unrealized_pl")
        BigDecimal unrealizedPl,

        // P&L non realizzato totale in forma frazionaria (es. 0.10 = +10%).
        @JsonProperty("unrealized_plpc")
        BigDecimal unrealizedPlpc,

        // P&L non realizzato intraday (valore assoluto).
        @JsonProperty("unrealized_intraday_pl")
        BigDecimal unrealizedIntradayPl,

        // P&L non realizzato intraday in forma frazionaria.
        @JsonProperty("unrealized_intraday_plpc")
        BigDecimal unrealizedIntradayPlpc,

        // Prezzo corrente di mercato.
        @JsonProperty("current_price")
        BigDecimal currentPrice,

        // Prezzo di chiusura del giorno precedente.
        @JsonProperty("lastday_price")
        BigDecimal lastdayPrice,

        // Variazione odierna in forma frazionaria (es. -0.0227 = -2.27%).
        @JsonProperty("change_today")
        BigDecimal changeToday,

        // Quantità disponibile (libera) per chiusura/vendita.
        @JsonProperty("qty_available")
        BigDecimal qtyAvailable
) {}
