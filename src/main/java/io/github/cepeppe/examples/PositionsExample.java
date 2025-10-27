package io.github.cepeppe.examples;
import io.github.cepeppe.exception.ApcaRestClientException;
import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.data.response.AlpacaClosePositionResponse;
import io.github.cepeppe.rest.data.response.AlpacaOrderResponse;
import io.github.cepeppe.rest.data.response.AlpacaPositionResponse;
import io.github.cepeppe.rest.trading.v2.AlpacaPositionsRestService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static io.github.cepeppe.Constants.ALPACA.DEFAULT_SINGLE_ACC_ID_RATE_LIMIT;
import static io.github.cepeppe.examples.util.ExampleSupport.*;

/**
 * PositionsExample
 *
 * Focus:
 *  - /v2/positions (list) — sync/async
 *  - /v2/positions/{idOrSymbol} (detail) — sync/async, handles 404→null
 *  - DANGER ZONE (commented by default): close all / close by qty / close by percentage
 */
public final class PositionsExample {
    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(PositionsExample.class);

    public static void main(String[] args) {
        AlpacaRestBaseEndpoints endpoint = AlpacaRestBaseEndpoints.API_V2_PAPER_TRADING;
        boolean retries = true;

        var svc = new AlpacaPositionsRestService(endpoint);

        demoListSync(svc, retries);
        demoListAsync(svc, retries);

        demoDetailSync(svc, retries, "AAPL"); // if no open position: null
        demoDetailAsync(svc, retries, "AAPL");

        // -------- DANGER ZONE (uncomment to execute) --------
        // demoCloseAllSync(svc, retries, /*cancelOrders*/ true);
        // demoCloseAllAsync(svc, retries, /*cancelOrders*/ true);
        // demoCloseByQtySync(svc, retries, "AAPL", new BigDecimal("1"));
        // demoCloseByQtyAsync(svc, retries, "AAPL", new BigDecimal("1"));
        // demoCloseByPctSync(svc, retries, "AAPL", new BigDecimal("100"));
        // demoCloseByPctAsync(svc, retries, "AAPL", new BigDecimal("100"));
    }

    /* ===== list ===== */

    private static void demoListSync(AlpacaPositionsRestService svc, boolean retries) {
        LOG.info("[SYNC] getOpenPositions()");
        Instant t0 = Instant.now();
        try {
            List<AlpacaPositionResponse> list = svc.getOpenPositions(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT);
            LOG.info("[SYNC] OK in {} ms (count={})", ms(t0, Instant.now()), list.size());
            if (!list.isEmpty()) {
                list.stream()
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing((AlpacaPositionResponse p) ->
                                Optional.ofNullable(p.marketValue()).orElse(BigDecimal.ZERO)).reversed())
                        .limit(3)
                        .forEach(p -> LOG.info("[SYNC] TOP MV → symbol={} qty={} mv={} uPL={} side={}",
                                p.symbol(), p.qty(), p.marketValue(), p.unrealizedPl(), p.side()));
                LOG.info("[SYNC] Preview:\n{}", safePreview(pretty(list), 300));
            }
        } catch (ApcaRestClientException e) {
            LOG.error("[SYNC] ERROR in {} ms -> {}", ms(t0, Instant.now()), e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("[SYNC] UNEXPECTED in {} ms", ms(t0, Instant.now()), e);
        }
    }

    private static void demoListAsync(AlpacaPositionsRestService svc, boolean retries) {
        LOG.info("[ASYNC-DTO] getAsyncOpenPositions()");
        Instant t0 = Instant.now();
        CompletableFuture<List<AlpacaPositionResponse>> fut =
                svc.getAsyncOpenPosition(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT);

        fut.thenAccept(list -> {
                    LOG.info("[ASYNC-DTO] OK in {} ms (count={})", ms(t0, Instant.now()), list.size());
                    if (!list.isEmpty()) {
                        AlpacaPositionResponse first = list.get(0);
                        LOG.info("[ASYNC-DTO] First → symbol={} side={} qty={} mv={} uPLpc={}",
                                first.symbol(), first.side(), first.qty(), first.marketValue(), first.unrealizedPlpc());
                        LOG.info("[ASYNC-DTO] Preview:\n{}", safePreview(pretty(list), 300));
                    }
                })
                .exceptionally(ex -> { LOG.error("[ASYNC-DTO] ERROR in {} ms -> {}", ms(t0, Instant.now()), rootMessage(ex), ex); return null; });

        blockForDemo(fut, 15);
    }

