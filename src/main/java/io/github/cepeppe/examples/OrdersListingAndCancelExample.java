package io.github.cepeppe.examples;

import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.data.response.AlpacaDeleteAllOrdersResponse;
import io.github.cepeppe.rest.data.response.AlpacaOrderResponse;
import io.github.cepeppe.rest.trading.v2.AlpacaOrdersRestService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.github.cepeppe.Constants.ALPACA.DEFAULT_SINGLE_ACC_ID_RATE_LIMIT;
import static io.github.cepeppe.examples.util.ExampleSupport.*;

/**
 * OrdersListingAndCancelExample
 *
 * Focus:
 *  - list orders (sync + async)
 *  - get by bogus id / client id (404→null expectation)
 *  - cancel by bogus id
 *  - cancel ALL (idempotent, use carefully)
 *
 * This example performs NO live create/replace. Safe to run anytime.
 */
public final class OrdersListingAndCancelExample {
    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(OrdersListingAndCancelExample.class);

    public static void main(String[] args) {
        var svc = new AlpacaOrdersRestService(AlpacaRestBaseEndpoints.API_V2_PAPER_TRADING);
        final String SYMBOL = readStr("mm.playground.symbol.crypto", "MM_PLAYGROUND_SYMBOL_CRYPTO", "BTCUSD");

        LOG.info("=== OrdersListingAndCancelExample START === symbol={}", SYMBOL);

        // LIST: open desc (sync)
        var openDesc = svc.getOrders(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, "open", 50, null, null, "desc", true, null, null);
        LOG.info("LIST open (sync): size={} | sample={}", openDesc.size(), preview(openDesc));

        // LIST: all asc async filtered by symbol
        var allAscAsync = svc.getAsyncOrders(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, "all", 25,
                        Instant.now().minusSeconds(7 * 24 * 3600), null, "asc", false, null, List.of(SYMBOL))
                .orTimeout(30, TimeUnit.SECONDS).join();
        LOG.info("LIST all (async) asc symbols=[{}]: size={} | sample={}", SYMBOL, allAscAsync.size(), preview(allAscAsync));

        // GET bogus id/client id → expect null
        String bogusOrderId = UUID.randomUUID().toString();
        var bogus = svc.getOrder(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, bogusOrderId);
        LOG.info("GET by ID (bogus) -> {}", bogus == null ? "null (expected)" : "NOT null");
        var bogusAsync = svc.getAsyncOrder(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, bogusOrderId).orTimeout(30, TimeUnit.SECONDS).join();
        LOG.info("GET-async by ID (bogus) -> {}", bogusAsync == null ? "null (expected)" : "NOT null");

        String bogusClientId = "PLAY-BOGUS-" + System.currentTimeMillis();
        var bogusByClient = svc.getOrderByClientOrderId(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, bogusClientId);
        LOG.info("GET by client_order_id (bogus) -> {}", bogusByClient == null ? "null (expected)" : "NOT null");
        var bogusByClientAsync = svc.getAsyncOrderByClientOrderId(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, bogusClientId)
                .orTimeout(30, TimeUnit.SECONDS).join();
        LOG.info("GET-async by client_order_id (bogus) -> {}", bogusByClientAsync == null ? "null (expected)" : "NOT null");

        // CANCEL bogus id
        boolean cancelledBogus = svc.cancelOrder(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, bogusOrderId);
        LOG.info("CANCEL bogus id -> {}", cancelledBogus);

        boolean cancelledBogusAsync = svc.cancelOrderAsync(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, bogusOrderId)
                .orTimeout(30, TimeUnit.SECONDS).join();
        LOG.info("CANCEL-async bogus id -> {}", cancelledBogusAsync);

        // CANCEL ALL (idempotent) — use with caution in live environments
        List<AlpacaDeleteAllOrdersResponse> delAll = svc.cancelAllOrders(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT);
        LOG.info("CANCEL ALL (sync) count={} | sample={}", delAll.size(), preview(delAll));

        List<AlpacaDeleteAllOrdersResponse> delAllAsync = svc.cancelAllOrdersAsync(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT)
                .orTimeout(30, TimeUnit.SECONDS).join();
        LOG.info("CANCEL ALL (async) count={} | sample={}", delAllAsync.size(), preview(delAllAsync));

        LOG.info("=== OrdersListingAndCancelExample END ===");
    }
}
