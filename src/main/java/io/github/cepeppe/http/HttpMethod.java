package io.github.cepeppe.http;

/**
 * Enum che modella i metodi HTTP standard (RFC 7231) più PATCH (RFC 5789).
 *
 * Ogni valore espone:
 *  - safe:          true se il metodo è "sicuro" (non modifica lo stato lato server). RFC 7231 §4.2.1
 *  - idempotent:    true se chiamate ripetute hanno lo stesso effetto. RFC 7231 §4.2.2
 *  - requestBody:   policy sull'uso del corpo nella richiesta (vedi BodyPolicy sotto)
 *  - responseBody:  true se una risposta può contenere un corpo (HEAD = false). RFC 7231 §4.3
 *  - cacheByDefault:true se le risposte sono cacheable per default (GET/HEAD). RFC 7234
 *  - rfc:           riferimento normativo
 *  - description:   breve descrizione d’uso
 *
 * Note pratiche:
 *  - Molti metodi *possono* avere un corpo in richiesta anche se è raro o sconsigliato (es. GET/DELETE/OPTIONS/TRACE).
 *    Alcuni server/client potrebbero ignorarlo o rifiutarlo.
 *  - HEAD non deve avere corpo in risposta; TRACE riflette la richiesta.
 *  - POST non è idempotente; PUT/DELETE lo sono; GET/HEAD/OPTIONS/TRACE sono anche "safe".
 */
public enum HttpMethod {

    /**
     * Recupera una risorsa senza effetti collaterali.
     * RFC 7231 §4.3.1. Safe, idempotent, cacheable by default.
     */
    GET(true,  true,  BodyPolicy.DISCOURAGED, true,  true,
            "RFC 7231 §4.3.1",
            "Recupera una rappresentazione della risorsa (lettura)."),

    /**
     * Come GET ma la risposta non ha corpo.
     * RFC 7231 §4.3.2. Safe, idempotent, cacheable by default.
     */
    HEAD(true, true,  BodyPolicy.DISCOURAGED, false, true,
            "RFC 7231 §4.3.2",
            "Come GET, ma il server NON invia il corpo della risposta."),

    /**
     * Invia dati al server per creare/elaborare una risorsa.
     * RFC 7231 §4.3.3. Non safe, non idempotent.
     */
    POST(false, false, BodyPolicy.OPTIONAL,    true,  false,
            "RFC 7231 §4.3.3",
            "Crea o elabora sotto una risorsa target (form, upload, RPC, ecc.)."),

    /**
     * Sostituisce integralmente la rappresentazione della risorsa target.
     * RFC 7231 §4.3.4. Idempotent.
     */
    PUT(false, true,  BodyPolicy.OPTIONAL,     true,  false,
            "RFC 7231 §4.3.4",
            "Sostituisce la risorsa con il payload inviato."),

    /**
     * Rimuove la risorsa target.
     * RFC 7231 §4.3.5. Idempotent.
     */
    DELETE(false, true, BodyPolicy.DISCOURAGED, true, false,
            "RFC 7231 §4.3.5",
            "Elimina la risorsa identificata dall’URI."),

    /**
     * Stabilisce un tunnel con il server di destinazione (es. HTTPS via proxy).
     * RFC 7231 §4.3.6. Non safe, non idempotent.
     */
    CONNECT(false, false, BodyPolicy.OPTIONAL,  true,  false,
            "RFC 7231 §4.3.6",
            "Stabilisce un tunnel TCP verso l’authority target (tipico con proxy)."),

    /**
     * Interroga le opzioni di comunicazione disponibili per la risorsa o server.
     * RFC 7231 §4.3.7. Safe, idempotent.
     */
    OPTIONS(true, true, BodyPolicy.DISCOURAGED, true,  false,
            "RFC 7231 §4.3.7",
            "Scopre metodi/supporto (CORS, Allow, ecc.)."),

    /**
     * Effettua un loopback diagnostico: il server riflette la richiesta.
     * RFC 7231 §4.3.8. Safe, idempotent.
     */
    TRACE(true, true, BodyPolicy.DISCOURAGED,  true,  false,
            "RFC 7231 §4.3.8",
            "Diagnostica: il server ritorna la richiesta ricevuta."),

    /**
     * Applica modifiche parziali alla risorsa.
     * RFC 5789. Non idempotent per definizione generale (può esserlo con patch docili).
     */
    PATCH(false, false, BodyPolicy.OPTIONAL,   true,  false,
            "RFC 5789",
            "Aggiorna parzialmente la risorsa (merge/JSON Patch, ecc.).");

    // --- API pubblica dell'enum ------------------------------------------------

    /** Policy di utilizzo del corpo nella richiesta. */
    public enum BodyPolicy {
        /**
         * È tecnicamente permesso ma sconsigliato/insolito e potrebbe essere ignorato o rifiutato
         * da alcuni stack. Tipico per GET/HEAD/DELETE/OPTIONS/TRACE.
         */
        DISCOURAGED,
        /**
         * Il corpo è accettato/atteso in molti scenari, ma non è obbligatorio.
         * Tipico per POST/PUT/PATCH/CONNECT.
         */
        OPTIONAL
        // Nota: in HTTP non esiste un vero "FORBIDDEN" generale per il request body dei metodi standard.
        // HEAD ha "nessun significato" per un payload di richiesta secondo RFC 7231 §4.3.2.
    }

    private final boolean safe;
    private final boolean idempotent;
    private final BodyPolicy requestBody;
    private final boolean responseBodyAllowed;
    private final boolean cacheByDefault;
    private final String rfc;
    private final String description;

    HttpMethod(boolean safe,
               boolean idempotent,
               BodyPolicy requestBody,
               boolean responseBodyAllowed,
               boolean cacheByDefault,
               String rfc,
               String description) {
        this.safe = safe;
        this.idempotent = idempotent;
        this.requestBody = requestBody;
        this.responseBodyAllowed = responseBodyAllowed;
        this.cacheByDefault = cacheByDefault;
        this.rfc = rfc;
        this.description = description;
    }

    /** @return true se il metodo è "safe" (non modifica lo stato lato server). */
    public boolean isSafe() { return safe; }

    /** @return true se il metodo è idempotente. */
    public boolean isIdempotent() { return idempotent; }

    /** @return policy sull'uso del corpo nella richiesta. */
    public BodyPolicy requestBodyPolicy() { return requestBody; }

    /** @return true se è lecito che la risposta contenga un corpo (HEAD = false). */
    public boolean isResponseBodyAllowed() { return responseBodyAllowed; }

    /**
     * @return true se il metodo è cacheable "by default" (GET/HEAD).
     * Altri metodi possono essere cacheati solo con direttive esplicite. Vedi RFC 7234.
     */
    public boolean isCacheableByDefault() { return cacheByDefault; }

    /** @return riferimento normativo (RFC). */
    public String rfcReference() { return rfc; }

    /** @return breve descrizione d’uso. */
    public String description() { return description; }

    /**
     * Parsing tollerante al case; lancia IllegalArgumentException se sconosciuto.
     * @param name nome del metodo, es. "get", "POST"
     */
    public static HttpMethod fromString(String name) {
        return HttpMethod.valueOf(name.trim().toUpperCase());
    }

    @Override
    public String toString() {
        return name() + " (safe=" + safe
                + ", idempotent=" + idempotent
                + ", requestBody=" + requestBody
                + ", responseBodyAllowed=" + responseBodyAllowed
                + ", cacheByDefault=" + cacheByDefault
                + ')';
    }
}

