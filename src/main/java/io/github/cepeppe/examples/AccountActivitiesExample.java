package io.github.cepeppe.examples;

import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.data.response.AlpacaAccountActivityResponse;
import io.github.cepeppe.rest.data.response.AlpacaAccountNonTradeActivityResponse;
import io.github.cepeppe.rest.data.response.AlpacaAccountTradeActivityResponse;
import io.github.cepeppe.rest.trading.v2.account.AlpacaAccountActivitiesRestService;
import io.github.cepeppe.rest.trading.v2.account.AlpacaAccountActivitiesRestService.AccountActivitiesByTypeQuery;
import io.github.cepeppe.rest.trading.v2.account.AlpacaAccountActivitiesRestService.AccountActivitiesQuery;
import io.github.cepeppe.utils.HttpUtils;

import java.time.LocalDate;
import java.util.*;

import static io.github.cepeppe.Constants.ALPACA.DEFAULT_SINGLE_ACC_ID_RATE_LIMIT;

/**
 * AccountActivitiesExample
 *
 * Focus:
 *  - GET /v2/account/activities (base + date + multi-type)
 *  - GET /v2/account/activities/{activity_type} (FILL, CFEE)
 *  - client-side validation hints
 */
public final class AccountActivitiesExample {
    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(AccountActivitiesExample.class);

    public static void main(String[] args) {
        AlpacaRestBaseEndpoints endpoint = AlpacaRestBaseEndpoints.API_V2_PAPER_TRADING;
        boolean retries = true;

        var svc = new AlpacaAccountActivitiesRestService(endpoint);

        // Stress (page_size clamped to 100 when date is absent)
        AccountActivitiesQuery q0 = new AccountActivitiesQuery.Builder()
                .pageSize(500).direction("desc").build();
        var a0 = svc.getAccountActivities(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, q0);
        LOG.info("[SYNC] activities count={} preview={}", a0.size(), safePreviewActivities(a0, 3));
        LOG.info("[SYNC] summary={}", summarizeActivities(a0, 8));

        // Date = single day
        LocalDate date = LocalDate.now();
        AccountActivitiesQuery q1 = new AccountActivitiesQuery.Builder().date(date).direction("desc").build();
        var a1 = svc.getAccountActivities(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, q1);
        LOG.info("[SYNC] byDate count={} date={} summary={}", a1.size(), date, summarizeActivities(a1, 6));

        // Multi-type async
        AccountActivitiesQuery q2 = new AccountActivitiesQuery.Builder()
                .activityTypes(List.of("FILL","CFEE","DIV")).pageSize(50).direction("desc").build();
        var a2 = svc.getAsyncAccountActivities(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, q2).join();
        LOG.info("[ASYNC-DTO] multi-type count={} preview={}", a2.size(), safePreviewActivities(a2, 5));
        LOG.info("[ASYNC-DTO] summary={}", summarizeActivities(a2, 10));

        // By type: FILL (sync)
        AccountActivitiesByTypeQuery btFill = new AccountActivitiesByTypeQuery.Builder().direction("desc").pageSize(50).build();
        var bt0 = svc.getAccountActivitiesByType(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, "FILL", btFill);
        LOG.info("[SYNC] byType=FILL count={} tradeCount={}", bt0.size(), countTrades(bt0));

        // By type: CFEE (async)
        AccountActivitiesByTypeQuery btCfee = new AccountActivitiesByTypeQuery.Builder().direction("desc").pageSize(50).build();
        var bt1 = svc.getAsyncAccountActivitiesByType(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, "CFEE", btCfee).join();
        LOG.info("[ASYNC-DTO] byType=CFEE count={} nonTradeCount={}", bt1.size(), countNonTrades(bt1));

        // Expected validations
        try {
            AccountActivitiesQuery bad = new AccountActivitiesQuery.Builder()
                    .date(LocalDate.now()).after("2025-01-01").build();
            svc.getAccountActivities(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, bad);
            LOG.warn("[EXPECTED-WARN] no exception with date+after?");
        } catch (IllegalArgumentException expected) {
            LOG.warn("[EXPECTED] {}", HttpUtils.safePreview(expected.getMessage(), 160));
        }
        try {
            AccountActivitiesByTypeQuery badBt = new AccountActivitiesByTypeQuery.Builder()
                    .category("crypto").build();
            svc.getAccountActivitiesByType(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, "CFEE", badBt);
            LOG.warn("[EXPECTED-WARN] no exception with category in byType?");
        } catch (IllegalArgumentException expected) {
            LOG.warn("[EXPECTED] {}", HttpUtils.safePreview(expected.getMessage(), 160));
        }
    }

    /* Helpers */
    private static String safePreviewActivities(List<AlpacaAccountActivityResponse> acts, int maxItems) {
        if (acts == null || acts.isEmpty() || maxItems <= 0) return "[]";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(maxItems, acts.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(" || ");
            sb.append(acts.get(i).toString());
        }
        return HttpUtils.safePreview(sb.toString(), 256);
    }

    private static String summarizeActivities(List<AlpacaAccountActivityResponse> acts, int topN) {
        if (acts == null || acts.isEmpty()) return "{empty}";
        int trades = 0, nonTrades = 0;
        Map<String, Integer> byType = new HashMap<>();
        for (AlpacaAccountActivityResponse a : acts) {
            String at;
            if (a instanceof AlpacaAccountTradeActivityResponse t) {
                trades++; at = t.activityType();
            } else if (a instanceof AlpacaAccountNonTradeActivityResponse n) {
                nonTrades++; at = n.activityType();
            } else at = "UNKNOWN";
            byType.merge(at, 1, Integer::sum);
        }
        List<Map.Entry<String,Integer>> list = new ArrayList<>(byType.entrySet());
        list.sort((e1,e2) -> Integer.compare(e2.getValue(), e1.getValue()));
        StringBuilder sb = new StringBuilder("{trades=").append(trades).append(", nonTrades=").append(nonTrades).append(", types=[");
        for (int i=0; i<Math.min(topN, list.size()); i++) {
            if (i>0) sb.append(", "); sb.append(list.get(i).getKey()).append(":").append(list.get(i).getValue());
        }
        if (list.size()>topN) sb.append(", ...");
        sb.append("]}");
        return sb.toString();
    }

    private static long countTrades(List<AlpacaAccountActivityResponse> acts) {
        if (acts == null) return 0;
        return acts.stream().filter(a -> a instanceof AlpacaAccountTradeActivityResponse).count();
    }
    private static long countNonTrades(List<AlpacaAccountActivityResponse> acts) {
        if (acts == null) return 0;
        return acts.stream().filter(a -> a instanceof AlpacaAccountNonTradeActivityResponse).count();
    }
}
