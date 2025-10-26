package io.github.cepeppe.rest.data.response;


import io.github.cepeppe.json.JsonCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

class AlpacaMarketCalendarDayResponseTest {

    @Test
    @DisplayName("Happy path: parsing + utility methods (EST in dicembre)")
    void parseAndUtilities_estDecember() {
        String json = """
        {
          "date": "2025-12-17",
          "open": "09:30",
          "close": "16:00",
          "session_open": "0400",
          "session_close": "2000",
          "settlement_date": "2025-12-18"
        }
        """;

        AlpacaMarketCalendarDayResponse day =
                JsonCodec.fromJson(json, AlpacaMarketCalendarDayResponse.class);

        assertNotNull(day, "La response non deve essere null");

        // Fuso del mercato USA
        ZoneId ET = AlpacaMarketCalendarDayResponse.US_EASTERN;

        // Orari regolari in Eastern
        ZonedDateTime expectedOpenET  = ZonedDateTime.of(LocalDate.of(2025, 12, 17), LocalTime.of(9, 30), ET);
        ZonedDateTime expectedCloseET = ZonedDateTime.of(LocalDate.of(2025, 12, 17), LocalTime.of(16, 0), ET);

        assertEquals(expectedOpenET, day.openAtZone(), "Apertura regolare in Eastern");
        assertEquals(expectedCloseET, day.closeAtZone(), "Chiusura regolare in Eastern");

        // Dicembre = EST (UTC-5) → 09:30 ET = 14:30Z ; 16:00 ET = 21:00Z
        assertEquals(Instant.parse("2025-12-17T14:30:00Z"), day.openInstantUtc(),  "Apertura regolare in UTC");
        assertEquals(Instant.parse("2025-12-17T21:00:00Z"), day.closeInstantUtc(), "Chiusura regolare in UTC");

        // Sessione estesa (HHmm)
        assertEquals(LocalTime.of(4, 0),  day.sessionOpenTime(),  "session_open HHmm → LocalTime");
        assertEquals(LocalTime.of(20, 0), day.sessionCloseTime(), "session_close HHmm → LocalTime");

        ZonedDateTime expectedSessOpenET  = ZonedDateTime.of(LocalDate.of(2025, 12, 17), LocalTime.of(4, 0), ET);
        ZonedDateTime expectedSessCloseET = ZonedDateTime.of(LocalDate.of(2025, 12, 17), LocalTime.of(20, 0), ET);

        assertEquals(expectedSessOpenET,  day.sessionOpenAtZone(),  "Apertura estesa in Eastern");
        assertEquals(expectedSessCloseET, day.sessionCloseAtZone(), "Chiusura estesa in Eastern");

        // 04:00 EST = 09:00Z ; 20:00 EST = 01:00Z del giorno successivo
        assertEquals(Instant.parse("2025-12-17T09:00:00Z"), day.sessionOpenInstantUtc(),  "Apertura estesa in UTC");
        assertEquals(Instant.parse("2025-12-18T01:00:00Z"), day.sessionCloseInstantUtc(), "Chiusura estesa in UTC");

        // Nota: qui non asseriamo settlement_date, perché è già mappato a LocalDate dal JsonCodec,
        // ed è indipendente dagli orari. Se necessario:
        // assertEquals(LocalDate.of(2025, 12, 18), day.getSettlementDate());  // se esponi un accessor o reflection nei test
    }

    @Test
    @DisplayName("DST handling: giugno (EDT, UTC-4) → 09:30 ET = 13:30Z")
    void dstHandling_summerEdt() {
        String json = """
        {
          "date": "2025-06-17",
          "open": "09:30",
          "close": "16:00",
          "session_open": "0400",
          "session_close": "2000",
          "settlement_date": "2025-06-18"
        }
        """;

        AlpacaMarketCalendarDayResponse day =
                JsonCodec.fromJson(json, AlpacaMarketCalendarDayResponse.class);

        // Giugno = EDT (UTC-4) → 09:30 ET = 13:30Z ; 16:00 ET = 20:00Z
        assertEquals(Instant.parse("2025-06-17T13:30:00Z"), day.openInstantUtc(),  "Apertura regolare in UTC (EDT)");
        assertEquals(Instant.parse("2025-06-17T20:00:00Z"), day.closeInstantUtc(), "Chiusura regolare in UTC (EDT)");

        // Sessione estesa: 04:00 EDT = 08:00Z ; 20:00 EDT = 00:00Z del giorno successivo
        assertEquals(Instant.parse("2025-06-17T08:00:00Z"), day.sessionOpenInstantUtc(),  "Apertura estesa in UTC (EDT)");
        assertEquals(Instant.parse("2025-06-18T00:00:00Z"), day.sessionCloseInstantUtc(), "Chiusura estesa in UTC (EDT)");
    }

    @Test
    @DisplayName("Valori HHmm non validi → metodi utility restituiscono null")
    void invalidHhmm_areNull() {
        String json = """
        {
          "date": "2025-12-17",
          "open": "09:30",
          "close": "16:00",
          "session_open": "2460",
          "session_close": "-100",
          "settlement_date": "2025-12-18"
        }
        """;

        AlpacaMarketCalendarDayResponse day =
                JsonCodec.fromJson(json, AlpacaMarketCalendarDayResponse.class);

        assertNull(day.sessionOpenTime(),  "HHmm invalido deve produrre null");
        assertNull(day.sessionCloseTime(), "HHmm invalido deve produrre null");
        assertNull(day.sessionOpenAtZone(),  "Con HHmm invalido → null");
        assertNull(day.sessionCloseAtZone(), "Con HHmm invalido → null");
        assertNull(day.sessionOpenInstantUtc(),  "Con HHmm invalido → null");
        assertNull(day.sessionCloseInstantUtc(), "Con HHmm invalido → null");
    }
}
