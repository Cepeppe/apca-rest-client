package io.github.cepeppe.examples;

import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.data.request.AlpacaCreateWatchlistRequest;
import io.github.cepeppe.rest.data.response.AlpacaWatchlistResponse;
import io.github.cepeppe.rest.trading.v2.AlpacaWatchlistsRestService;
import io.github.cepeppe.utils.HttpUtils;

import java.util.List;

import static io.github.cepeppe.Constants.ALPACA.DEFAULT_SINGLE_ACC_ID_RATE_LIMIT;
import static io.github.cepeppe.examples.util.ExampleSupport.*;

/**
 * WatchlistsExample
 *
 * Focus: /v2/watchlists end-to-end:
 *  - List, Create, Get by ID/Name (404→null), Delete symbol, Delete by ID/Name.
 * Note: POST is non-idempotent; the service disables retries internally.
 */
public final class WatchlistsExample {
    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(WatchlistsExample.class);

    public static void main(String[] args) {
        AlpacaRestBaseEndpoints endpoint = AlpacaRestBaseEndpoints.API_V2_PAPER_TRADING;
        boolean retries = true;

        var svc = new AlpacaWatchlistsRestService(endpoint);

        // List
        List<AlpacaWatchlistResponse> all0 = svc.getWatchlists(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT);
        LOG.info("[SYNC] watchlists count={} preview={}", all0.size(), safePreviewWatchlists(all0, 3));

        // Create
        String wlName1 = "Play-WL-" + System.currentTimeMillis();
        AlpacaWatchlistResponse wl1 = svc.createWatchlist(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT,
                new AlpacaCreateWatchlistRequest(wlName1, List.of("AAPL","MSFT")));
        LOG.info("[SYNC] created id={} name={}", wl1.id(), wlName1);

        // Get by id
        AlpacaWatchlistResponse byId = svc.getWatchlistById(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, wl1.id());
        LOG.info("[SYNC] getById found={} assets={}", (byId!=null), (byId!=null && byId.assets()!=null ? byId.assets().size():0));

        // Get by name (async)
        AlpacaWatchlistResponse byName = svc.getAsyncWatchlistByName(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, wlName1).join();
        LOG.info("[ASYNC-DTO] getByName found={} id={}", (byName!=null), (byName!=null ? byName.id() : "-"));

        // Delete one symbol
        AlpacaWatchlistResponse wlAfter = svc.deleteSymbolFromWatchlist(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, wl1.id(), "MSFT");
        LOG.info("[SYNC] after delete symbol: remaining={}", (wlAfter.assets()!=null ? wlAfter.assets().size() : 0));

        // Delete by id
        boolean delById = svc.deleteWatchlistById(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, wl1.id());
        LOG.info("[SYNC] deleteById -> {}", delById);

        // Confirm 404 by id
        AlpacaWatchlistResponse shouldNull = svc.getWatchlistById(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, wl1.id());
        LOG.info("[SYNC] getById post-delete found={} expected=false", (shouldNull!=null));

        // By NAME flow (create & delete by name)
        String wlName2 = "Play-WL-" + (System.currentTimeMillis() + 1);
        svc.createWatchlist(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT,
                new AlpacaCreateWatchlistRequest(wlName2, List.of("TSLA")));
        boolean delByName = svc.deleteWatchlistByName(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, wlName2);
        LOG.info("[SYNC] deleteByName '{}' -> {}", wlName2, delByName);

        // Validations (expected IAE)
        try {
            svc.createWatchlist(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT,
                    new AlpacaCreateWatchlistRequest("   ", List.of("AAPL")));
            LOG.warn("[EXPECTED-WARN] no exception with blank name?");
        } catch (IllegalArgumentException expected) {
            LOG.warn("[EXPECTED] {}", HttpUtils.safePreview(expected.getMessage(), 160));
        }
    }

    private static String safePreviewWatchlists(List<AlpacaWatchlistResponse> wls, int maxItems) {
        if (wls == null || wls.isEmpty() || maxItems <= 0) return "[]";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(maxItems, wls.size());
        for (int i = 0; i < n; i++) {
            AlpacaWatchlistResponse w = wls.get(i);
            if (i > 0) sb.append(" | ");
            sb.append("[id=").append(w.id()).append(", name=").append(w.name())
                    .append(", assets=").append(w.assets()!=null ? w.assets().size() : 0).append("]");
        }
        return sb.toString();
    }
}
