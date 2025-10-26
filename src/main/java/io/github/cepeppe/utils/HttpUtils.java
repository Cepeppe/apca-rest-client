package io.github.cepeppe.utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Utility HTTP/URL a basso livello (senza dipendenze esterne).
 *
 * <p><b>Obiettivi</b>:
 * <ul>
 *   <li>Composizione sicura di URL (join base+path, path param, query param).</li>
 *   <li>Gestione difensiva di {@code null}/blank, map/list vuote, e caratteri speciali.</li>
 *   <li>URL-encoding UTF-8 dove appropriato (query & path segment).</li>
 * </ul>
 *
 * <p><b>Nota</b>: la <i>validazione semantica</i> degli input (es. combinazioni di parametri ammesse dall'API)
 * resta responsabilità del chiamante. Qui si cura la robustezza sintattica dell'URL.</p>
 */
public final class HttpUtils {

    private HttpUtils(){}

    /**
     * Unisce in modo sicuro un URL base e un path, evitando doppie o mancanti slash.
     *
     * <p>Se {@code path} è null o vuoto, restituisce {@code base} invariato (fallback difensivo).
     * Gestisce correttamente i casi base/"/" + "/path" e base senza "/" + "path".</p>
     *
     * @param base base URL (es. {@code https://api.alpaca.markets/v2})
     * @param path path logico (con o senza slash iniziale, es. {@code "clock"} o {@code "/clock"})
     * @return URL risultante (es. {@code https://api.alpaca.markets/v2/clock})
     */
    public static String joinUrl(String base, String path) {
        if (base == null || base.isBlank()) return path;     // fallback difensivo
        if (path == null || path.isBlank()) return base;     // nessun path da unire

        boolean baseEndsWithSlash = base.endsWith("/");
        boolean pathStartsWithSlash = path.startsWith("/");
        if (baseEndsWithSlash && pathStartsWithSlash) {
            return base + path.substring(1);
        } else if (!baseEndsWithSlash && !pathStartsWithSlash) {
            return base + "/" + path;
        } else {
            return base + path;
        }
    }

    /**
     * Restituisce un URL con i query param aggiunti/settati, partendo da {@code url}.
     *
     * <p><b>Comportamento</b>:
     * <ul>
     *   <li>Se {@code queryParams} è null o vuota → restituisce l'URL invariato (non aggiunge {@code ?}).</li>
     *   <li>Filtra entry con chiave o valore null/blank (non vengono aggiunte).</li>
     *   <li>Applica URL-encoding UTF-8 a nomi e valori dei parametri.</li>
     *   <li>Se l'URL ha già una query, appende con {@code &} preservando quella esistente.</li>
     *   <li>Preserva un eventuale frammento (parte dopo {@code #}).</li>
     * </ul>
     *
     * <p><b>Esempio</b>:
     * <pre>
     * setQueryParams("https://x.y/z?foo=1", Map.of("a b", "c/d"))
     * → https://x.y/z?foo=1&amp;a+b=c%2Fd
     * </pre>
     *
     * @param url         URL base (può già contenere una query o un frammento)
     * @param queryParams mappa nome→valore; per liste usare una singola stringa CSV
     *                    (vedi {@link #normalizeListForQueryParams(List)})
     * @return URL con query params (encoded) oppure URL invariato se non ci sono parametri validi
     */
    public static String setQueryParams(String url, Map<String, String> queryParams) {
        if (url == null || url.isBlank()) return url;
        if (queryParams == null || queryParams.isEmpty()) return stripTrailingQuestionMark(url); // nessuna mutazione

        // Isola l'eventuale frammento #...
        String fragment = null;
        int hashIdx = url.indexOf('#');
        if (hashIdx >= 0) {
            fragment = url.substring(hashIdx);  // include '#'
            url = url.substring(0, hashIdx);    // parte prima del frammento
        }

        // Rimuove trailing slash ridondante (ma non tocca "http://")
        if (url.endsWith("/")) {
            int schemeIdx = url.indexOf("://");
            boolean endsExactlyWithScheme = (schemeIdx >= 0) && (schemeIdx == url.length() - 3);
            if (!endsExactlyWithScheme) {
                url = url.substring(0, url.length() - 1);
            }
        }

        // Separa eventuale query già presente
        String base = url;
        String existingQuery = null;
        int qIdx = url.indexOf('?');
        if (qIdx >= 0) {
            base = url.substring(0, qIdx);
            existingQuery = (qIdx + 1 < url.length()) ? url.substring(qIdx + 1) : "";
        }

        // Filtra e codifica i nuovi parametri
        String newQuery = queryParams.entrySet().stream()
                .filter(e -> e.getKey() != null && !e.getKey().isBlank())
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .map(e -> encodeQueryComponent(e.getKey()) + "=" + encodeQueryComponent(e.getValue()))
                .collect(Collectors.joining("&"));

        // Se non ci sono parametri validi → restituisci l'URL originale
        if (newQuery.isBlank()) {
            return stripTrailingQuestionMark(url) + (fragment != null ? fragment : "");
        }

        StringBuilder out = new StringBuilder(base);
        if (existingQuery == null || existingQuery.isBlank()) {
            out.append('?').append(newQuery);
        } else {
            out.append('?').append(existingQuery);
            if (!existingQuery.endsWith("&")) out.append('&');
            out.append(newQuery);
        }

        if (fragment != null) out.append(fragment);
        return out.toString();
    }

    /**
     * Restituisce un URL con il prossimo <i>path segment</i> aggiunto.
     *
     * <p><b>Comportamento</b>:
     * <ul>
     *   <li>Se {@code url} o {@code pathArg} sono null/blank → restituisce {@code url} invariato.</li>
     *   <li>Garantisce uno e un solo '/' tra base e segment.</li>
     *   <li>Applica encoding del path segment (spazi → {@code %20}, non {@code +}).</li>
     * </ul>
     *
     * <p><b>Nota</b>: questo metodo si aspetta una base <i>senza query/frammenti</i>. Se usato
     * su URL con parametri o frammenti, il segment verrà aggiunto in coda all'intera stringa.</p>
     *
     * @param url     base url (idealmente senza query/frammenti)
     * @param pathArg prossimo path segment (non l'intero path)
     * @return url con path param correttamente settato
     */
    public static String setPathParam(String url, String pathArg){
        if (url == null || url.isBlank() || pathArg == null || pathArg.isBlank())
            return url; // fallback difensivo

        String encoded = encodePathSegment(pathArg);

        StringBuilder sb = new StringBuilder(url);
        if (!url.endsWith("/"))
            sb.append("/");
        sb.append(encoded);
        return sb.toString();
    }

    /**
     * Restituisce una stringa a partire da una lista per uso come query param CSV.
     *
     * <p>Esempio: {@code ["abc","d","ef"] → "abc,d,ef"}</p>
     *
     * <p><b>Robustezza</b>:
     * <ul>
     *   <li>Filtra elementi null/blank.</li>
     *   <li>Se la lista è null/vuota o tutti gli elementi sono blank → restituisce stringa vuota (nessuna eccezione).</li>
     *   <li><b>Attenzione</b>: non fa encoding né rimuove virgole interne; evitare elementi contenenti {@code ','}.</li>
     * </ul>
     * </p>
     *
     * @param list lista di stringhe
     * @return Stringa CSV (potenzialmente vuota se non ci sono elementi validi)
     */
    public static String normalizeListForQueryParams(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(","));
    }

    /**
     * Restituisce una piccola anteprima sicura del body di risposta per messaggi d'errore/logging.
     *
     * @param s      stringa originale (può essere {@code null})
     * @param maxLen numero massimo di caratteri
     * @return anteprima troncata con "..." se necessario
     */
    public static String safePreview(String s, int maxLen) {
        if (s == null) return "null";
        if (maxLen <= 0) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // ======================================================================
    // Helpers privati
    // ======================================================================

    /** URL-encode per componenti di query (UTF-8). Converte spazi in '+' come da application/x-www-form-urlencoded. */
    private static String encodeQueryComponent(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    /**
     * Encode "path segment" stile RFC3986 (spazi → %20, non '+').
     * <p>Nota: non si consente l'inserimento di '/'; se presente nel segmento verrà codificato come {@code %2F}.</p>
     */
    private static String encodePathSegment(String segment) {
        String enc = URLEncoder.encode(segment, StandardCharsets.UTF_8);
        // URLEncoder è pensato per query: normalizziamo gli spazi per il PATH
        enc = enc.replace("+", "%20");
        // NON decodifichiamo '%2F' in '/', per evitare l'iniezione di segmenti aggiuntivi.
        return enc;
    }

    /** Se la stringa termina con '?', la rimuove; altrimenti la restituisce invariata. */
    private static String stripTrailingQuestionMark(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.endsWith("?") ? s.substring(0, s.length() - 1) : s;
    }
}
