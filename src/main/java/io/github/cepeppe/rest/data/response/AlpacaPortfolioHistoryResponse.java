package io.github.cepeppe.rest.data.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * <h1>AlpacaPortfolioHistoryResponse</h1>
 *
 * DTO (record) per la risposta di <b>Get Account Portfolio History</b>
 * dell'API Trading Alpaca (<i>timeseries di equity e P/L</i> nel periodo richiesto).
 *
 * <h2>Note di modellazione</h2>
 * <ul>
 *   <li><b>Temporali</b>: tutti i campi temporali sono modellati come {@link Instant}.
 *       Il campo {@code timestamp} è una lista di istanti <i>allineati</i> agli altri array (equity, P/L, …).
 *       Alpaca restituisce i timestamp come <b>epoch seconds</b> (interi); si assume che la
 *       configurazione Jackson del progetto (JsonCodec) li mappi correttamente a {@code Instant}.</li>
 *   <li><b>Numerici</b>: tutti i valori monetari/percentuali sono {@link BigDecimal} per preservare precisione.</li>
 *   <li><b>Allineamento</b>: gli array {@code equity}, {@code profitLoss}, {@code profitLossPct}
 *       (e ciascuna serie in {@code cashflow}) hanno la <b>stessa cardinalità</b> di {@code timestamp} e sono
 *       indicizzati 1:1 rispetto ad esso.</li>
 *   <li><b>Timeframe</b>: {@code timeframe} è testuale (es. {@code "1D"}, {@code "1H"}, {@code "15Min"}, …)
 *       e descrive il passo temporale della serie.</li>
 *   <li><b>baseValue/baseValueAsOf</b>: {@code baseValue} è la base di calcolo del P/L e
 *       {@code baseValueAsOf} rappresenta l’istante (tipicamente la <i>data</i> del primo punto valido) a cui si riferisce la base.
 *       Alpaca può restituire una data-only (es. {@code "2025-09-19"}); qui è mappata a {@link Instant}
 *       (convenzionalmente a mezzanotte UTC, coerente con la policy del progetto di usare sempre {@code Instant}).</li>
 *   <li><b>cashflow</b>: mappatura <i>codice evento → serie temporale importi</i>.
 *       Le chiavi sono <b>codici tipo-attività</b> Alpaca (gli stessi di “Account Activities”), ad es.:
 *       <ul>
 *           <li>{@code CFEE}: commissioni/fee lato crypto o pair-fee; valori tipicamente <b>negativi</b> perché <i>outflow</i>;</li>
 *           <li>{@code FEE}: altre fee regolamentari o di piattaforma;</li>
 *           <li>{@code DIV}: dividendi (tipicamente <b>positivi</b>, <i>inflow</i>);</li>
 *           <li>altri codici non predefiniti qui possono comparire senza rompere la compatibilità.</li>
 *       </ul>
 *       Per ogni chiave, la lista di importi ha la stessa lunghezza di {@code timestamp} e gli importi sono espressi
 *       nella <i>valuta base del conto</i> (es. USD). Assenza di una chiave ⇒ nessun evento di quel tipo nel periodo.
 *   </li>
 * </ul>
 *
 * <h2>Semantica dei campi P/L</h2>
 * <ul>
 *   <li>{@code profitLoss}: P/L assoluto per punto temporale (stessa valuta di {@code equity}).</li>
 *   <li>{@code profitLossPct}: P/L relativo come <b>frazione</b> (non “percento intero”): es. {@code -0.0007} = -0.07%.</li>
 *   <li>Formula documentata: {@code pnl_pct = equity / base_value - 1}. (Il server applica arrotondamenti
 *       lato risposta, ma qui si conservano i valori come {@code BigDecimal}).</li>
 * </ul>
 *
 * <h2>Compatibilità</h2>
 * <ul>
 *   <li>{@link JsonIgnoreProperties#ignoreUnknown()} abilitato per tollerare campi aggiuntivi lato server.</li>
 *   <li>Le chiavi di {@code cashflow} non sono enumerate rigidamente: il server può introdurre nuovi codici.</li>
 * </ul>
 *
 * @see <a href="https://docs.alpaca.markets/reference/getaccountportfoliohistory-1">Alpaca – Get Account Portfolio History</a>
 * @see <a href="https://docs.alpaca.markets/v1.1/reference/getaccountactivities-2">Alpaca – Retrieve Account Activities (codici attività)</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaPortfolioHistoryResponse(

        /**
         * Serie di timestamp (epoch → {@link Instant}) per i punti della timeseries.
         * Tutte le altre serie sono indicizzate rispetto a questa.
         */
        @JsonProperty("timestamp")
        List<Instant> timestamp,

        /**
         * Equity del conto a ciascun timestamp (valuta base del conto, es. USD).
         * Stessa cardinalità di {@link #timestamp}.
         */
        @JsonProperty("equity")
        List<BigDecimal> equity,

        /**
         * P/L assoluto a ciascun timestamp (stessa valuta di {@link #equity}).
         */
        @JsonProperty("profit_loss")
        List<BigDecimal> profitLoss,

        /**
         * P/L relativo come frazione (non percento intero): es. 0.01 = +1%.
         */
        @JsonProperty("profit_loss_pct")
        List<BigDecimal> profitLossPct,

        /**
         * Valore base utilizzato per il calcolo del P/L (cfr. formula in documentazione Alpaca).
         */
        @JsonProperty("base_value")
        BigDecimal baseValue,

        /**
         * Istante (tipicamente una data) a cui si riferisce {@link #baseValue}.
         * Alpaca può inviare una data-only (es. "2025-09-19"): mappata a Instant (00:00:00Z per convenzione).
         */
        @JsonProperty("base_value_asof")
        Instant baseValueAsOf,

        /**
         * Timeframe della serie (es. "1D", "1H", "15Min", "5Min", "1Min"...).
         */
        @JsonProperty("timeframe")
        String timeframe,

        /**
         * Mappa "codice attività" → serie importi (una voce per ogni {@link #timestamp}).
         * Esempi di chiavi: "CFEE" (crypto fee), "FEE" (altre fee), "DIV" (dividendi).
         * Importi negativi = outflow (es. fee/prelievi), positivi = inflow (es. dividendi/versamenti).
         */
        @JsonProperty("cashflow")
        Map<String, List<BigDecimal>> cashflow

) {}
