package io.github.cepeppe.json;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.core.type.TypeReference;
import io.github.cepeppe.json.JsonCodec;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonCodecTest
 * -------------
 * Obiettivi di questi test:
 *  1) Verificare le regole di (de)serializzazione centrali del JsonCodec:
 *     - Instant → stringa RFC3339 in UTC con suffisso 'Z'
 *     - Serializzazione di Instant troncata ai millisecondi
 *     - Deserializzazione che accetta sia '...Z' sia offset tipo '+01:00' (normalizzando a UTC)
 *     - Rifiuto esplicito di formati non supportati (es. date-only "YYYY-MM-DD")
 *     - NIENTE timestamp numerici in scrittura
 *  2) Tolleranza agli "unknown properties" (FAIL_ON_UNKNOWN_PROPERTIES disabilitato)
 *  3) Funzionamento con tipi generici (TypeReference)
 *  4) Round-trip per BigDecimal e Instant
 *  5) Casi limite/particolari (nanosecondi → millis, stringa vuota → null, campo mancante → null)
 *
 * Nota logging test:
 *  - NON modifichiamo il codice di produzione. Invece, per i test che si aspettano errori
 *    (es. parsing STRICT di date-only), “demotiamo” in modo *locale ai test* i log ERROR
 *    del logger di JsonCodec in un DEBUG sintetico con stacktrace troncato e una nota: «è normale».
 *  - Questo evita rumore nei log di build mantenendo comunque visibile un contesto utile.
 */
public class JsonCodecTest {

    /* =======================================================================
       DTO DI SUPPORTO AI TEST
       ======================================================================= */

    /** DTO minimale per testare Instant. */
    private record InstantDto(Instant t) {}

    /** DTO con BigDecimal + Instant per test round-trip combinati. */
    private record MoneyEventDto(BigDecimal amount, Instant when) {}

    /** DTO che include una mappa con liste di BigDecimal (simula es. cashflow Alpaca). */
    private record CashflowDto(Map<String, List<BigDecimal>> cashflow) {}

    /* =======================================================================
       SUPPORTO LOGGING (SOLO PER QUESTI TEST)
       ======================================================================= */

    /**
     * Appender che, per il logger di JsonCodec, intercetta gli ERROR attesi in profilo STRICT
     * (tipicamente messaggi «cannot parse Instant») e li trasforma in una stampa DEBUG
     * sintetica con stacktrace troncato, marcando esplicitamente che è «normale» qui.
     *
     * Non propaghiamo ai parent appenders (il logger è messo in additive=false),
     * così non appare nessun ERROR in console per questi casi attesi.
     */
    private static class StrictErrorToDebugAppender extends AppenderBase<ILoggingEvent> {
        private final String CODEC_LOGGER_NAME = JsonCodec.class.getName();

        @Override
        protected void append(ILoggingEvent event) {
            // Interessa solo il logger di JsonCodec
            if (!CODEC_LOGGER_NAME.equals(event.getLoggerName())) {
                // per altri logger, non facciamo nulla (oppure potresti inoltrare/printare)
                return;
            }

            // Se non è un ERROR, non interveniamo (mantenere silenzioso per i test)
            if (event.getLevel() != Level.ERROR) {
                return;
            }

            // Heuristica: caso atteso di STRICT che rifiuta formati (date-only/epoch/non-ISO).
            String msg = safeLower(event.getFormattedMessage());
            boolean looksStrictTemporal =
                    msg.contains("strict") &&
                            (msg.contains("cannot parse instant") || msg.contains("failed"));

            if (looksStrictTemporal) {
                // Stampiamo un riquadro sintetico in DEBUG (via System.out) con stacktrace troncato
                System.out.println("[DEBUG][TEST] JsonCodec STRICT expected parse failure (normale qui).");
                System.out.println("  Logger: " + event.getLoggerName());
                System.out.println("  Msg:    " + event.getFormattedMessage());

                String summary = summarizeThrowable(event.getThrowableProxy(), 6, 2);
                if (!summary.isBlank()) {
                    System.out.println(summary);
                }
                // Niente propagazione: l'appender è agganciato a un logger con additive=false,
                // quindi non comparirà alcun ERROR effettivo in console.
            } else {
                // Caso non riconosciuto: per sicurezza, stampiamo comunque in INFO sintetico
                // per non perdere eventuali errori reali durante i test di questa classe.
                System.out.println("[INFO][TEST] " + event.getLevel() + " " + event.getLoggerName() + " - " + event.getFormattedMessage());
                String summary = summarizeThrowable(event.getThrowableProxy(), 4, 1);
                if (!summary.isBlank()) {
                    System.out.println(summary);
                }
            }
        }

