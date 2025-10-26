package io.github.cepeppe.json;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.cepeppe.json.FlexibleJsonDecoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <h1>FlexibleJsonDecoderTest</h1>
 *
 * Test di unità per {@link FlexibleJsonDecoder}.
 *
 * <h2>Obiettivi di copertura</h2>
 * <ul>
 *   <li><b>Instant:</b> verifica deserializzazione tollerante da:
 *       <ul>
 *         <li>numeri epoch <b>seconds</b> (es. 1698135599 → {@code Instant.ofEpochSecond(...)})</li>
 *         <li>numeri epoch <b>millis</b> (es. 1698135599123 → {@code Instant.ofEpochMilli(...)})</li>
 *         <li>stringhe numeriche (auto-detect seconds/millis)</li>
 *         <li>stringhe ISO-8601/RFC3339 (es. "2025-10-24T12:30:00Z" → {@code Instant.parse(...)})</li>
 *         <li>stringhe <b>date-only</b> "YYYY-MM-DD" (→ mezzanotte UTC di quel giorno)</li>
 *         <li>null / stringa vuota → {@code null}</li>
 *       </ul>
 *   </li>
 *   <li><b>“timestamp” numerici</b> (campi DTO <i>non</i> Instant) rimangono numeri: il decoder è type-based.</li>
 *   <li><b>Decimali → BigDecimal:</b> i float JSON sono trattati come BigDecimal quando il target lo consente
 *       (o il tipo è non tipizzato/mappe generiche) grazie a {@code USE_BIG_DECIMAL_FOR_FLOATS}.</li>
 *   <li><b>Ignora campi sconosciuti</b> (no failure su proprietà extra).</li>
 *   <li><b>Generic decode</b> con {@link TypeReference} (liste/mappe).</li>
 * </ul>
 */
class FlexibleJsonDecoderTest {

    /** DTO di appoggio per i test: combina campi Instant, numerici e BigDecimal. */
    public static class SampleDto {
        public Instant  t1;      // può arrivare come number/string → Instant
        public Instant  t2;      // come sopra
        public Instant  t3;      // ISO-8601
        public Instant  t4;      // date-only
        public Instant  tNull;   // null / empty string
        public long     ts;      // timestamp numerico che DEVE restare numerico (nessuna conversione a Instant)
        public BigDecimal amount; // decimali → BigDecimal
        public Double   dbl;     // esempio campo double: resta Double se così tipizzato
    }

    @Nested
    @DisplayName("Deserializzazione Instant: numeri vs stringhe")
    class InstantDecoding {

        @Test
        @DisplayName("Numero epoch SECONDS → Instant.ofEpochSecond")
        void numberSecondsToInstant() {
            String json = """
                {
                  "t1": 1698135599,
                  "ts": 1,
                  "amount": 123.45
                }
                """;

            SampleDto dto = FlexibleJsonDecoder.decode(json, SampleDto.class);

            assertEquals(Instant.ofEpochSecond(1698135599L), dto.t1, "t1 deve essere epoch seconds");
            assertEquals(1L, dto.ts, "ts deve rimanere numerico (long), non trasformato");
            assertNotNull(dto.amount, "amount deve essere deserializzato");
            assertTrue(dto.amount.compareTo(new BigDecimal("123.45")) == 0, "amount deve essere BigDecimal ~ 123.45");
        }

        @Test
        @DisplayName("Numero epoch MILLIS → Instant.ofEpochMilli")
        void numberMillisToInstant() {
            String json = """
                {
                  "t1": 1698135599123,
                  "ts": 2
                }
                """;

            SampleDto dto = FlexibleJsonDecoder.decode(json, SampleDto.class);

            assertEquals(Instant.ofEpochMilli(1698135599123L), dto.t1, "t1 deve essere epoch millis");
            assertEquals(2L, dto.ts);
        }

        @Test
        @DisplayName("Stringa numerica (seconds/millis) → Instant (auto-detect)")
        void numericStringAutoDetect() {
            String json = """
                {
                  "t1": "1698135599",
                  "t2": "1698135599123"
                }
                """;

            SampleDto dto = FlexibleJsonDecoder.decode(json, SampleDto.class);

            assertEquals(Instant.ofEpochSecond(1698135599L), dto.t1, "stringa seconds → Instant.ofEpochSecond");
            assertEquals(Instant.ofEpochMilli(1698135599123L), dto.t2, "stringa millis → Instant.ofEpochMilli");
        }

        @Test
        @DisplayName("Stringa ISO-8601 → Instant.parse")
        void isoStringToInstant() {
            String json = """
                {
                  "t3": "2025-10-24T12:30:00Z"
                }
                """;

            SampleDto dto = FlexibleJsonDecoder.decode(json, SampleDto.class);

            assertEquals(Instant.parse("2025-10-24T12:30:00Z"), dto.t3);
        }

