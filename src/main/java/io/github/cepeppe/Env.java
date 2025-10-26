package io.github.cepeppe;

import io.github.cepeppe.logging.ApcaRestClientLogger;
import lombok.experimental.UtilityClass;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@UtilityClass
public class Env {
    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(Env.class);
    private static final Map<String, String> DOTENV = loadDotenv(Paths.get(".env"));

    public static String get(String key) {
        String v = System.getProperty(key);
        if (v != null) return v;
        v = System.getenv(key);
        if (v != null) return v;
        v = DOTENV.get(key);
        if (v != null) return v;
        return null;
    }

    public static String getOrDefault(String key, String def) {
        return Optional.ofNullable(get(key)).orElse(def);
    }

    public static String require(String key) {
        String v = get(key);
        if (v == null || v.isBlank()) {
            LOG.error("Missing required configuration key '{}'", key);
            throw new IllegalStateException("Missing required config: " + key);
        }
        return v;
    }

    private static Map<String, String> loadDotenv(Path path) {
        Map<String, String> map = new HashMap<>();
        if (!Files.exists(path)) {
            LOG.info(".env not found at {}", path.toAbsolutePath());
            return map;
        }
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int n = 0;
            while ((line = br.readLine()) != null) {
                n++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq <= 0) continue; // skip righe senza '=' o con chiave vuota
                String key = trimmed.substring(0, eq).trim();
                String val = trimmed.substring(eq + 1).trim();

                // Rimuove eventuali virgolette esterne
                if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                    val = val.substring(1, val.length() - 1);
                }
                map.put(key, val);
            }
            LOG.info(".env loaded with {} entries from {}", map.size(), path.toAbsolutePath());
        } catch (IOException e) {
            LOG.warn("Error reading .env at {}: {}", path.toAbsolutePath(), e.getMessage(), e);
        }
        return map;
    }

    /** Come get(), ma con default. */
    public static String getOr(String key, String defaultValue) {
        String v = get(key);
        if (v==null) return defaultValue;
        else if (v.isBlank()) return defaultValue;
        return v;
    }

    /** Boolean helper: true se "true"/"1"/"yes" (case-insensitive). */
    public static boolean getBool(String key, boolean defaultValue) {
        String v = get(key);
        if (v == null) return defaultValue;
        v = v.trim().toLowerCase();
        return Objects.equals(v, "true") || Objects.equals(v, "1") || Objects.equals(v, "yes");
    }

    /** int helper */
    public static int getInt(String key, int defaultValue) {
        String v = get(key);
        if (v == null) return defaultValue;
        v = v.trim().toLowerCase();

        int res;

        try{
            res = Integer.parseInt(v);
        }catch (NumberFormatException nfe){
            LOG.error("Unable to parse to int: " + v);
            return defaultValue;
        }
        return res;
    }

    /** Long helper */
    public static long getLong(String key, long defaultValue) {
        String v = get(key);
        if (v == null) return defaultValue;
        v = v.trim().toLowerCase();

        long res;

        try{
            res = Long.parseLong(v);
        }catch (NumberFormatException nfe){
            LOG.error("Unable to parse to long: " + v);
            return defaultValue;
        }
        return res;
    }

    /** Double helper */
    public static double getDouble(String key, double defaultValue) {
        String v = get(key);
        if (v == null) return defaultValue;
        v = v.trim().toLowerCase();

        double res;
        try {
            res = Double.parseDouble(v);
        } catch (NumberFormatException nfe) {
            LOG.error("Unable to parse to double: " + v);
            return defaultValue;
        }
        return res;
    }


    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static List getListOf(String key, Class type, List defaultValue) {
        return (List) getListOfOrDefault(key, (Class<Object>) type, (List<Object>) defaultValue);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static Set getSetOf(String key, Class type, Set defaultValue) {
        return (Set) getSetOfOrDefault(key, (Class<Object>) type, (Set<Object>) defaultValue);
    }

    private static <T> List<T> getListOfOrDefault(String key, Class<T> type, List<T> defaultValue) {
        String raw = get(key);
        if (raw == null || raw.isBlank()) return defaultValue;

        List<String> parts = splitListLiteral(raw);
        List<T> out = new ArrayList<>(parts.size());

        for (String p : parts) {
            T v = tryConvert(p, type);
            if (v != null) {
                out.add(v);
            } else {
                LOG.warn("Unable to convert '{}' to {}", p, type.getSimpleName());
            }
        }
        return out.isEmpty() ? defaultValue : out;
    }

    /** Come getListOf(), ma elimina i duplicati (mantiene l’ordine d’incontro). */
    private static <T> Set<T> getSetOfOrDefault(String key, Class<T> type, Set<T> defaultValue) {
        List<T> list = getListOf(key, type, null);
        if (list == null || list.isEmpty()) return defaultValue;
        return new LinkedHashSet<>(list);
    }

    /* =========================
        Helper privati di parsing
    ========================= */

    /** Divide una rappresentazione testuale di lista in elementi, rispettando virgolette e backslash-escape. */
    private static List<String> splitListLiteral(String raw) {
        String s = raw.trim();

        // Consenti notazioni con [] o () esterne
        if ((s.startsWith("[") && s.endsWith("]")) || (s.startsWith("(") && s.endsWith(")"))) {
            s = s.substring(1, s.length() - 1);
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        char quoteCh = 0;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (inQuotes) {
                if (c == '\\') { // escape dentro stringa
                    if (i + 1 < s.length()) cur.append(s.charAt(++i));
                    else cur.append('\\');
                } else if (c == quoteCh) {
                    inQuotes = false;
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"' || c == '\'') {
                    inQuotes = true;
                    quoteCh = c;
                } else if (c == ',' || c == ';') { // separatori supportati
                    String t = cur.toString().trim();
                    if (!t.isEmpty()) tokens.add(stripOuterQuotes(t));
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        String last = cur.toString().trim();
        if (!last.isEmpty()) tokens.add(stripOuterQuotes(last));

        // Filtra eventuali vuoti residui
        List<String> clean = new ArrayList<>(tokens.size());
        for (String t : tokens) {
            if (t != null && !t.isBlank()) clean.add(t.trim());
        }
        return clean;
    }

    /** Rimuove virgolette esterne da un token, se presenti. */
    private static String stripOuterQuotes(String v) {
        if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    /** Converte la stringa nel tipo richiesto. Aggiungi qui altri tipi se ti servono. */
    @SuppressWarnings("unchecked")
    private static <T> T tryConvert(String s, Class<T> type) {
        try {
            if (type == String.class)                return (T) s;
            if (type == Integer.class || type == int.class)
                return (T) Integer.valueOf(s);
            if (type == Long.class || type == long.class)
                return (T) Long.valueOf(s);
            if (type == Boolean.class || type == boolean.class) {
                String v = s.trim().toLowerCase();
                return (T) Boolean.valueOf("true".equals(v) || "1".equals(v) || "yes".equals(v));
            }
            if (type == Double.class || type == double.class)
                return (T) Double.valueOf(s);
            if (type == Float.class || type == float.class)
                return (T) Float.valueOf(s);
            if (type == Short.class || type == short.class)
                return (T) Short.valueOf(s);
            if (type == Path.class)    return (T) Paths.get(s);
            // Tipi custom: prova un costruttore(String) se esiste
            try {
                var ctor = type.getDeclaredConstructor(String.class);
                ctor.setAccessible(true);
                return ctor.newInstance(s);
            } catch (NoSuchMethodException ignore) {
                // niente costruttore(String): continua sotto
            }
            LOG.error("Unsupported conversion to {}", type.getName());
            return null;
        } catch (Exception e) {
            LOG.warn("Conversion error '{}' -> {}: {}", s, type.getSimpleName(), e.toString());
            return null;
        }
    }

}
