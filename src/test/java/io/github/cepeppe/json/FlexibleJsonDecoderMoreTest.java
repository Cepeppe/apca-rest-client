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
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <h1>FlexibleJsonDecoderMoreTest</h1>
 *
 * Test aggiuntivi per stressare {@link FlexibleJsonDecoder} su casi limite:
 * <ul>
 *   <li><b>Soglia seconds/millis</b>: valori al limite (1e12) e segno negativo</li>
 *   <li><b>ISO con offset</b> e <b>whitespace</b> → null</li>
 *   <li><b>Token non validi</b> per campi Instant (object/array)</li>
 *   <li><b>Liste e mappe</b> di Instant (misto number/string/date-only)</li>
 *   <li><b>BigDecimal</b> anche in strutture annidate</li>
 *   <li><b>Unknown properties</b> anche annidate → ignorate</li>
 *   <li><b>Thread-safety</b>: decoding in parallelo</li>
 * </ul>
 */
class FlexibleJsonDecoderMoreTest {

    /** DTO minimale per testare campi Instant e numerici “timestamp” che restano tali. */
    static class MiniDto {
        public Instant t;   // deve attivare il deserializer
        public long ts;     // resta numerico
    }

    /** DTO annidato per verificare nested object/list/map di Instant. */
    static class RootDto {
        public Inner inner;
        public List<Instant> list;
        public Map<String, Instant> map;
        public static class Inner {
            public Instant created;
            // Campo extra non mappato nella RootDto -> ignora
        }
    }

    @Nested
    @DisplayName("Soglia seconds/millis e segno")
    class SecondsMillisThreshold {

        @Test
        @DisplayName("Valore = MILLIS_THRESHOLD - 1 → seconds")
        void thresholdMinusOneIsSeconds() {
            long v = FlexibleJsonDecoder.MILLIS_THRESHOLD - 1; // 999_999_999_999
            String json = "{ \"t\": " + v + ", \"ts\": 7 }";

            MiniDto dto = FlexibleJsonDecoder.decode(json, MiniDto.class);
            assertEquals(Instant.ofEpochSecond(v), dto.t, "Sotto soglia → epoch SECONDS");
            assertEquals(7L, dto.ts);
        }

        @Test
        @DisplayName("Valore = MILLIS_THRESHOLD → millis")
        void thresholdIsMillis() {
            long v = FlexibleJsonDecoder.MILLIS_THRESHOLD; // 1_000_000_000_000
            String json = "{ \"t\": " + v + " }";

            MiniDto dto = FlexibleJsonDecoder.decode(json, MiniDto.class);
            assertEquals(Instant.ofEpochMilli(v), dto.t, "Alla soglia → epoch MILLIS");
        }

        @Test
        @DisplayName("Millis negativo grande (|v| >= soglia) → ofEpochMilli")
        void negativeLargeMillis() {
            long v = -1_698_135_599_123L;
            String json = "{ \"t\": " + v + " }";

            MiniDto dto = FlexibleJsonDecoder.decode(json, MiniDto.class);
            assertEquals(Instant.ofEpochMilli(v), dto.t);
        }

        @Test
        @DisplayName("Seconds negativo piccolo (|v| < soglia) → ofEpochSecond")
        void negativeSmallSeconds() {
            long v = -1000L; // |v| << 1e12 → seconds
            String json = "{ \"t\": " + v + " }";

            MiniDto dto = FlexibleJsonDecoder.decode(json, MiniDto.class);
            assertEquals(Instant.ofEpochSecond(v), dto.t);
        }
    }

    @Nested
    @DisplayName("Formati stringa: ISO con offset e whitespace")
    class IsoAndWhitespace {

        @Test
        @DisplayName("ISO-8601 con offset +02:00 → Instant corretto")
        void isoWithOffset() {
            String json = "{ \"t\": \"2025-10-24T14:30:00+02:00\" }";
            MiniDto dto = FlexibleJsonDecoder.decode(json, MiniDto.class);
            assertEquals(Instant.parse("2025-10-24T12:30:00Z"), dto.t,
                    "14:30+02:00 corrisponde a 12:30Z");
        }

        @Test
        @DisplayName("Stringa solo spazi → null")
        void whitespaceToNull() {
            String json = "{ \"t\": \"   \" }";
            MiniDto dto = FlexibleJsonDecoder.decode(json, MiniDto.class);
            assertNull(dto.t);
        }

        @Test
        @DisplayName("Date-only edge (leap day) → mezzanotte UTC")
        void leapDayDateOnly() {
            String json = "{ \"t\": \"2024-02-29\" }";
            MiniDto dto = FlexibleJsonDecoder.decode(json, MiniDto.class);
            Instant expected = LocalDate.parse("2024-02-29").atStartOfDay(ZoneOffset.UTC).toInstant();
            assertEquals(expected, dto.t);
        }
    }

    @Nested
    @DisplayName("Token non validi su Instant")
    class InvalidTokensForInstant {

        @Test
        @DisplayName("Oggetto `{}` dove serve un Instant → eccezione")
        void objectInsteadOfInstant() {
            String json = "{ \"t\": {\"x\":1} }";
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> FlexibleJsonDecoder.decode(json, MiniDto.class));
            assertTrue(ex.getMessage().contains("failed to decode"),
                    "Messaggio di errore deve indicare il fallimento del decode");
        }

