package io.github.cepeppe.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test di robustezza per HttpUtils.
 *
 * Copre:
 * - joinUrl: gestione slash doppi/mancanti, null/blank.
 * - setQueryParams: map vuota/null, chiavi/valori null/blank, encoding, query pre-esistente, frammento (#...),
 *                   trailing "?", ordine dei parametri (LinkedHashMap), rimozione slash finale.
 * - setPathParam: aggiunta segment con encoding (spazi, unicode, slash), base con/without slash.
 * - normalizeListForQueryParams: null/empty/solo blank, mix, nessuna virgola finale.
 * - safePreview: null, maxLen<=0, stringhe corte/lunghe, esatto maxLen.
 */
class HttpUtilsTest {

    // -----------------------------------------------------------------------------------------------------------------
    // joinUrl
    // -----------------------------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("joinUrl()")
    class JoinUrlTests {

        @Test
        @DisplayName("Base con '/' finale e path con '/' iniziale → niente doppio slash")
        void joinUrl_doubleSlashAvoided() {
            String out = HttpUtils.joinUrl("https://api.example.com/v2/", "/clock");
            assertEquals("https://api.example.com/v2/clock", out);
        }

        @Test
        @DisplayName("Base senza '/' finale e path senza '/' iniziale → aggiunge '/'")
        void joinUrl_missingSlashAdded() {
            String out = HttpUtils.joinUrl("https://api.example.com/v2", "clock");
            assertEquals("https://api.example.com/v2/clock", out);
        }

        @Test
        @DisplayName("Base con '/' finale e path senza '/' iniziale → concatena diretto")
        void joinUrl_happyPath() {
            String out = HttpUtils.joinUrl("https://api.example.com/v2/", "clock");
            assertEquals("https://api.example.com/v2/clock", out);
        }

        @Test
        @DisplayName("Base senza '/' finale e path con '/' iniziale → concatena diretto")
        void joinUrl_happyPath2() {
            String out = HttpUtils.joinUrl("https://api.example.com/v2", "/clock");
            assertEquals("https://api.example.com/v2/clock", out);
        }

        @Test
        @DisplayName("Path null → restituisce base")
        void joinUrl_nullPath() {
            String out = HttpUtils.joinUrl("https://api.example.com/v2", null);
            assertEquals("https://api.example.com/v2", out);
        }

        @Test
        @DisplayName("Path blank → restituisce base")
        void joinUrl_blankPath() {
            String out = HttpUtils.joinUrl("https://api.example.com/v2", "  ");
            assertEquals("https://api.example.com/v2", out);
        }

        @Test
        @DisplayName("Base null/blank → restituisce path")
        void joinUrl_nullOrBlankBase() {
            assertEquals("clock", HttpUtils.joinUrl(null, "clock"));
            assertEquals("/clock", HttpUtils.joinUrl("  ", "/clock"));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // setQueryParams
    // -----------------------------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("setQueryParams()")
    class SetQueryParamsTests {

        @Test
        @DisplayName("Map null o vuota → URL invariato (niente '?')")
        void setQueryParams_emptyMapNoChange() {
            String base = "https://api.example.com/v2/clock";
            assertEquals(base, HttpUtils.setQueryParams(base, null));
            assertEquals(base, HttpUtils.setQueryParams(base, Map.of()));
        }

        @Test
        @DisplayName("Filtra chiavi/valori null/blank → aggiunge solo i validi")
        void setQueryParams_filtersNullBlank() {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("a", "1");
            params.put(null, "x");
            params.put("  ", "y");
            params.put("b", null);
            params.put("c", "   "); // blank
            params.put("d", "2");
            String out = HttpUtils.setQueryParams("https://api.example.com/v2/clock", params);
            // Ordine deterministico grazie a LinkedHashMap
            assertEquals("https://api.example.com/v2/clock?a=1&d=2", out);
        }

        @Test
        @DisplayName("URL-encoding query: spazi, slash, unicode")
        void setQueryParams_encoding() {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("a b", "c/d");
            params.put("symb", "€uro"); // unicode
            String out = HttpUtils.setQueryParams("https://h/x", params);
            // URLEncoder: ' ' -> '+', '/' -> %2F, '€' -> %E2%82%AC
            assertEquals("https://h/x?a+b=c%2Fd&symb=%E2%82%ACuro", out);
        }

        @Test
        @DisplayName("Query pre-esistente → appende con & preservandola")
        void setQueryParams_existingQuery() {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("a", "1");
            params.put("b", "2");
            String out = HttpUtils.setQueryParams("https://h/x?foo=bar", params);
            assertEquals("https://h/x?foo=bar&a=1&b=2", out);
        }

        @Test
        @DisplayName("Preserva frammento #frag spostando la query prima del frammento")
        void setQueryParams_preservesFragment() {
            Map<String, String> params = Map.of("q", "v");
            String out = HttpUtils.setQueryParams("https://h/x#frag", params);
            assertEquals("https://h/x?q=v#frag", out);
        }

        @Test
        @DisplayName("URL che termina con '?' → rimuove '?' se non ci sono parametri validi")
        void setQueryParams_stripLonelyQuestionMark() {
            // Tutti invalidi → niente parametri → '?' rimosso
            Map<String, String> params = new LinkedHashMap<>();
            params.put(" ", " ");
            params.put(null, "x");
            String out = HttpUtils.setQueryParams("https://h/x?", params);
            assertEquals("https://h/x", out);
        }

        @Test
        @DisplayName("Rimozione slash finale ridondante su base (tranne lo schema)")
        void setQueryParams_stripTrailingSlashOnBase() {
            Map<String, String> p = Map.of("a", "1");
            String out = HttpUtils.setQueryParams("https://h/x/", p);
            assertEquals("https://h/x?a=1", out);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // setPathParam
    // -----------------------------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("setPathParam()")
    class SetPathParamTests {

        @Test
        @DisplayName("Aggiunge segment con '/' corretta (base senza slash finale)")
        void setPathParam_addsSlash() {
            String out = HttpUtils.setPathParam("https://api.example.com/v2", "clock");
            assertEquals("https://api.example.com/v2/clock", out);
        }

        @Test
        @DisplayName("Base già con slash finale → non aggiunge slash ulteriore")
        void setPathParam_respectsExistingSlash() {
            String out = HttpUtils.setPathParam("https://api.example.com/v2/", "clock");
            assertEquals("https://api.example.com/v2/clock", out);
        }

        @Test
        @DisplayName("Encoding del path segment: spazi → %20; slash non consentito (→ %2F)")
        void setPathParam_encodesSegment() {
            String out1 = HttpUtils.setPathParam("https://h/x", "my file");
            assertEquals("https://h/x/my%20file", out1);

            String out2 = HttpUtils.setPathParam("https://h/x", "a/b");
            // slash del segmento deve essere codificato come %2F (per evitare path injection)
            assertEquals("https://h/x/a%2Fb", out2);
        }

        @Test
        @DisplayName("Unicode nel segment (es. €) viene codificato")
        void setPathParam_unicodeEncoded() {
            String out = HttpUtils.setPathParam("https://h/x", "€uro");
            assertEquals("https://h/x/%E2%82%ACuro", out);
        }

        @Test
        @DisplayName("url o pathArg null/blank → restituisce url invariato")
        void setPathParam_nullBlank() {
            assertEquals("https://h/x", HttpUtils.setPathParam("https://h/x", " "));
            assertEquals("https://h/x", HttpUtils.setPathParam("https://h/x", null));
            assertNull(HttpUtils.setPathParam(null, "clock"));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // normalizeListForQueryParams
    // -----------------------------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("normalizeListForQueryParams()")
    class NormalizeListTests {

        @Test
        @DisplayName("Lista normale → CSV senza virgola finale")
        void normalize_ok() {
            String csv = HttpUtils.normalizeListForQueryParams(List.of("abc", "d", "ef"));
            assertEquals("abc,d,ef", csv);
        }

        @Test
        @DisplayName("Lista con null/blank → filtra e produce CSV corretto")
        void normalize_filters() {
            // FIX: List.of(...) non consente elementi null → NPE alla costruzione della lista.
            // Per testare il filtro del metodo, usiamo Arrays.asList(...) che accetta null.
            String csv = HttpUtils.normalizeListForQueryParams(Arrays.asList("  ", null, "x", " y "));
            assertEquals("x,y", csv);
        }

        @Test
        @DisplayName("Lista null/empty/tutti blank → stringa vuota (no eccezioni)")
        void normalize_emptyCases() {
            assertEquals("", HttpUtils.normalizeListForQueryParams(null));
            assertEquals("", HttpUtils.normalizeListForQueryParams(List.of()));
            assertEquals("", HttpUtils.normalizeListForQueryParams(List.of(" ", "   ")));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // safePreview
    // -----------------------------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("safePreview()")
    class SafePreviewTests {

        @Test
        @DisplayName("Null → 'null'")
        void preview_null() {
            assertEquals("null", HttpUtils.safePreview(null, 10));
        }

        @Test
        @DisplayName("maxLen <= 0 → stringa vuota")
        void preview_nonPositiveLen() {
            assertEquals("", HttpUtils.safePreview("abcdef", 0));
            assertEquals("", HttpUtils.safePreview("abcdef", -5));
        }

        @Test
        @DisplayName("Stringa corta → invariata")
        void preview_short() {
            assertEquals("abc", HttpUtils.safePreview("abc", 10));
        }

        @Test
        @DisplayName("Troncamento con '...' quando s.length > maxLen")
        void preview_truncate() {
            assertEquals("abc...", HttpUtils.safePreview("abcdef", 3));
        }

        @Test
        @DisplayName("Lunghezza esatta = maxLen → nessun '...'")
        void preview_exactLen() {
            assertEquals("abcd", HttpUtils.safePreview("abcd", 4));
        }
    }
}
