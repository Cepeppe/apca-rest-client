package io.github.cepeppe.examples.util;

import io.github.cepeppe.Env;
import io.github.cepeppe.json.JsonCodec;
import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.utils.HttpUtils;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * ExampleSupport
 *
 * Shared helpers for all examples:
 * - env/system property readers
 * - safe preview & JSON pretty
 * - masking, small timing utilities
 * - async "block-for-demo"
 * - baseUrl resolution (enum vs env override)
 */
public final class ExampleSupport {
    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(ExampleSupport.class);

    private ExampleSupport() {}

    /* =========================
       Environment / Properties
       ========================= */

    public static boolean readFlag(String sysProp, String env, boolean def) {
        String sp = System.getProperty(sysProp);
        if (sp != null) return sp.equalsIgnoreCase("true");
        String ev = System.getenv(env);
        if (ev != null) return ev.equalsIgnoreCase("true");
        return def;
    }

    public static String readStr(String sysProp, String env, String def) {
        String sp = System.getProperty(sysProp);
        if (sp != null && !sp.isBlank()) return sp;
        String ev = System.getenv(env);
        if (ev != null && !ev.isBlank()) return ev;
        return def;
    }

    public static BigDecimal readDecimal(String sysProp, String env, BigDecimal def) {
        try {
            String sp = System.getProperty(sysProp);
            if (sp != null && !sp.isBlank()) return new BigDecimal(sp);
            String ev = System.getenv(env);
            if (ev != null && !ev.isBlank()) return new BigDecimal(ev);
        } catch (Exception e) {
            LOG.warn("Invalid decimal for {} or {} → using default {}", sysProp, env, def);
        }
        return def;
    }

    public static String trimToNull(String v) {
        return Objects.toString(v, "").trim().isEmpty() ? null : v.trim();
    }

    /* ================
       Logging helpers
       ================ */

    public static String mask(String value) {
        if (value == null || value.isBlank()) return "<missing>";
        int len = value.length();
        String tail = len >= 4 ? value.substring(len - 4) : value;
        return "***" + tail + " (len=" + len + ")";
    }

    public static String pretty(Object dto) {
        try {
            return JsonCodec.toJson(dto);
        } catch (Exception e) {
            return String.valueOf(dto);
        }
    }

    public static String preview(Object o) {
        if (o == null) return "null";
        if (o instanceof Collection<?> c) {
            return c.stream().limit(1).findFirst()
                    .map(JsonCodec::toJson)
                    .orElse("[]");
        }
        return JsonCodec.toJson(o);
    }

    public static String safePreview(String s, int maxLen) {
        if (s == null) return "null";
        if (maxLen <= 0) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    public static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur != null && cur.getCause() != null) cur = cur.getCause();
        return Optional.ofNullable(cur).map(Throwable::getMessage).orElse("");
    }

    public static void blockForDemo(CompletableFuture<?> fut, int seconds) {
        try {
            fut.get(seconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.warn("Async wait interrupted ({} s): {}", seconds, e.toString());
        }
    }

    /* ===========
       Base URLs
       =========== */

    /**
     * Resolve effective base URL: prefer env override, fallback to endpoint enum.
     */
    public static String effectiveBaseUrl(AlpacaRestBaseEndpoints endpoint) {
        String override = switch (endpoint) {
            case API_V2_PAPER_TRADING -> trimToNull(Env.get("ALPACA_TRADING_PAPER_API_V2_URL"));
            case API_V2_PRODUCTION_TRADING -> trimToNull(Env.get("ALPACA_TRADING_PRODUCTION_API_V2_URL"));
            default -> null;
        };
        return override != null ? override : endpoint.baseUrl();
    }

    /* ==========
       Misc
       ========== */

    public static long ms(Instant t0, Instant t1) {
        return Duration.between(t0, t1).toMillis();
    }

    public static String maskPresent(String v) {
        return (v == null || v.isBlank()) ? "NO" : "YES";
    }

    public static String encode(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    public static String join(String base, String tail) {
        return HttpUtils.joinUrl(base, tail);
    }
}