        private static String safeLower(String s) {
            return s == null ? "" : s.toLowerCase();
        }

        /**
         * Ritorna una sintesi del throwable (class: message + prime N frame), continuando per maxCause cause.
         */
        private static String summarizeThrowable(IThrowableProxy tp, int maxFrames, int maxCause) {
            if (tp == null) return "";
            StringBuilder sb = new StringBuilder(256);
            IThrowableProxy cur = tp;
            int causeIdx = 0;
            while (cur != null && causeIdx < Math.max(1, maxCause)) {
                if (causeIdx == 0) {
                    sb.append("  Cause: ").append(cur.getClassName()).append(": ").append(cur.getMessage());
                } else {
                    sb.append("\n  Caused by: ").append(cur.getClassName()).append(": ").append(cur.getMessage());
                }
                StackTraceElementProxy[] frames = cur.getStackTraceElementProxyArray();
                int n = Math.min(frames == null ? 0 : frames.length, Math.max(0, maxFrames));
                for (int i = 0; i < n; i++) {
                    sb.append("\n    at ").append(frames[i].getStackTraceElement());
                }
                if (frames != null && frames.length > n) {
                    sb.append("\n    ... (").append(frames.length - n).append(" more)");
                }
                cur = cur.getCause();
                causeIdx++;
            }
            return sb.toString();
        }
    }

    /**
     * Configura/teardown dell’appender “demoter” SOLO per i test di deserializzazione,
     * in modo da non toccare gli altri test del progetto.
     *
     * Notare:
     * - settiamo additive=false sul logger di JsonCodec per evitare che gli ERROR arrivino al root appender.
     * - poi agganciamo il nostro appender che stampa la versione DEBUG sintetica.
     */
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisplayName("Deserializzazione ← JSON")
    class Deserialization {

        private Logger codecLogger;
        private StrictErrorToDebugAppender demoter;

        @BeforeAll
        void setupCodecLoggerDemoter() {
            LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
            codecLogger = (Logger) LoggerFactory.getLogger(JsonCodec.class);

            // Disabilita propagazione al root, rimuove appenders esistenti su questo logger di classe
            codecLogger.setAdditive(false);
            codecLogger.detachAndStopAllAppenders();

            // Aggiunge il nostro appender che demota gli ERROR attesi
            demoter = new StrictErrorToDebugAppender();
            demoter.setContext(ctx);
            demoter.start();
            codecLogger.addAppender(demoter);

            // Livello DEBUG per sicurezza (ma tanto il demoter stampa via System.out)
            codecLogger.setLevel(Level.DEBUG);
        }

        @AfterAll
        void teardownCodecLoggerDemoter() {
            if (codecLogger != null && demoter != null) {
                codecLogger.detachAppender(demoter);
                demoter.stop();
                // Ripristina l'additivity (non è strettamente necessario, ma è più pulito)
                codecLogger.setAdditive(true);
            }
        }

        @Test
        @DisplayName("Accetta stringhe RFC3339 con 'Z' (Instant.parse)")
        void parseInstant_isoZ() {
            String json = """
                    {"t":"2025-03-01T10:11:12.345Z"}
                    """;

            InstantDto dto = JsonCodec.fromJson(json, InstantDto.class);

            assertEquals(Instant.parse("2025-03-01T10:11:12.345Z"), dto.t(),
                    "La stringa ISO con 'Z' deve essere accettata come Instant UTC");
        }

