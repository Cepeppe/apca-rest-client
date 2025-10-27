package io.github.cepeppe.examples;

import io.github.cepeppe.Env;
import io.github.cepeppe.exception.ApcaRestClientException;
import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.data.response.AlpacaAccountDetailsResponse;
import io.github.cepeppe.rest.data.response.AlpacaMarketClockInfoResponse;

import io.github.cepeppe.rest.trading.v2.AlpacaClockRestService;
import io.github.cepeppe.rest.trading.v2.account.AlpacaAccountRestService;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static io.github.cepeppe.Constants.ALPACA.DEFAULT_SINGLE_ACC_ID_RATE_LIMIT;
import static io.github.cepeppe.examples.util.ExampleSupport.*;

/**
 * CoreClockAccountExample
 *
 * Focus: basic Trading v2 resources:
 *   - GET /v2/clock (sync + async)
 *   - GET /v2/account (sync + async)
 * Includes small raw preflight tips and 401 hints.
 */
public final class CoreClockAccountExample {
    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(CoreClockAccountExample.class);

    public static void main(String[] args) {
        AlpacaRestBaseEndpoints endpoint = AlpacaRestBaseEndpoints.API_V2_PAPER_TRADING;
        boolean enableRetries = true;

        preflight(endpoint);
        var clock = new AlpacaClockRestService(endpoint);
        var account = new AlpacaAccountRestService(endpoint);

        demoSyncClock(clock, enableRetries);
        demoAsyncClock(clock, enableRetries);

        demoSyncAccount(account, enableRetries);
        demoAsyncAccount(account, enableRetries);
    }

    private static void preflight(AlpacaRestBaseEndpoints endpoint) {
        String key = trimToNull(Env.get("APCA_API_KEY_ID"));
        String secret = trimToNull(Env.get("APCA_API_SECRET_KEY"));
        LOG.info("[Preflight] Headers expected = [APCA-API-KEY-ID, APCA-API-SECRET-KEY]");
        LOG.info("[Preflight] Credentials present: key={}, secret={}", mask(key), mask(secret));
        LOG.info("[Preflight] Base URL = {}", effectiveBaseUrl(endpoint));
    }

    /* ===== Clock ===== */

    private static void demoSyncClock(AlpacaClockRestService svc, boolean retries) {
        LOG.info("[SYNC] getMarketClockInfo()");
        Instant t0 = Instant.now();
        try {
            AlpacaMarketClockInfoResponse dto = svc.getMarketClockInfo(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT);
            LOG.info("[SYNC] SUCCESS in {} ms\n{}", ms(t0, Instant.now()), pretty(dto));
        } catch (ApcaRestClientException e) {
            LOG.error("[SYNC] ERROR in {} ms -> {}", ms(t0, Instant.now()), e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("[SYNC] UNEXPECTED in {} ms", ms(t0, Instant.now()), e);
        }
    }

    private static void demoAsyncClock(AlpacaClockRestService svc, boolean retries) {
        LOG.info("[ASYNC-DTO] getAsyncMarketClockInfo()");
        Instant t0 = Instant.now();
        CompletableFuture<AlpacaMarketClockInfoResponse> fut = svc.getAsyncMarketClockInfo(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT);
        fut.thenAccept(dto -> LOG.info("[ASYNC-DTO] SUCCESS in {} ms\n{}", ms(t0, Instant.now()), pretty(dto)))
                .exceptionally(ex -> { LOG.error("[ASYNC-DTO] ERROR in {} ms -> {}", ms(t0, Instant.now()), rootMessage(ex), ex); return null; });
        blockForDemo(fut, 10);
    }

    /* ===== Account ===== */

    private static void demoSyncAccount(AlpacaAccountRestService svc, boolean retries) {
        LOG.info("[SYNC] getAccountDetails()");
        Instant t0 = Instant.now();
        try {
            AlpacaAccountDetailsResponse dto = svc.getAccountDetails(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT);
            LOG.info("[SYNC] SUCCESS in {} ms\n{}", ms(t0, Instant.now()), pretty(dto));
            LOG.info("[SYNC] Summary: currency={} cash={} equity={} buying_power={} pdt={}",
                    dto.currency(), dto.cash(), dto.equity(), dto.buyingPower(), dto.patternDayTrader());
        } catch (ApcaRestClientException e) {
            LOG.error("[SYNC] ERROR in {} ms -> {}", ms(t0, Instant.now()), e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("[SYNC] UNEXPECTED in {} ms", ms(t0, Instant.now()), e);
        }
    }

    private static void demoAsyncAccount(AlpacaAccountRestService svc, boolean retries) {
        LOG.info("[ASYNC-DTO] getAsyncAccountDetails()");
        Instant t0 = Instant.now();
        CompletableFuture<AlpacaAccountDetailsResponse> fut = svc.getAsyncAccountDetails(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT);
        fut.thenAccept(dto -> LOG.info("[ASYNC-DTO] SUCCESS in {} ms\n{}", ms(t0, Instant.now()), pretty(dto)))
                .exceptionally(ex -> { LOG.error("[ASYNC-DTO] ERROR in {} ms -> {}", ms(t0, Instant.now()), rootMessage(ex), ex); return null; });
        blockForDemo(fut, 10);
    }
}
