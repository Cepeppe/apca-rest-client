package io.github.cepeppe.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Domain exception
 * <p>
 * Caratteristiche:
 * <ul>
 *   <li>Unchecked: estende {@link RuntimeException} per evitare boilerplate di catch.</li>
 *   <li>Codice macchina {@code code}: permette categorizzazione/observability.</li>
 *   <li>Context immutabile {@code context}: metadati (chiave/valore) utili al debug/logging.</li>
 * </ul>
 *
 * <strong>Nota</strong>: evitare di inserire nel context PII o segreti (API key, token, ecc.).
 */
public class ApcaRestClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Codice "macchina" dell'errore, utile per metrics/alerting (es. "CONFIG", "EXTERNAL_SERVICE", ...).
     */
    private final String code;

    /**
     * Metadati immutabili che contestualizzano l'errore (es. {@code orderId}, {@code endpoint}, {@code responseCode}).
     */
    private final Map<String, Object> context;

    /* =======================
       Costruttori principali
       ======================= */

    public ApcaRestClientException(String message) {
        this(null, message, null, null);
    }

    public ApcaRestClientException(String message, Throwable cause) {
        this(null, message, null, cause);
    }

    public ApcaRestClientException(String code, String message) {
        this(code, message, null, null);
    }

    public ApcaRestClientException(String code, String message, Throwable cause) {
        this(code, message, null, cause);
    }

    public ApcaRestClientException(String code, String message, Map<String, ?> context) {
        this(code, message, context, null);
    }

    public ApcaRestClientException(String code, String message, Map<String, ?> context, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.context = toImmutableContext(context);
    }

    /* =======================
       Factory helpers
       ======================= */

    /**
     * Crea un'eccezione con coppie chiave/valore (varargs) per il context.
     * Esempio: {@code ApcaRestClientException.of("EXTERNAL_SERVICE","Call failed","endpoint",url,"status",503)}
     *
     * @throws IllegalArgumentException se il numero di elementi di {@code kvPairs} è dispari
     */
    public static ApcaRestClientException of(String code, String message, Object... kvPairs) {
        return new ApcaRestClientException(code, message, fromPairs(kvPairs), null);
    }

    /** Wrappa una {@code cause} aggiungendo un codice e un messaggio. */
    public static ApcaRestClientException wrap(Throwable cause, String code, String message, Object... kvPairs) {
        if (cause instanceof ApcaRestClientException mme) {
            // opzionale: arricchisci il context mantenendo il mme originale come cause
            Map<String, Object> merged = new LinkedHashMap<>(mme.getContext());
            merged.putAll(fromPairs(kvPairs));
            return new ApcaRestClientException(
                    code != null ? code : mme.getCode(),
                    message != null ? message : mme.getMessage(),
                    merged,
                    mme
            );
        }
        return new ApcaRestClientException(code, message, fromPairs(kvPairs), cause);
    }

    /* =======================
       Accessors
       ======================= */

    public String getCode() {
        return code;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    /* =======================
       Utility private
       ======================= */

    private static Map<String, Object> toImmutableContext(Map<String, ?> ctx) {
        if (ctx == null || ctx.isEmpty()) return Map.of();
        Map<String, Object> copy = new LinkedHashMap<>();
        ctx.forEach((k, v) -> copy.put(Objects.toString(k, "null"), v));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Object> fromPairs(Object... kvPairs) {
        if (kvPairs == null || kvPairs.length == 0) return Map.of();
        if ((kvPairs.length & 1) == 1) {
            throw new IllegalArgumentException("kvPairs deve avere un numero pari di elementi (chiave, valore, ...)");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kvPairs.length; i += 2) {
            String key = Objects.toString(kvPairs[i], "null");
            Object val = kvPairs[i + 1];
            map.put(key, val);
        }
        return Collections.unmodifiableMap(map);
    }

    /* =======================
       (Opzionale) Enum codici
       ======================= */

    /**
     * Enum suggerito per categorizzare gli errori; se preferisci, puoi
     * usare direttamente le stringhe nel campo {@code code}.
     */
    public enum Code {
        VALIDATION, CONFIG, AUTH, PERMISSION, NOT_FOUND, CONFLICT,
        TIMEOUT, EXTERNAL_SERVICE, RATE_LIMIT, INTERNAL, UNKNOWN
    }

    /** Costruttore di comodo che usa {@link Code}. */
    public static ApcaRestClientException of(Code code, String message, Object... kvPairs) {
        return of(code != null ? code.name() : null, message, kvPairs);
    }
}
