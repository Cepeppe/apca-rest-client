package io.github.cepeppe.json;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.cepeppe.logging.ApcaRestClientLogger;


import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * JsonCodec
 *
 * Facciata unica per (de)serializzazione JSON con supporto a PROFILI di decoding:
 *
 *  - STRICT (default):
 *      * Scrittura: Instant -> RFC3339/ISO-8601 in UTC con 'Z', precisione millisecondi.
 *      * Lettura:   accetta solo stringhe ISO (…Z) o con offset (es. -04:00) normalizzate a UTC.
 *                   NON accetta numeri epoch (sec/millis) e NON accetta date-only.
 *
 *  - FLEXIBLE_TEMPORAL_NUMERIC (payload esterni “variabili”, es. Alpaca /portfolio/history):
 *      * Instant:
 *          - NUMBER (int/float): epoch secondi o millisecondi (heuristic: |v| >= 1e12 => millis).
 *          - STRING:
 *              • ISO/Z (Instant.parse) oppure con offset (OffsetDateTime.parse → UTC)
 *              • date-only (yyyy-MM-dd) → 00:00:00Z
 *              • numero come stringa -> come NUMBER sopra
 *      * BigDecimal:
 *          - NUMBER o STRING numerica/trimmed → BigDecimal
 *      * Campi sconosciuti: ignorati.
 *
 * Serializzazione (uguale per entrambi i profili): ISO-8601 in UTC, millisecond precision.
 */
public final class JsonCodec {

    public enum Profile { STRICT, FLEXIBLE_TEMPORAL_NUMERIC }

    private static final ApcaRestClientLogger log = ApcaRestClientLogger.getLogger(JsonCodec.class);

    private static final DateTimeFormatter RFC3339_UTC = DateTimeFormatter.ISO_INSTANT;

    // == STRICT ==
    private static final ObjectMapper STRICT_MAPPER = buildStrictMapper();
    private static final ObjectReader STRICT_READER = STRICT_MAPPER.reader();
    private static final ObjectWriter STRICT_WRITER = STRICT_MAPPER.writer();

    // == FLEXIBLE (solo lettura tollerante; la scrittura resta quella STRICT) ==
    private static final ObjectMapper FLEX_MAPPER = buildFlexibleTemporalNumericMapper();
    private static final ObjectReader FLEX_READER = FLEX_MAPPER.reader();

    private JsonCodec() {}

    /* =========================================================================================
       COSTRUZIONE MAPPERS
       ========================================================================================= */

    /** Mapper “rigoroso”: niente epoch numerici, niente date-only; ignora campi sconosciuti. */
    private static ObjectMapper buildStrictMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        om.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // Serializer coerente per Instant (ISO-8601 UTC, ms)
        SimpleModule mod = new SimpleModule("instant-serializer-strict");
        mod.addSerializer(Instant.class, new JsonSerializer<Instant>() {
            @Override public void serialize(Instant value, com.fasterxml.jackson.core.JsonGenerator gen, SerializerProvider serializers) throws IOException {
                if (value == null) { gen.writeNull(); return; }
                gen.writeString(RFC3339_UTC.format(value.truncatedTo(ChronoUnit.MILLIS)));
            }
        });

