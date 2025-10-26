package io.github.cepeppe.rest.data.response;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * <h1>AlpacaMarketCalendarDayResponse</h1>
 *
 * DTO per una riga del calendario di mercato restituita da Alpaca
 * (endpoint {@code GET /v2/calendar} su paper/prod).
 *
 * <h2>Campi principali</h2>
 * <ul>
 *   <li><b>date</b> – {@code yyyy-MM-dd} (es. {@code "2025-12-17"}), giorno di calendario.</li>
 *   <li><b>open</b> – {@code HH:MM} (es. {@code "09:30"}), apertura della <b>sessione regolare</b> (RTH) in America/New_York.</li>
 *   <li><b>close</b> – {@code HH:MM} (es. {@code "16:00"}), chiusura della <b>sessione regolare</b> (RTH) in America/New_York.</li>
 *   <li><b>session_open</b> – {@code HHmm} (es. {@code "0400"}), apertura della <b>sessione estesa</b> (premarket + RTH + after-hours) in America/New_York.</li>
 *   <li><b>session_close</b> – {@code HHmm} (es. {@code "2000"}), chiusura della <b>sessione estesa</b> in America/New_York.</li>
 *   <li><b>settlement_date</b> – {@code yyyy-MM-dd}, data di regolamento (T+X) per la trade date.</li>
 * </ul>
 *
 * <p><b>Nota formati:</b> {@code open}/{@code close} hanno i due punti (HH:MM),
 * mentre {@code session_open}/{@code session_close} sono senza due punti (HHmm).</p>
 *
 * <h2>Fuso orario</h2>
 * <p>Tutti gli orari sono in {@code America/New_York} (US Eastern, con gestione automatica DST).
 * Usa i metodi di utilità per ottenere {@link ZonedDateTime} o {@link Instant} (UTC).</p>
 *
 * <h2>Scelte di modellazione</h2>
 * <ul>
 *   <li>Immutabile: campi {@code private final}; niente getters pubblici dei raw fields.</li>
 *   <li>Tollerante a campi extra: {@link JsonIgnoreProperties @JsonIgnoreProperties(ignoreUnknown = true)}.</li>
 *   <li>Nessuna logica di <i>decoding</i> JSON qui: il parsing è demandato al JsonCodec centrale.
 *       Qui offriamo solo metodi di utilità per costruire istanti/fusi partendo dai valori già deserializzati.</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(
        fieldVisibility   = JsonAutoDetect.Visibility.ANY,
        getterVisibility  = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility= JsonAutoDetect.Visibility.NONE,
        setterVisibility  = JsonAutoDetect.Visibility.NONE
)
public final class AlpacaMarketCalendarDayResponse {

    /** Fuso del mercato azionario USA (gestisce automaticamente DST). */
    public static final ZoneId US_EASTERN = ZoneId.of("America/New_York");

    @JsonProperty("date")
    private final LocalDate date;                 // yyyy-MM-dd

    @JsonProperty("open")
    private final LocalTime open;                 // HH:MM (regolare, Eastern)

    @JsonProperty("close")
    private final LocalTime close;                // HH:MM (regolare, Eastern)

    @JsonProperty("session_open")
    private final String sessionOpenRaw;          // HHmm (estesa, Eastern), es. "0400"

    @JsonProperty("session_close")
    private final String sessionCloseRaw;         // HHmm (estesa, Eastern), es. "2000"

    @JsonProperty("settlement_date")
    private final LocalDate settlementDate;       // yyyy-MM-dd

    public AlpacaMarketCalendarDayResponse(
            @JsonProperty("date")            LocalDate date,
            @JsonProperty("open")            LocalTime open,
            @JsonProperty("close")           LocalTime close,
            @JsonProperty("session_open")    String sessionOpenRaw,
            @JsonProperty("session_close")   String sessionCloseRaw,
            @JsonProperty("settlement_date") LocalDate settlementDate
    ) {
        this.date = date;
        this.open = open;
        this.close = close;
        this.sessionOpenRaw = sessionOpenRaw;
        this.sessionCloseRaw = sessionCloseRaw;
        this.settlementDate = settlementDate;
    }

    /* =======================
       Metodi di utilità (API)
       ======================= */

    /** {@return apertura <b>regolare</b> in Eastern, o {@code null} se data/ora mancanti} */
    public ZonedDateTime openAtZone() {
        if (date == null || open == null) return null;
        return ZonedDateTime.of(date, open, US_EASTERN);
    }

    /** {@return chiusura <b>regolare</b> in Eastern, o {@code null}} */
    public ZonedDateTime closeAtZone() {
        if (date == null || close == null) return null;
        return ZonedDateTime.of(date, close, US_EASTERN);
    }

