package io.github.cepeppe.examples;

import io.github.cepeppe.exception.ApcaRestClientException;
import io.github.cepeppe.json.JsonCodec;
import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.data.request.AlpacaCreateOrderRequest;
import io.github.cepeppe.rest.data.request.AlpacaReplaceOrderRequest;
import io.github.cepeppe.rest.data.response.AlpacaDeleteAllOrdersResponse;
import io.github.cepeppe.rest.data.response.AlpacaOrderResponse;
import io.github.cepeppe.rest.trading.v2.AlpacaOrdersRestService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.github.cepeppe.Constants.ALPACA.DEFAULT_SINGLE_ACC_ID_RATE_LIMIT;
import static io.github.cepeppe.examples.util.ExampleSupport.*;

/**
 * OrdersLifecycleExample
 *
 * Focus: CREATE → GET → REPLACE → CANCEL by id (paper environment).
 * SAFE by default (DRY RUN). Enable LIVE with:
 *  -Dmm.playground.liveOrders=true   or   MM_PLAYGROUND_LIVE_ORDERS=true
 */
public final class OrdersLifecycleExample {
    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(OrdersLifecycleExample.class);

    public static void main(String[] args) {
        var svc = new AlpacaOrdersRestService(AlpacaRestBaseEndpoints.API_V2_PAPER_TRADING);

        final boolean LIVE = readFlag("mm.playground.liveOrders", "MM_PLAYGROUND_LIVE_ORDERS", false);
        final String SYMBOL = readStr("mm.playground.symbol.crypto", "MM_PLAYGROUND_SYMBOL_CRYPTO", "BTCUSD");
        final BigDecimal NOTIONAL = readDecimal("mm.playground.notional", "MM_PLAYGROUND_NOTIONAL", new BigDecimal("1.00"));
        final BigDecimal LIMIT_PX = readDecimal("mm.playground.limitPrice", "MM_PLAYGROUND_LIMIT_PRICE", new BigDecimal("100.00"));

        LOG.info("=== OrdersLifecycleExample START === LIVE={} SYMBOL={} NOTIONAL={} LIMIT_PX={}", LIVE, SYMBOL, NOTIONAL, LIMIT_PX);

        if (!LIVE) {
            LOG.warn("LIVE mode OFF → skip CREATE/REPLACE/CANCEL. Enable with -Dmm.playground.liveOrders=true or env MM_PLAYGROUND_LIVE_ORDERS=true");
            return;
        }

        String createdOrderId;
        String createdClientOrderId;
        try {
            // CREATE (limit far away so it stays open)
            AlpacaCreateOrderRequest createReq = JsonCodec.fromJson("""
                {
                  "symbol": "%s",
                  "side": "buy",
                  "type": "limit",
                  "time_in_force": "gtc",
                  "notional": %s,
                  "limit_price": %s,
                  "client_order_id": "PLAY-%d"
                }
                """.formatted(SYMBOL, NOTIONAL.toPlainString(), LIMIT_PX.toPlainString(), System.currentTimeMillis()),
                    AlpacaCreateOrderRequest.class
            );
            AlpacaOrderResponse created = svc.createOrder(DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, createReq);
            createdOrderId = created.id();
            createdClientOrderId = created.clientOrderId();
            LOG.info("CREATE OK | id={} | client_id={} | status={} | type={} | tif={}",
                    createdOrderId, createdClientOrderId, created.status(), created.type(), created.timeInForce());

            // GET (by id & by client id)
            LOG.info("GET by id & client_order_id");
            AlpacaOrderResponse byId = svc.getOrder(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, createdOrderId);
            AlpacaOrderResponse byClient = svc.getOrderByClientOrderId(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, createdClientOrderId);
            LOG.info("byId.status={}  byClient.status={}", byId.status(), byClient.status());

            // REPLACE (raise limit a bit)
            BigDecimal newLimit = LIMIT_PX.add(new BigDecimal("10.00"));
            AlpacaReplaceOrderRequest replaceReq = JsonCodec.fromJson("""
                { "limit_price": %s }
                """.formatted(newLimit.toPlainString()), AlpacaReplaceOrderRequest.class);
            AlpacaOrderResponse replaced = svc.replaceOrder(DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, createdOrderId, replaceReq);
            LOG.info("REPLACE OK | old_id={} | new_id={} | new_limit={}", createdOrderId, replaced.id(), newLimit);

            // CANCEL (by new id)
            boolean cancelOk = svc.cancelOrder(true, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, replaced.id());
            LOG.info("CANCEL by ID {} → {}", replaced.id(), cancelOk);

            // REPLACE on canceled (should fail)
            try {
                svc.replaceOrder(DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, replaced.id(), replaceReq);
                throw new AssertionError("Expected failure when replacing a canceled order.");
            } catch (Exception expected) {
                LOG.info("REPLACE on canceled → expected exception: {}", rootMessage(expected));
            }

        } catch (Exception e) {
            LOG.error("Lifecycle failed: {}", e.getMessage(), e);
        }

        LOG.info("=== OrdersLifecycleExample END ===");
    }
}