        // Deserializer “strict”: solo stringhe ISO; consente anche offset type-aware
        mod.addDeserializer(Instant.class, new JsonDeserializer<Instant>() {
            @Override public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonToken token = p.currentToken();
                if (token == JsonToken.VALUE_STRING) {
                    String s = p.getValueAsString();
                    if (s == null || s.isBlank()) return null;
                    // 1) ISO con 'Z'
                    try { return Instant.parse(s); } catch (Exception ignore) {}
                    // 2) Offset (es. -04:00) → UTC
                    try { return OffsetDateTime.parse(s).withOffsetSameInstant(ZoneOffset.UTC).toInstant(); } catch (Exception ignore) {}
                    // Stringa ma non ISO valida
                    throw new JsonParseException(p, "STRICT: cannot parse Instant from string value: \"" + s + "\"");
                }
                // Token non stringa
                String preview = (token == null ? "null" : token.name());
                throw new JsonParseException(p, "STRICT: cannot parse Instant from token=" + preview);
            }
        });

        om.registerModule(mod);
        return om;
    }

    /**
     * Mapper “flessibile” per lettura:
     *  - Instant: number epoch sec/millis, ISO con o senza 'Z', offset, date-only.
     *  - BigDecimal: number o stringa numerica.
     *  - Ignora sconosciuti.
     * Serializzazione = come STRICT (manteniamo solo la parte di read flessibile).
     */
    private static ObjectMapper buildFlexibleTemporalNumericMapper() {
        ObjectMapper om = buildStrictMapper(); // eredita serializer e configurazioni base
        SimpleModule flex = new SimpleModule("flexible-temporal-numeric");

        // Instant tollerante
        flex.addDeserializer(Instant.class, new JsonDeserializer<Instant>() {
            @Override public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonToken t = p.currentToken();

                // NUMBER → epoch sec/millis (float → secondi frazionari)
                if (t == JsonToken.VALUE_NUMBER_INT || t == JsonToken.VALUE_NUMBER_FLOAT) {
                    return parseInstantFromNumber(p);
                }

                // STRING → prova numerico, poi ISO, poi offset, poi date-only
                if (t == JsonToken.VALUE_STRING) {
                    String s = p.getValueAsString();
                    if (s == null || s.isBlank()) return null;
                    String trimmed = s.trim();

                    // Numerico come stringa?
                    if (isNumeric(trimmed)) {
                        return parseInstantFromNumericString(trimmed);
                    }

                    // ISO con 'Z'
                    try { return Instant.parse(trimmed); } catch (Exception ignore) {}

                    // OffsetDateTime
                    try { return OffsetDateTime.parse(trimmed).withOffsetSameInstant(ZoneOffset.UTC).toInstant(); } catch (Exception ignore) {}

                    // Date-only (yyyy-MM-dd)
                    try { return LocalDate.parse(trimmed).atStartOfDay(ZoneOffset.UTC).toInstant(); } catch (Exception ignore) {}

                    throw new JsonParseException(p, "FLEX: cannot parse Instant from string: \"" + trimmed + "\"");
                }

                // Null o altro
                if (t == JsonToken.VALUE_NULL) return null;
                throw new JsonParseException(p, "FLEX: cannot parse Instant from token=" + t);
            }

            /** Heuristics: |value| >= 1e12 -> millis, altrimenti secondi. Float → secondi frazionari *1000. */
            private Instant parseInstantFromNumber(JsonParser p) throws IOException {
                if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                    long v = p.getLongValue();
                    long abs = Math.abs(v);
                    boolean isMillis = abs >= 1_000_000_000_000L; // ~2001-09-09
                    return isMillis ? Instant.ofEpochMilli(v) : Instant.ofEpochSecond(v);
                } else {
                    // float: interpretiamo come secondi (anche frazionari)
                    BigDecimal bd = p.getDecimalValue();
                    long ms = bd.multiply(BigDecimal.valueOf(1000L)).longValue();
                    return Instant.ofEpochMilli(ms);
                }
            }

            private Instant parseInstantFromNumericString(String s) {
                // supporta interi e decimali (es. "1698132345.123")
                if (s.contains(".") || s.contains(",")) {
                    BigDecimal bd = new BigDecimal(s.replace(',', '.'));
                    long ms = bd.multiply(BigDecimal.valueOf(1000L)).longValue();
                    return Instant.ofEpochMilli(ms);
                } else {
                    long v = new BigDecimal(s).longValue();
                    long abs = Math.abs(v);
                    boolean isMillis = abs >= 1_000_000_000_000L;
                    return isMillis ? Instant.ofEpochMilli(v) : Instant.ofEpochSecond(v);
                }
            }

            private boolean isNumeric(String s) {
                // semplice check: cifra iniziale/segno e poi cifre/decimale
                return s.matches("^[+-]?\\d+(?:[\\.,]\\d+)?$");
            }
        });

        // BigDecimal tollerante (accetta anche stringhe numeriche/trimmed)
        flex.addDeserializer(BigDecimal.class, new JsonDeserializer<BigDecimal>() {
            @Override public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonToken t = p.currentToken();
                if (t == JsonToken.VALUE_NUMBER_INT || t == JsonToken.VALUE_NUMBER_FLOAT) {
                    return p.getDecimalValue();
                }
                if (t == JsonToken.VALUE_STRING) {
                    String s = p.getValueAsString();
                    if (s == null) return null;
                    String trimmed = s.trim();
                    if (trimmed.isEmpty()) return null;
                    // sostieni virgola decimale europea
                    trimmed = trimmed.replace(',', '.');
                    try { return new BigDecimal(trimmed); }
                    catch (NumberFormatException e) {
                        throw new JsonParseException(p, "FLEX: invalid BigDecimal string: \"" + s + "\"");
                    }
                }
                if (t == JsonToken.VALUE_NULL) return null;
                throw new JsonParseException(p, "FLEX: cannot parse BigDecimal from token=" + t);
            }
        });

        om.registerModule(flex);
        return om;
    }

    /* =========================================================================================
       API PUBBLICA
       ========================================================================================= */

    /** Serializza un oggetto in JSON (String) usando la policy coerente (ISO-8601 UTC ms). */
    public static String toJson(Object value) {
        try {
            return STRICT_WRITER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("JsonCodec.toJson failed: {}", e.getMessage(), e);
            throw new RuntimeException("JSON serialization error", e);
        }
    }

    /** Deserializza con profilo STRICT (default attuale). */
    public static <T> T fromJson(String json, Class<T> type) {
        return fromJson(json, type, Profile.STRICT);
    }

    /** Deserializza con profilo scelto (STRICT o FLEXIBLE_TEMPORAL_NUMERIC). */
    public static <T> T fromJson(String json, Class<T> type, Profile profile) {
        try {
            return reader(profile).forType(type).readValue(json);
        } catch (IOException e) {
            log.error("JsonCodec.fromJson(Class,{}) failed: {}", profile, e.getMessage(), e);
            throw new RuntimeException("JSON deserialization error", e);
        }
    }

    /** Deserializza tipologie generiche (es. List<MyDto>) con profilo STRICT. */
    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        return fromJson(json, typeRef, Profile.STRICT);
    }

    /** Deserializza tipologie generiche (es. List<MyDto>) con profilo scelto. */
    public static <T> T fromJson(String json, TypeReference<T> typeRef, Profile profile) {
        try {
            return reader(profile).forType(typeRef).readValue(json);
        } catch (IOException e) {
            log.error("JsonCodec.fromJson(TypeRef,{}) failed: {}", profile, e.getMessage(), e);
            throw new RuntimeException("JSON deserialization error", e);
        }
    }

    /** Espone l’ObjectReader del profilo (riusabile, thread-safe). */
    public static ObjectReader reader(Profile profile) {
        return (profile == Profile.FLEXIBLE_TEMPORAL_NUMERIC) ? FLEX_READER : STRICT_READER;
    }
}