        @Test
        @DisplayName("Date-only 'YYYY-MM-DD' → mezzanotte UTC")
        void dateOnlyToMidnightUtcInstant() {
            String json = """
                {
                  "t4": "2025-10-24"
                }
                """;

            SampleDto dto = FlexibleJsonDecoder.decode(json, SampleDto.class);

            Instant expected = LocalDate.parse("2025-10-24").atStartOfDay(ZoneOffset.UTC).toInstant();
            assertEquals(expected, dto.t4, "date-only deve mappare a mezzanotte UTC di quel giorno");
        }

        @Test
        @DisplayName("null / stringa vuota → Instant null")
        void emptyOrNullToNullInstant() {
            String json = """
                {
                  "t1": null,
                  "tNull": ""
                }
                """;

            SampleDto dto = FlexibleJsonDecoder.decode(json, SampleDto.class);

            assertNull(dto.t1, "Instant null deve rimanere null");
            assertNull(dto.tNull, "Stringa vuota deve diventare null");
        }

        @Test
        @DisplayName("Epoch negativo (seconds) → Instant valido (pre-1970)")
        void negativeEpochSeconds() {
            String json = """
                {
                  "t1": -1
                }
                """;

            SampleDto dto = FlexibleJsonDecoder.decode(json, SampleDto.class);

            assertEquals(Instant.ofEpochSecond(-1), dto.t1, "epoch negativo deve essere supportato");
        }
    }

    @Nested
    @DisplayName("BigDecimal e numeri decimali")
    class BigDecimalDecoding {

        @Test
        @DisplayName("Float JSON → BigDecimal quando il target è BigDecimal")
        void floatToBigDecimalWhenFieldIsBigDecimal() {
            String json = """
                {
                  "amount": 1.23
                }
                """;

            SampleDto dto = FlexibleJsonDecoder.decode(json, SampleDto.class);

            assertNotNull(dto.amount);
            assertTrue(dto.amount.compareTo(new BigDecimal("1.23")) == 0,
                    "1.23 deve essere rappresentato come BigDecimal con lo stesso valore");
        }

        @Test
        @DisplayName("Float JSON in mappa generica → BigDecimal (USE_BIG_DECIMAL_FOR_FLOATS)")
        void floatToBigDecimalInGenericMap() {
            String json = """
                {
                  "a": 1.23,
                  "b": 2.0,
                  "c": 3
                }
                """;

            Map<String, Object> map = FlexibleJsonDecoder.decode(json, new TypeReference<Map<String, Object>>() {});
            assertTrue(map.get("a") instanceof BigDecimal, "a deve essere BigDecimal");
            assertTrue(map.get("b") instanceof BigDecimal, "b deve essere BigDecimal");
            assertTrue(map.get("c") instanceof Integer || map.get("c") instanceof Long,
                    "c è un intero, può essere Integer o Long a seconda del parser");

            BigDecimal a = (BigDecimal) map.get("a");
            assertTrue(a.compareTo(new BigDecimal("1.23")) == 0);
        }

        @Test
        @DisplayName("Campo tipizzato Double rimane Double")
        void doubleFieldRemainsDouble() {
            String json = """
                {
                  "dbl": 3.14
                }
                """;

            SampleDto dto = FlexibleJsonDecoder.decode(json, SampleDto.class);

            assertNotNull(dto.dbl);
            assertEquals(3.14, dto.dbl, 1e-10, "se il campo è Double, resta Double");
        }
    }

    @Nested
    @DisplayName("Tolleranza a campi sconosciuti e generic decode")
    class ToleranceAndGeneric {

        @Test
        @DisplayName("Campi sconosciuti: nessun errore, vengono ignorati")
        void unknownFieldsAreIgnored() {
            String json = """
                {
                  "t1": 1698135599,
                  "ts": 99,
                  "unknown": "ignored"
                }
                """;

            SampleDto dto = FlexibleJsonDecoder.decode(json, SampleDto.class);

            assertEquals(Instant.ofEpochSecond(1698135599L), dto.t1);
            assertEquals(99L, dto.ts);
            // Se arriviamo qui senza eccezioni, il comportamento è conforme (unknown ignorato).
        }

        @Test
        @DisplayName("Decodifica di una lista di DTO con TypeReference")
        void decodeListOfDtos() {
            String json = """
                [
                  { "t1": 1698135599, "ts": 1, "amount": 1.23 },
                  { "t1": "2025-10-24", "ts": 2, "amount": 0.01 }
                ]
                """;

            List<SampleDto> list = FlexibleJsonDecoder.decode(json, new TypeReference<List<SampleDto>>() {});
            assertEquals(2, list.size());

            // 1° elemento
            assertEquals(Instant.ofEpochSecond(1698135599L), list.get(0).t1);
            assertEquals(1L, list.get(0).ts);
            assertTrue(list.get(0).amount.compareTo(new BigDecimal("1.23")) == 0);

            // 2° elemento
            Instant expected = LocalDate.parse("2025-10-24").atStartOfDay(ZoneOffset.UTC).toInstant();
            assertEquals(expected, list.get(1).t1);
            assertEquals(2L, list.get(1).ts);
            assertTrue(list.get(1).amount.compareTo(new BigDecimal("0.01")) == 0);
        }
    }
}

