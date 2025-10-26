package io.github.cepeppe.rest.data.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * <h1>AlpacaAssetResponse</h1>
 *
 * DTO (record) per la rappresentazione di un asset Alpaca (es. /v2/assets, /v2/assets/{symbol}).
 *
 * <h2>Scelte di modellazione</h2>
 * <ul>
 *   <li><b>{@code assetClass}</b>: String (nessun enum/classe interna) per tollerare nuovi valori lato Alpaca.</li>
 *   <li><b>Valori numerici/percentuali</b>: {@link BigDecimal} per preservare precisione e coerenza
 *       (anche se nel JSON ufficiale alcuni campi sono tipizzati come stringhe).</li>
 *   <li><b>Timestamp</b> (se presenti in altri payload correlati): usare {@link java.time.Instant} in ISO-8601 UTC.</li>
 *   <li><b>Lista attributi</b>: {@code List<String>} per accogliere valori nuovi/specifici di piattaforma.</li>
 * </ul>
 *
 * <h2>Campi</h2>
 * <ul>
 *   <li><b>id</b> – Identificativo univoco dell’asset nel catalogo Alpaca (UUID).</li>
 *
 *   <li><b>assetClass</b> (JSON: {@code "class"}) – Categoria dell’asset.
 *     <br/>Valori noti lato Alpaca: {@code "us_equity"}, {@code "us_option"}, {@code "crypto"}, {@code "crypto_perp"}.
 *     <br/>Nota: manteniamo String per compatibilità a futuri valori.</li>
 *
 *   <li><b>exchange</b> – Exchange/venue primario.
 *     <br/>Valori comuni (non esaustivo): {@code "AMEX"}, {@code "ARCA"}, {@code "ASCX"}, {@code "BATS"},
 *     {@code "NYSE"}, {@code "NASDAQ"}, {@code "NYSEARCA"}, {@code "OTC"}, {@code "CRYPTO"}
 *     (possono apparire ulteriori codici exchange specifici).</li>
 *
 *   <li><b>symbol</b> – Simbolo/ticker dell’asset (es. {@code "AAPL"}).
 *     <br/>Crypto: Alpaca accetta sia la “vecchia” simmetria senza slash (es. {@code "BTCUSD"}) sia le coppie con slash
 *     (es. {@code "BTC/USDT"}, da URL-codificare nel path).</li>
 *
 *   <li><b>name</b> – Nome descrittivo dell’asset.</li>
 *
 *   <li><b>status</b> – Stato nel catalogo.
 *     <br/>Valori tipici: {@code "active"}, {@code "inactive"}.</li>
 *
 *   <li><b>tradable</b> – {@code true} se l’asset è negoziabile su Alpaca.
 *     <br/>Caso particolare: {@code status="active"} ma {@code tradable=false} indica spesso titoli in “liquidations-only}
 *     (es. spostati su OTC/delisted: si possono solo chiudere posizioni esistenti).</li>
 *
 *   <li><b>marginable</b> – {@code true} se l’asset è marginabile (Reg T ecc.). Per crypto è tipicamente {@code false}
 *     (le criptovalute non sono marginabili su Alpaca).</li>
 *
 *   <li><b>maintenanceMarginRequirement</b> – <b>DEPRECATO</b> nei docs ufficiali a favore di
 *     {@code marginRequirementLong}/{@code marginRequirementShort}. Rappresenta la percentuale di margine di mantenimento.
 *     Manteniamo il campo per retro-compatibilità (BigDecimal, es. {@code 30.0} = 30%).</li>
 *
 *   <li><b>marginRequirementLong</b> – Percentuale di requisito di margine per posizioni <i>long</i> (equities).
 *     <br/>BigDecimal, interpretato come percentuale (es. {@code 30.0} = 30%).</li>
 *
 *   <li><b>marginRequirementShort</b> – Percentuale di requisito di margine per posizioni <i>short</i> (equities).
 *     <br/>BigDecimal, interpretato come percentuale.</li>
 *
 *   <li><b>shortable</b> – {@code true} se l’asset può essere venduto allo scoperto (equities soltanto; crypto non shortabili).</li>
 *
 *   <li><b>easyToBorrow</b> – {@code true} se il titolo è classificato come “easy-to-borrow” (ETB) per lo short selling.</li>
 *
 *   <li><b>fractionable</b> – {@code true} se sono supportate frazioni (fractional shares) per l’asset.</li>
 *
 *   <li><b>attributes</b> – Caratteristiche addizionali dell’asset (lista di stringhe, ordine non significativo).
 *     <br/>Esempi frequenti (non esaustivi): {@code "fractional_eh_enabled"}, {@code "has_options"},
 *     {@code "options_late_close"}, {@code "ptp_no_exception"}, {@code "ptp_with_exception"},
 *     {@code "overnight_tradable"}, {@code "overnight_halted"}.
 *     La lista può evolvere lato Alpaca.</li>
 * </ul>
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaAssetResponse(

        @JsonProperty("id")
        String id, // UUID univoco dell’asset nel catalogo Alpaca.

        @JsonProperty("class")
        String assetClass, // Classe dell’asset (es. "us_equity", "us_option", "crypto", "crypto_perp"). String INTENZIONALE (no enum).

        @JsonProperty("exchange")
        String exchange, // Exchange principale (es. "NASDAQ", "NYSE", "OTC", "CRYPTO", ...).

        @JsonProperty("symbol")
        String symbol, // Ticker/simbolo (es. "AAPL"; per crypto anche "BTCUSD" o coppie "BTC/USDT" URL-encoded).

        @JsonProperty("name")
        String name, // Nome descrittivo.

        @JsonProperty("status")
        String status, // Stato: tipicamente "active" o "inactive".

        @JsonProperty("tradable")
        boolean tradable, // true se negoziabile; se active=false tradable tende a false; active=true & tradable=false = liquidations-only.

        @JsonProperty("marginable")
        boolean marginable, // true se marginabile (equities); per crypto in genere false.

        @JsonProperty("maintenance_margin_requirement")
        BigDecimal maintenanceMarginRequirement, // DEPRECATO nei docs: usare marginRequirementLong/Short. Percentuale (es. 30.0=30).

        @JsonProperty("margin_requirement_long")
        BigDecimal marginRequirementLong, // Percentuale di margine per posizioni LONG (equities).

        @JsonProperty("margin_requirement_short")
        BigDecimal marginRequirementShort, // Percentuale di margine per posizioni SHORT (equities).

        @JsonProperty("shortable")
        boolean shortable, // true se shortabile (equities). Le crypto non sono shortabili.

        @JsonProperty("easy_to_borrow")
        boolean easyToBorrow, // true se in lista ETB (easy-to-borrow).

        @JsonProperty("fractionable")
        boolean fractionable, // true se supporta fractional shares.

        @JsonProperty("attributes")
        List<String> attributes // Attributi addizionali (lista vuota se assenti).
) {}