    /* ===== detail ===== */

    private static void demoDetailSync(AlpacaPositionsRestService svc, boolean retries, String idOrSymbol) {
        LOG.info("[SYNC] getOpenPosition({})", idOrSymbol);
        Instant t0 = Instant.now();
        try {
            AlpacaPositionResponse dto = svc.getOpenPosition(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, idOrSymbol);
            if (dto == null) {
                LOG.warn("[SYNC] POSITION detail: no open position for '{}'. (mapped from HTTP 404)", idOrSymbol);
                return;
            }
            LOG.info("[SYNC] OK in {} ms\n{}", ms(t0, Instant.now()), pretty(dto));
        } catch (ApcaRestClientException e) {
            LOG.error("[SYNC] ERROR in {} ms -> {}", ms(t0, Instant.now()), e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("[SYNC] UNEXPECTED in {} ms", ms(t0, Instant.now()), e);
        }
    }

    private static void demoDetailAsync(AlpacaPositionsRestService svc, boolean retries, String idOrSymbol) {
        LOG.info("[ASYNC-DTO] getAsyncOpenPosition({})", idOrSymbol);
        Instant t0 = Instant.now();
        CompletableFuture<AlpacaPositionResponse> fut =
                svc.getAsyncOpenPosition(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, idOrSymbol);

        fut.thenAccept(dto -> {
                    if (dto == null) {
                        LOG.warn("[ASYNC-DTO] POSITION detail: no open position for '{}' (404 mapped to null)", idOrSymbol);
                        return;
                    }
                    LOG.info("[ASYNC-DTO] OK in {} ms\n{}", ms(t0, Instant.now()), pretty(dto));
                })
                .exceptionally(ex -> { LOG.error("[ASYNC-DTO] ERROR in {} ms -> {}", ms(t0, Instant.now()), rootMessage(ex), ex); return null; });

        blockForDemo(fut, 15);
    }

    /* ===== DANGER ZONE: close operations (commented in main) ===== */

    private static void demoCloseAllSync(AlpacaPositionsRestService svc, boolean retries, boolean cancelOrders) {
        LOG.warn("[DANGER][SYNC] closeAllOpenPositions(cancel_orders={})", cancelOrders);
        Instant t0 = Instant.now();
        try {
            List<AlpacaClosePositionResponse> out = svc.closeAllOpenPositions(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, cancelOrders);
            LOG.info("[SYNC] CLOSE-ALL OK in {} ms (items={})", ms(t0, Instant.now()), out.size());
            LOG.info("[SYNC] Preview:\n{}", safePreview(pretty(out), 300));
        } catch (ApcaRestClientException e) {
            LOG.error("[SYNC] CLOSE-ALL ERROR in {} ms -> {}", ms(t0, Instant.now()), e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("[SYNC] CLOSE-ALL UNEXPECTED in {} ms", ms(t0, Instant.now()), e);
        }
    }

    private static void demoCloseAllAsync(AlpacaPositionsRestService svc, boolean retries, boolean cancelOrders) {
        LOG.warn("[DANGER][ASYNC-DTO] closeAllOpenPositionsAsync(cancel_orders={})", cancelOrders);
        Instant t0 = Instant.now();
        var fut = svc.closeAllOpenPositionsAsync(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, cancelOrders);

        fut.thenAccept(out -> {
                    LOG.info("[ASYNC-DTO] CLOSE-ALL OK in {} ms (items={})", ms(t0, Instant.now()), out.size());
                    LOG.info("[ASYNC-DTO] Preview:\n{}", safePreview(pretty(out), 300));
                })
                .exceptionally(ex -> { LOG.error("[ASYNC-DTO] CLOSE-ALL ERROR in {} ms -> {}", ms(t0, Instant.now()), rootMessage(ex), ex); return null; });

        blockForDemo(fut, 20);
    }

    private static void demoCloseByQtySync(AlpacaPositionsRestService svc, boolean retries, String symbolOrId, BigDecimal qty) {
        LOG.warn("[DANGER][SYNC] closePositionByQty(symbol={}, qty={})", symbolOrId, qty);
        Instant t0 = Instant.now();
        try {
            AlpacaOrderResponse order = svc.closePositionByQty(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, symbolOrId, qty);
            LOG.info("[SYNC] CLOSE-POS(QTY) OK in {} ms | id={} status={} side={} filled={}/{}",
                    ms(t0, Instant.now()), order.id(), order.status(), order.side(), order.filledQty(), order.qty());
        } catch (ApcaRestClientException e) {
            LOG.error("[SYNC] CLOSE-POS(QTY) ERROR in {} ms -> {}", ms(t0, Instant.now()), e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("[SYNC] CLOSE-POS(QTY) UNEXPECTED in {} ms", ms(t0, Instant.now()), e);
        }
    }

    private static void demoCloseByQtyAsync(AlpacaPositionsRestService svc, boolean retries, String symbolOrId, BigDecimal qty) {
        LOG.warn("[DANGER][ASYNC-DTO] closePositionByQtyAsync(symbol={}, qty={})", symbolOrId, qty);
        Instant t0 = Instant.now();
        var fut = svc.closePositionByQtyAsync(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, symbolOrId, qty);

        fut.thenAccept(order -> LOG.info("[ASYNC-DTO] CLOSE-POS(QTY) OK in {} ms | id={} status={} side={} filled={}/{}",
                        ms(t0, Instant.now()), order.id(), order.status(), order.side(), order.filledQty(), order.qty()))
                .exceptionally(ex -> { LOG.error("[ASYNC-DTO] CLOSE-POS(QTY) ERROR in {} ms -> {}", ms(t0, Instant.now()), rootMessage(ex), ex); return null; });

        blockForDemo(fut, 20);
    }

    private static void demoCloseByPctSync(AlpacaPositionsRestService svc, boolean retries, String symbolOrId, BigDecimal percentage) {
        LOG.warn("[DANGER][SYNC] closePositionByPercentage(symbol={}, percentage={})", symbolOrId, percentage);
        Instant t0 = Instant.now();
        try {
            AlpacaOrderResponse order = svc.closePositionByPercentage(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, symbolOrId, percentage);
            LOG.info("[SYNC] CLOSE-POS(%) OK in {} ms | id={} status={} side={} filled={}/{}",
                    ms(t0, Instant.now()), order.id(), order.status(), order.side(), order.filledQty(), order.qty());
        } catch (ApcaRestClientException e) {
            LOG.error("[SYNC] CLOSE-POS(%) ERROR in {} ms -> {}", ms(t0, Instant.now()), e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("[SYNC] CLOSE-POS(%) UNEXPECTED in {} ms", ms(t0, Instant.now()), e);
        }
    }

    private static void demoCloseByPctAsync(AlpacaPositionsRestService svc, boolean retries, String symbolOrId, BigDecimal percentage) {
        LOG.warn("[DANGER][ASYNC-DTO] closePositionByPercentageAsync(symbol={}, percentage={})", symbolOrId, percentage);
        Instant t0 = Instant.now();
        var fut = svc.closePositionByPercentageAsync(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, symbolOrId, percentage);

        fut.thenAccept(order -> LOG.info("[ASYNC-DTO] CLOSE-POS(%) OK in {} ms | id={} status={} side={} filled={}/{}",
                        ms(t0, Instant.now()), order.id(), order.status(), order.side(), order.filledQty(), order.qty()))
                .exceptionally(ex -> { LOG.error("[ASYNC-DTO] CLOSE-POS(%) ERROR in {} ms -> {}", ms(t0, Instant.now()), rootMessage(ex), ex); return null; });

        blockForDemo(fut, 20);
    }
}
