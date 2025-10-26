package io.github.cepeppe.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

/**
 * <h1>FlexibleJsonDecoder</h1>
 *
 * <p>
 * Decoder JSON <b>riusabile</b> e <b>indipendente</b> da qualsiasi configurazione globale (es. {@code JsonCodec} del progetto).
 * È pensato per essere usato <i>ad hoc</i> su endpoint dove:
 * </p>
 *
 * <ul>
 *   <li>i campi temporali arrivano a volte come <b>epoch numerici</b> (secondi o millisecondi),
 *       altre volte come <b>stringhe ISO-8601</b> (es. {@code 2025-10-24T12:30:00Z}),
 *       e talvolta come <b>date-only</b> (es. {@code 2025-10-24});</li>
 *   <li>i numeri con decimali vanno trattati come {@link BigDecimal} per preservare precisione;</li>
 *   <li>occorre tollerare campi extra senza fallire la deserializzazione.</li>
 * </ul>
 *
 * <h2>Regole chiave (molto precise)</h2>
 *
 * <h3>1) Campi temporali modellati come {@link Instant}</h3>
 * <p>Questo decoder aggancia un deserializer <b>solo</b> per il tipo {@link Instant}. Quindi:</p>
 * <ul>
 *   <li><b>Se il DTO ha un campo numerico</b> (es. {@code long timestamp} / {@code Long ts} / {@code int}):
 *       <b>resta numerico</b>. <u>Non</u> interveniamo su quel campo, non lo convertiamo, non lo tocchiamo.</li>
 *   <li><b>Se il DTO ha un campo {@code Instant}</b> (es. {@code Instant createdAt}):
 *       si applicano le regole sotto per interpretare il valore proveniente dal JSON:
 *       <ul>
 *         <li><b>Numero intero (VALUE_NUMBER_INT)</b>:
 *             interpretazione <i>auto-detect</i>:
 *             <ul>
 *               <li>se |valore| &ge; {@value MILLIS_THRESHOLD} (≈ 10<sup>12</sup>): trattato come <b>epoch millisecondi</b> &rarr; {@code Instant.ofEpochMilli(v)}</li>
 *               <li>altrimenti: trattato come <b>epoch secondi</b> &rarr; {@code Instant.ofEpochSecond(v)}</li>
 *             </ul>
 *             Esempi: {@code 1698135599 → seconds → 2023-10-24T…Z}, {@code 1698135599000 → millis → 2023-10-24T…Z}.</li>
 *         <li><b>Stringa numerica</b> (es. {@code "1698135599"}): stessa regola di auto-detect.</li>
 *         <li><b>Stringa ISO-8601/RFC3339</b> (es. {@code "2025-10-24T12:30:00Z"}): {@code Instant.parse(...)}.</li>
 *         <li><b>Stringa “date-only”</b> (es. {@code "2025-10-24"}): interpretata come <b>mezzanotte UTC</b> di quel giorno
 *             &rarr; {@code LocalDate.parse(...).atStartOfDay(UTC).toInstant()}.
 *             <br/>Nota: <i>non</i> usiamo fusi locali: forziamo UTC per coerenza cross-ambiente.</li>
 *         <li><b>null</b> o stringa vuota: mappato a {@code null}.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h3>2) Campi “timestamp” numerici che devono rimanere numerici</h3>
 * <p>
 * Se il tuo DTO dichiara un campo come {@code long}/{@code Long}/{@code int}, rimane tale:
 * <b>non c’è alcuna conversione automatica a {@code Instant}</b>. Questo è importante per distinguere:
 * </p>
 * <ul>
 *   <li><b>timestamp (numero)</b> &ne; <b>Instant (tempo tipizzato)</b>.</li>
 * </ul>
 *
 * <h3>3) Decimali in {@link BigDecimal}</h3>
 * <ul>
 *   <li>Abilitiamo {@link DeserializationFeature#USE_BIG_DECIMAL_FOR_FLOATS}, così i numeri con parte frazionaria
 *       (float JSON) vengono letti come {@link BigDecimal} quando il target è non tipizzato ({@code Object/Number}) o è {@code BigDecimal} stesso.</li>
 *   <li>Se il campo del DTO è dichiarato {@code Double/double}, Jackson continuerà a fare il bind su {@code Double}.
 *       Per ottenere realmente {@code BigDecimal}, tipizza il campo come tale.</li>
 * </ul>
 *
 * <h3>4) Tolleranza a campi extra</h3>
 * <p>{@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES} è a {@code false}: campi sconosciuti vengono ignorati.</p>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * L’{@link ObjectMapper} usato qui è <b>immortale e thread-safe</b> per uso in lettura dopo la configurazione. Si può riusare
 * in tutto il processo senza sincronizzazione esterna.
 * </p>
 *
 * <h2>Uso</h2>
 * <pre>{@code
 * // DTO singolo
 * MyDto dto = FlexibleJsonDecoder.decode(json, MyDto.class);
 *
 * // Collezioni / tipi generici
 * List<MyDto> list = FlexibleJsonDecoder.decode(json, new TypeReference<List<MyDto>>() {});
 *
 * // Accesso all'ObjectMapper (per test o utilità locali)
 * ObjectMapper mapper = FlexibleJsonDecoder.mapper();
 * }</pre>
 *
 * <p><b>Nota:</b> questo decoder non modifica alcuna configurazione globale.
 * Usalo dove serve una semantica “robusta” per tempi/decimali (es. portfolio history).</p>
 */