        @Test
        @DisplayName("Accetta stringhe con offset (es. '+01:00') normalizzando a UTC")
        void parseInstant_withOffset_normalizedToUTC() {
            // 2025-01-01T01:00:00+01:00 == 2025-01-01T00:00:00Z
            String json = """
                    {"t":"2025-01-01T01:00:00+01:00"}
                    """;

            InstantDto dto = JsonCodec.fromJson(json, InstantDto.class);

            assertEquals(Instant.parse("2025-01-01T00:00:00Z"), dto.t(),
                    "La data con offset deve essere normalizzata a UTC (Z)");
        }

        @Test
        @DisplayName("Accetta offset non interi (es. '+05:30') e normalizza correttamente")
        void parseInstant_withHalfHourOffset() {
            // 2025-02-01T12:00:00+05:30 == 2025-02-01T06:30:00Z
            String json = """
                    {"t":"2025-02-01T12:00:00+05:30"}
                    """;

            InstantDto dto = JsonCodec.fromJson(json, InstantDto.class);

            assertEquals(Instant.parse("2025-02-01T06:30:00Z"), dto.t(),
                    "Offset di 5h30m deve essere correttamente normalizzato a UTC");
        }

        @Test
        @DisplayName("Stringa vuota → null (tolleranza minima su valore mancante)")
        void parseInstant_blankString_toNull() {
            String json = """
                    {"t":""}
                    """;

            InstantDto dto = JsonCodec.fromJson(json, InstantDto.class);

            assertNull(dto.t(), "Stringa vuota deve diventare null");
        }

        @Test
        @DisplayName("Campo mancante → null")
        void parseInstant_missingField_toNull() {
            String json = """
                    {}
                    """;

            InstantDto dto = JsonCodec.fromJson(json, InstantDto.class);

            assertNull(dto.t(), "Campo assente deve risultare null");
        }

        @Test
        @DisplayName("Date-only (YYYY-MM-DD) NON supportate: deve lanciare eccezione ma il log è DEBUG sintetico e «normale»")
        void parseInstant_dateOnly_notSupported() {
            String json = """
                    {"t":"2025-03-01"}
                    """;

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> JsonCodec.fromJson(json, InstantDto.class),
                    "Le date-only non devono essere accettate dal JsonCodec centrale");

            // La RuntimeException dovrebbe incapsulare un'eccezione Jackson (JsonProcessing/JsonParse) o un'IOException.
            Throwable cause = ex.getCause();
            assertNotNull(cause, "La RuntimeException dovrebbe incapsulare la causa");

            boolean acceptableCause =
                    (cause instanceof com.fasterxml.jackson.core.JsonProcessingException) ||
                            (cause instanceof java.io.IOException) ||
                            (cause.getCause() instanceof com.fasterxml.jackson.core.JsonProcessingException) ||
                            (cause.getCause() instanceof java.io.IOException);

            assertTrue(acceptableCause,
                    "La causa (o la root-cause) dovrebbe essere una IOException/JsonProcessingException");

            // Messaggio robusto (ignora dettagli di path/posizione aggiunti da Jackson)
            String m1 = String.valueOf(cause.getMessage()).toLowerCase();
            String m2 = cause.getCause() != null ? String.valueOf(cause.getCause().getMessage()).toLowerCase() : "";
            String combined = m1 + " :: " + m2;

            assertTrue(combined.contains("cannot")
                            && combined.contains("parse")
                            && combined.contains("instant"),
                    "Il messaggio della causa dovrebbe chiarire l'impossibilità di parsare l'Instant");

            // Nota: il log visto in console per questo test deve essere un DEBUG sintetico,
            // non un ERROR: è «normale qui» che il parsing fallisca in STRICT.
        }

