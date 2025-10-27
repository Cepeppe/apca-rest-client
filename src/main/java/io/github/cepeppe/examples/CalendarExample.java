package io.github.cepeppe.examples;

import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.data.response.AlpacaMarketCalendarDayResponse;
import io.github.cepeppe.rest.trading.v2.AlpacaCalendarRestService;
import io.github.cepeppe.utils.HttpUtils;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.github.cepeppe.Constants.ALPACA.DEFAULT_SINGLE_ACC_ID_RATE_LIMIT;
import static io.github.cepeppe.examples.util.ExampleSupport.*;

/**
 * CalendarExample
 *
 * Focus: GET /v2/calendar (range & single day).
 * Demonstrates DTO utilities (open/close in ET & UTC) and expected validation warnings.
 */
public final class CalendarExample {
    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(CalendarExample.class);

    private static final int PREVIEW_LEN = 256;

    public static void main(String[] args) {
        AlpacaRestBaseEndpoints endpoint = AlpacaRestBaseEndpoints.API_V2_PAPER_TRADING;
        boolean retries = true;

        var calendarService = new AlpacaCalendarRestService(endpoint);

        // Range: today → +7 days (US_EASTERN)
        LocalDate start = LocalDate.now(AlpacaMarketCalendarDayResponse.US_EASTERN);
        LocalDate end   = start.plusDays(7);

        var days = calendarService.getCalendarDays(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, start, end);
        LOG.info("[SYNC] count={} range=[{}, {}]", days.size(), start, end);
        LOG.info("[SYNC] Preview: {}", HttpUtils.safePreview(safePreviewDays(days, 3), PREVIEW_LEN));

        if (!days.isEmpty()) {
            AlpacaMarketCalendarDayResponse d0 = days.get(0);
            ZonedDateTime openET  = d0.openAtZone();
            ZonedDateTime closeET = d0.closeAtZone();
            LOG.info("[SYNC] Day#0: openET={} closeET={} openUTC={} closeUTC={}",
                    openET, closeET, d0.openInstantUtc(), d0.closeInstantUtc());
        }

        // Single day async: tomorrow
        LocalDate day = start.plusDays(1);
        CompletableFuture<List<AlpacaMarketCalendarDayResponse>> fut =
                calendarService.getAsyncCalendarDays(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, day, day);
        var asyncDays = fut.join();
        LOG.info("[ASYNC-DTO] count={} day={}", asyncDays.size(), day);
        LOG.info("[ASYNC-DTO] Preview: {}", HttpUtils.safePreview(safePreviewDays(asyncDays, 1), PREVIEW_LEN));

        // Expected validation example: end < start
        try {
            calendarService.getCalendarDays(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, start, start.minusDays(1));
            LOG.warn("[EXPECTED-WARN] no exception with end<start?");
        } catch (IllegalArgumentException expected) {
            LOG.warn("[EXPECTED] {}", briefExpected(expected));
        }
    }

    private static String safePreviewDays(List<AlpacaMarketCalendarDayResponse> days, int maxItems) {
        if (days == null || days.isEmpty() || maxItems <= 0) return "[]";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(maxItems, days.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(", ");
            sb.append(days.get(i).toString());
        }
        return sb.toString();
    }

    private static String briefExpected(Throwable t) {
        String type = t.getClass().getSimpleName();
        String msg  = HttpUtils.safePreview(String.valueOf(t.getMessage()), 160);
        return "EXPECTED " + type + ": " + msg;
    }
}