public final class FlexibleJsonDecoder {

    private FlexibleJsonDecoder() {}

    /**
     * Soglia per discriminare epoch seconds vs epoch millis.
     * <ul>
     *   <li>Valori con modulo <b>&ge; 1e12</b> (13+ cifre) → <b>millisecondi</b>.</li>
     *   <li>Valori con modulo &lt; 1e12 → <b>secondi</b>.</li>
     * </ul>
     * Esempi: 1_698_135_599 (10 cifre) → seconds; 1_698_135_599_000 (13 cifre) → millis.
     */
    public static final long MILLIS_THRESHOLD = 1_000_000_000_000L;

    private static final Pattern NUMERIC_STRING = Pattern.compile("^-?\\d+$");      // "1698135599" / "-12345"
    private static final Pattern DATE_ONLY      = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$"); // "YYYY-MM-DD"

    /**
     * <h3>LenientInstantDeserializer</h3>
     * <p>Deserializer tollerante per {@link Instant}, con auto-detect di epoch seconds/millis e supporto per date-only.</p>
     */
    public static final class LenientInstantDeserializer extends JsonDeserializer<Instant> {
        @Override
        public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            final JsonToken t = p.currentToken();

            // 1) Numero intero: seconds/millis auto-detect
            if (t == JsonToken.VALUE_NUMBER_INT) {
                final long v = p.getLongValue();
                final long abs = Math.abs(v);
                return (abs >= MILLIS_THRESHOLD) ? Instant.ofEpochMilli(v) : Instant.ofEpochSecond(v);
            }

            // 2) Stringa
            if (t == JsonToken.VALUE_STRING) {
                final String s = p.getText().trim();
                if (s.isEmpty()) return null;

                // 2a) Stringa numerica → seconds/millis auto-detect
                if (NUMERIC_STRING.matcher(s).matches()) {
                    final long v = Long.parseLong(s);
                    final long abs = Math.abs(v);
                    return (abs >= MILLIS_THRESHOLD) ? Instant.ofEpochMilli(v) : Instant.ofEpochSecond(v);
                }

                // 2b) Date-only (YYYY-MM-DD) → mezzanotte UTC
                if (DATE_ONLY.matcher(s).matches()) {
                    final LocalDate d = LocalDate.parse(s);
                    return d.atStartOfDay(ZoneOffset.UTC).toInstant();
                }

                // 2c) ISO-8601/RFC3339 → Instant.parse
                return Instant.parse(s);
            }

            // 3) null
            if (t == JsonToken.VALUE_NULL) {
                return null;
            }

            // 4) Qualsiasi altro token inatteso → lascia gestire a Jackson con diagnostica
            return (Instant) ctxt.handleUnexpectedToken(Instant.class, p);
        }
    }

    /**
     * <h3>ObjectMapper locale</h3>
     * <ul>
     *   <li>Registra {@link JavaTimeModule} per i tipi {@code java.time}.</li>
     *   <li>Registra il deserializer <b>solo</b> per {@link Instant} (gli altri tipi temporali seguono le regole standard di Jackson).</li>
     *   <li>Ignora campi sconosciuti.</li>
     *   <li>Usa {@link BigDecimal} per i float JSON quando il target è non tipizzato o {@link BigDecimal}.</li>
     * </ul>
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
            .registerModule(new SimpleModule("LenientInstantModule")
                    .addDeserializer(Instant.class, new LenientInstantDeserializer()));

    /**
     * Decodifica un JSON in un tipo concreto.
     *
     * @param json body JSON
     * @param type classe di destinazione
     * @param <T>  tipo del DTO risultante
     * @return istanza popolata di {@code T}
     * @throws RuntimeException in caso di errore di parsing/bind
     */
    public static <T> T decode(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new RuntimeException("FlexibleJsonDecoder: failed to decode " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Decodifica un JSON in un tipo generico (liste, mappe, DTO parametrizzati).
     *
     * @param json   body JSON
     * @param typeRef {@link TypeReference} del tipo target (es. {@code new TypeReference<List<MyDto>>() {}})
     * @param <T>    tipo del risultato
     * @return istanza popolata di {@code T}
     * @throws RuntimeException in caso di errore di parsing/bind
     */
    public static <T> T decode(String json, TypeReference<T> typeRef) {
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (IOException e) {
            throw new RuntimeException("FlexibleJsonDecoder: failed to decode generic type: " + e.getMessage(), e);
        }
    }

    /**
     * Espone l'ObjectMapper interno (read-only) per test o usi avanzati.
     * <p><b>Attenzione:</b> non mutare la configurazione a runtime per non incidere sulla thread-safety.
     * In caso servano varianti, creare un mapper clone con ulteriori moduli.</p>
     */
    public static ObjectMapper mapper() {
        return MAPPER;
        // In caso serva un clone non condiviso:
        // return MAPPER.copy();
    }
}