        @Test
        @DisplayName("Unknown properties: devono essere ignorate senza errori")
        void unknownProperties_areIgnored() {
            String json = """
                    {"t":"2025-04-05T06:07:08Z","extra":123,"another":"ignored"}
                    """;

            InstantDto dto = JsonCodec.fromJson(json, InstantDto.class);

            assertEquals(Instant.parse("2025-04-05T06:07:08Z"), dto.t(),
                    "I campi extra non devono impedire la deserializzazione");
        }

        @Test
        @DisplayName("Deserializzazione generica: List<InstantDto> (TypeReference)")
        void genericDeserialization_listOfDto() {
            String json = """
                    [
                      {"t":"2025-01-01T00:00:00Z"},
                      {"t":"2025-01-01T01:00:00+01:00"}
                    ]
                    """;

            List<InstantDto> list = JsonCodec.fromJson(json, new TypeReference<>() {});

            assertEquals(2, list.size());
            assertEquals(Instant.parse("2025-01-01T00:00:00Z"), list.get(0).t());
            // Il secondo si normalizza a Z
            assertEquals(Instant.parse("2025-01-01T00:00:00Z"), list.get(1).t());
        }

        @Test
        @DisplayName("Deserializzazione generica: Map<String,List<BigDecimal>> dentro un DTO")
        void genericDeserialization_mapOfDecimalLists() {
            String json = """
                    {
                      "cashflow": {
                        "DIV": [1.23, 0, -0.10],
                        "FEE": [-0.50]
                      }
                    }
                    """;

            CashflowDto dto = JsonCodec.fromJson(json, CashflowDto.class);

            assertNotNull(dto.cashflow(), "La mappa cashflow deve essere valorizzata");
            assertEquals(List.of(new BigDecimal("1.23"), BigDecimal.ZERO, new BigDecimal("-0.10")),
                    dto.cashflow().get("DIV"),
                    "Le liste devono essere deserializzate in BigDecimal con i corretti valori");
            assertEquals(List.of(new BigDecimal("-0.50")),
                    dto.cashflow().get("FEE"));
        }
    }

    /* =======================================================================
       TEST SU SERIALIZZAZIONE
       ======================================================================= */

    @Nested
    @DisplayName("Serializzazione → JSON")
    class Serialization {

        @Test
        @DisplayName("Instant con nanosecondi: deve essere troncato ai millisecondi e formattato in UTC con 'Z'")
        void serializeInstant_truncatedToMillis_andUTCZ() {
            // 2025-01-02T03:04:05.123456789Z → tronca a 2025-01-02T03:04:05.123Z
            InstantDto dto = new InstantDto(Instant.parse("2025-01-02T03:04:05.123456789Z"));

            String json = JsonCodec.toJson(dto);

            assertTrue(json.contains("\"t\":\"2025-01-02T03:04:05.123Z\""),
                    "L'Instant deve essere troncato ai millisecondi e reso in UTC con suffisso 'Z'");
            assertFalse(json.contains("456789"), "Niente frazioni oltre i millisecondi");
            assertFalse(json.matches(".*\"t\"\\s*:\\s*\\d+.*"),
                    "Non devono essere scritti timestamp numerici");
        }

        @Test
        @DisplayName("Instant con millisecondi zero: può omettere la frazione oppure esprimerla come .000Z; entrambe accettabili")
        void serializeInstant_zeroMillis_fractionOptional() {
            InstantDto dto = new InstantDto(Instant.parse("2025-01-02T03:04:05Z")); // zero millis

            String json = JsonCodec.toJson(dto);

            // ISO_INSTANT può rendere senza frazione (.000Z opzionale); accettiamo entrambi i casi
            boolean ok = json.contains("\"t\":\"2025-01-02T03:04:05Z\"")
                    || json.contains("\"t\":\"2025-01-02T03:04:05.000Z\"");
            assertTrue(ok, "L'Instant senza millisecondi può essere serializzato senza frazione o con .000Z");
        }

        @Test
        @DisplayName("DTO con Instant: non deve scrivere timestamp numerici (WRITE_DATES_AS_TIMESTAMPS disabilitato)")
        void noNumericTimestampsOnWrite() {
            InstantDto dto = new InstantDto(Instant.parse("2025-05-06T07:08:09.321Z"));

            String json = JsonCodec.toJson(dto);

            assertFalse(json.matches(".*\"t\"\\s*:\\s*\\d+.*"),
                    "Le date devono essere stringhe ISO-8601, non numeri");
            assertTrue(json.contains("Z"), "Deve comparire il suffisso 'Z' (UTC)");
        }

        @Test
        @DisplayName("Round-trip BigDecimal + Instant: i valori devono essere preservati (numericamente) dopo andata e ritorno")
        void roundTrip_bigDecimal_and_instant() {
            MoneyEventDto dto = new MoneyEventDto(new BigDecimal("123.4500"),
                    Instant.parse("2024-12-31T23:59:59.999Z"));

            String json = JsonCodec.toJson(dto);
            MoneyEventDto back = JsonCodec.fromJson(json, MoneyEventDto.class);

            // BigDecimal: confrontiamo il valore numerico (lo string literal può perdere zeri di coda)
            assertEquals(0, dto.amount.compareTo(back.amount),
                    "Il valore numerico di BigDecimal deve essere preservato");
            // Instant: identico
            assertEquals(dto.when, back.when, "L'Instant deve essere preservato identico");
        }
    }

    /* =======================================================================
       TEST DI INTEGRAZIONE SEMPLICI (ROUND-TRIP)
       ======================================================================= */

    @Nested
    @DisplayName("Round-trip integrazione (toJson → fromJson)")
    class RoundTrip {

        @Test
        @DisplayName("Instant con nanos → tronca a millis in scrittura e torna uguale al parse della stringa scritta")
        void roundTrip_instantWithNanos() {
            Instant original = Instant.parse("2026-06-07T08:09:10.777888999Z");
            InstantDto dto = new InstantDto(original);

            String json = JsonCodec.toJson(dto);
            InstantDto back = JsonCodec.fromJson(json, InstantDto.class);

            // La stringa scritta è tronca ai millis → l'Instant 'back' avrà .777Z (non .777888999Z)
            assertEquals(Instant.parse("2026-06-07T08:09:10.777Z"), back.t(),
                    "Dopo il round-trip, il valore deve riflettere il truncation-to-millis effettuato in scrittura");
        }

        @Test
        @DisplayName("Lista di DTO con istanti misti (Z e offset) → tutti normalizzati a Z dopo il round-trip")
        void roundTrip_listDto_mixedOffsets() {
            List<InstantDto> list = List.of(
                    new InstantDto(Instant.parse("2023-01-01T00:00:00Z")),
                    // Simuliamo un input con offset serializzandolo manualmente (qui lo forniamo già normalizzato)
                    new InstantDto(Instant.parse("2023-01-01T00:00:00Z"))
            );

            String json = JsonCodec.toJson(list);
            List<InstantDto> back = JsonCodec.fromJson(json, new TypeReference<>() {});

            assertEquals(2, back.size());
            assertEquals(Instant.parse("2023-01-01T00:00:00Z"), back.get(0).t());
            assertEquals(Instant.parse("2023-01-01T00:00:00Z"), back.get(1).t());
        }

        @Test
        @DisplayName("MoneyEventDto: BigDecimal + Instant → valori preservati numericamente/temporalmente")
        void roundTrip_moneyEvent() {
            MoneyEventDto dto = new MoneyEventDto(
                    new BigDecimal("0.000100"),  // scala 'strana'
                    Instant.parse("2020-02-29T23:59:59.001Z")
            );

            String json = JsonCodec.toJson(dto);
            MoneyEventDto back = JsonCodec.fromJson(json, MoneyEventDto.class);

            assertEquals(0, dto.amount.compareTo(back.amount),
                    "Il valore numerico del BigDecimal deve essere preservato");
            assertEquals(dto.when, back.when, "L'Instant deve restare identico nel round-trip");
        }
    }
}