        @Test
        @DisplayName("Array `[]` dove serve un Instant → eccezione")
        void arrayInsteadOfInstant() {
            String json = "{ \"t\": [1,2,3] }";
            assertThrows(RuntimeException.class, () -> FlexibleJsonDecoder.decode(json, MiniDto.class));
        }
    }

    @Nested
    @DisplayName("Liste/Mappe di Instant (misto number/string/date-only)")
    class ListsAndMapsOfInstant {

        @Test
        @DisplayName("Lista di Instant (seconds, millis, date-only, ISO)")
        void listOfInstantsMixed() {
            String json = """
                [
                  1698135599,
                  1698135599123,
                  "2025-10-24",
                  "2025-10-24T12:30:00Z"
                ]
                """;
            List<Instant> list = FlexibleJsonDecoder.decode(json, new TypeReference<List<Instant>>() {});
            assertEquals(4, list.size());
            assertEquals(Instant.ofEpochSecond(1698135599L), list.get(0));
            assertEquals(Instant.ofEpochMilli(1698135599123L), list.get(1));
            assertEquals(LocalDate.parse("2025-10-24").atStartOfDay(ZoneOffset.UTC).toInstant(), list.get(2));
            assertEquals(Instant.parse("2025-10-24T12:30:00Z"), list.get(3));
        }

        @Test
        @DisplayName("Root con Inner, lista e mappa di Instant")
        void nestedRootWithInnerListMap() {
            String json = """
                {
                  "inner":  { "created": "1698135599123", "unknownInner":"ignored" },
                  "list":   [ 1698135599, "2025-10-24" ],
                  "map":    { "a":"2025-10-24T12:30:00Z", "b": 1698135599 }
                }
                """;

            RootDto root = FlexibleJsonDecoder.decode(json, RootDto.class);
            assertNotNull(root.inner);
            assertEquals(Instant.ofEpochMilli(1698135599123L), root.inner.created);

            assertEquals(2, root.list.size());
            assertEquals(Instant.ofEpochSecond(1698135599L), root.list.get(0));
            assertEquals(LocalDate.parse("2025-10-24").atStartOfDay(ZoneOffset.UTC).toInstant(), root.list.get(1));

            assertEquals(Instant.parse("2025-10-24T12:30:00Z"), root.map.get("a"));
            assertEquals(Instant.ofEpochSecond(1698135599L), root.map.get("b"));
        }
    }

    @Nested
    @DisplayName("BigDecimal in strutture annidate")
    class BigDecimalDeep {

        @Test
        @DisplayName("BigDecimal dentro mappa/liste annidate (generic decode)")
        void bigDecimalNestedInGenericStructures() {
            String json = """
                {
                  "x": [ 1.23, { "y": 2.34 } ],
                  "z": { "k": 5.0, "arr": [0.000001, 10] }
                }
                """;

            Map<String, Object> map = FlexibleJsonDecoder.decode(json, new TypeReference<Map<String,Object>>() {});
            assertTrue(map.get("x") instanceof List);
            List<?> x = (List<?>) map.get("x");

            assertTrue(x.get(0) instanceof BigDecimal, "x[0] deve essere BigDecimal");
            @SuppressWarnings("unchecked")
            Map<String, Object> x1 = (Map<String, Object>) x.get(1);
            assertTrue(x1.get("y") instanceof BigDecimal, "x[1].y deve essere BigDecimal");

            @SuppressWarnings("unchecked")
            Map<String, Object> z = (Map<String, Object>) map.get("z");
            assertTrue(z.get("k") instanceof BigDecimal, "z.k deve essere BigDecimal");

            assertTrue(z.get("arr") instanceof List);
            List<?> arr = (List<?>) z.get("arr");
            assertTrue(arr.get(0) instanceof BigDecimal, "arr[0] deve essere BigDecimal (scientific-like)");
            // arr[1] è intero → Integer/Long in base al parser
            assertTrue(arr.get(1) instanceof Integer || arr.get(1) instanceof Long);
        }

        @Test
        @DisplayName("Precisione BigDecimal con molti decimali")
        void bigDecimalPrecision() {
            String json = "{ \"val\": 0.123456789123 }";
            Map<String, Object> m = FlexibleJsonDecoder.decode(json, new TypeReference<Map<String,Object>>() {});
            BigDecimal val = (BigDecimal) m.get("val");
            assertEquals(new BigDecimal("0.123456789123"), val);
        }
    }

    @Nested
    @DisplayName("Unknown properties profondi e tolleranza")
    class UnknownTolerance {

        static class SparseDto {
            public Instant only;
        }

        @Test
        @DisplayName("Unknown profondi → ignorati (no FAIL)")
        void deepUnknownIgnored() {
            String json = """
                {
                  "only": "2025-10-24T12:30:00Z",
                  "level1": {
                    "level2": {
                      "unknown": 123,
                      "obj": { "nestedUnknown": true }
                    }
                  }
                }
                """;

            SparseDto dto = FlexibleJsonDecoder.decode(json, SparseDto.class);
            assertEquals(Instant.parse("2025-10-24T12:30:00Z"), dto.only);
        }
    }

    @Nested
    @DisplayName("Thread-safety (decode parallelo)")
    class ThreadSafety {

        @Test
        @DisplayName("Decoding concorrente: risultati consistenti")
        void concurrentDecodingIsSafe() throws Exception {
            final String json = """
                { "t": 1698135599, "ts": 42 }
                """;

            int threads = Math.min(8, Runtime.getRuntime().availableProcessors());
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                List<Callable<MiniDto>> tasks = IntStream.range(0, 50)
                        .<Callable<MiniDto>>mapToObj(i -> () -> FlexibleJsonDecoder.decode(json, MiniDto.class))
                        .toList();

                List<Future<MiniDto>> futures = pool.invokeAll(tasks);
                for (Future<MiniDto> f : futures) {
                    MiniDto dto = f.get(2, TimeUnit.SECONDS);
                    assertEquals(Instant.ofEpochSecond(1698135599L), dto.t);
                    assertEquals(42L, dto.ts);
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }
}