    /** {@return apertura <b>regolare</b> come Instant UTC, o {@code null}} */
    public Instant openInstantUtc() {
        ZonedDateTime z = openAtZone();
        return (z == null) ? null : z.toInstant();
    }

    /** {@return chiusura <b>regolare</b> come Instant UTC, o {@code null}} */
    public Instant closeInstantUtc() {
        ZonedDateTime z = closeAtZone();
        return (z == null) ? null : z.toInstant();
    }

    /** {@return apertura <b>sessione estesa</b> come {@link LocalTime} (parsando HHmm), o {@code null} se assente/invalid} */
    public LocalTime sessionOpenTime() {
        return parseHHmm(sessionOpenRaw);
    }

    /** {@return chiusura <b>sessione estesa</b> come {@link LocalTime} (parsando HHmm), o {@code null} se assente/invalid} */
    public LocalTime sessionCloseTime() {
        return parseHHmm(sessionCloseRaw);
    }

    /** {@return apertura <b>sessione estesa</b> in Eastern, o {@code null}} */
    public ZonedDateTime sessionOpenAtZone() {
        LocalTime t = sessionOpenTime();
        if (date == null || t == null) return null;
        return ZonedDateTime.of(date, t, US_EASTERN);
    }

    /** {@return chiusura <b>sessione estesa</b> in Eastern, o {@code null}} */
    public ZonedDateTime sessionCloseAtZone() {
        LocalTime t = sessionCloseTime();
        if (date == null || t == null) return null;
        return ZonedDateTime.of(date, t, US_EASTERN);
    }

    /** {@return apertura <b>sessione estesa</b> come Instant UTC, o {@code null}} */
    public Instant sessionOpenInstantUtc() {
        ZonedDateTime z = sessionOpenAtZone();
        return (z == null) ? null : z.toInstant();
    }

    /** {@return chiusura <b>sessione estesa</b> come Instant UTC, o {@code null}} */
    public Instant sessionCloseInstantUtc() {
        ZonedDateTime z = sessionCloseAtZone();
        return (z == null) ? null : z.toInstant();
    }

    /** Varianti parametrizzabili di fuso (per test/usi speciali) */
    public ZonedDateTime openAtZone(ZoneId zoneId)        { return (date == null || open == null  || zoneId == null) ? null : ZonedDateTime.of(date, open,  zoneId); }
    public ZonedDateTime closeAtZone(ZoneId zoneId)       { return (date == null || close == null || zoneId == null) ? null : ZonedDateTime.of(date, close, zoneId); }
    public ZonedDateTime sessionOpenAtZone(ZoneId zoneId) { LocalTime t = sessionOpenTime();  return (date == null || t == null || zoneId == null) ? null : ZonedDateTime.of(date, t, zoneId); }
    public ZonedDateTime sessionCloseAtZone(ZoneId zoneId){ LocalTime t = sessionCloseTime(); return (date == null || t == null || zoneId == null) ? null : ZonedDateTime.of(date, t, zoneId); }

    /* ============== helper interni ============== */

    /** Converte una stringa HHmm (es. "0400") in LocalTime; restituisce {@code null} se la stringa è nulla/vuota/invalid. */
    private static LocalTime parseHHmm(String hhmm) {
        if (hhmm == null) return null;
        String s = hhmm.trim();
        if (s.length() != 4 || !s.chars().allMatch(Character::isDigit)) return null;
        int hh = Integer.parseInt(s.substring(0, 2));
        int mm = Integer.parseInt(s.substring(2, 4));
        if (hh < 0 || hh > 23 || mm < 0 || mm > 59) return null;
        return LocalTime.of(hh, mm);
    }

    /* equals/hashCode/toString per praticità (non espongono getters dei campi) */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AlpacaMarketCalendarDayResponse that)) return false;
        return Objects.equals(date, that.date)
                && Objects.equals(open, that.open)
                && Objects.equals(close, that.close)
                && Objects.equals(sessionOpenRaw, that.sessionOpenRaw)
                && Objects.equals(sessionCloseRaw, that.sessionCloseRaw)
                && Objects.equals(settlementDate, that.settlementDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, open, close, sessionOpenRaw, sessionCloseRaw, settlementDate);
    }

    @Override
    public String toString() {
        return "AlpacaMarketCalendarDayResponse{" +
                "date=" + date +
                ", open=" + open +
                ", close=" + close +
                ", sessionOpenRaw='" + sessionOpenRaw + '\'' +
                ", sessionCloseRaw='" + sessionCloseRaw + '\'' +
                ", settlementDate=" + settlementDate +
                '}';
    }
}
