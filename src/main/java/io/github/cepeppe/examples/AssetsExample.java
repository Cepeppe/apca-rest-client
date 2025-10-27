package io.github.cepeppe.examples;

import io.github.cepeppe.exception.ApcaRestClientException;
import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.data.response.AlpacaAssetResponse;
import io.github.cepeppe.rest.trading.v2.AlpacaAssetsRestService;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.github.cepeppe.Constants.ALPACA.DEFAULT_SINGLE_ACC_ID_RATE_LIMIT;
import static io.github.cepeppe.examples.util.ExampleSupport.*;

/**
 * AssetsExample
 *
 * Focus: /v2/assets (list) and /v2/assets/{idOrSymbol} (detail),
 * sync + async with basic logging and safe preview.
 */
public final class AssetsExample {
    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(AssetsExample.class);

    public static void main(String[] args) {
        AlpacaRestBaseEndpoints endpoint = AlpacaRestBaseEndpoints.API_V2_PAPER_TRADING;
        boolean retries = true;

        var svc = new AlpacaAssetsRestService(endpoint);

        demoListSync(svc, retries, "active", "us_equity", null, List.of());
        demoListAsync(svc, retries, "active", "us_equity", null, List.of());

        demoDetailSync(svc, retries, "AAPL");
        demoDetailAsync(svc, retries, "AAPL");
    }

    private static void demoListSync(AlpacaAssetsRestService svc, boolean retries,
                                     String status, String assetClass, String exchange, List<String> attributes) {
        LOG.info("[SYNC] getAssets(status={}, class={}, exch={}, attrs={})", status, assetClass, exchange, attributes);
        Instant t0 = Instant.now();
        try {
            List<AlpacaAssetResponse> dto = svc.getAssets(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, status, assetClass, exchange, attributes);
            LOG.info("[SYNC] SUCCESS in {} ms (count={})\n{}",
                    ms(t0, Instant.now()), dto.size(), safePreview(pretty(dto), 150));
        } catch (ApcaRestClientException e) {
            LOG.error("[SYNC] ERROR in {} ms -> {}", ms(t0, Instant.now()), e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("[SYNC] UNEXPECTED in {} ms", ms(t0, Instant.now()), e);
        }
    }

    private static void demoListAsync(AlpacaAssetsRestService svc, boolean retries,
                                      String status, String assetClass, String exchange, List<String> attributes) {
        LOG.info("[ASYNC-DTO] getAsyncAssets(status={}, class={}, exch={}, attrs={})",
                status, assetClass, exchange, attributes);
        Instant t0 = Instant.now();
        CompletableFuture<List<AlpacaAssetResponse>> fut =
                svc.getAsyncAssets(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, status, assetClass, exchange, attributes);

        fut.thenAccept(dto -> LOG.info("[ASYNC-DTO] SUCCESS in {} ms (count={})\n{}",
                        ms(t0, Instant.now()), dto.size(), safePreview(pretty(dto), 150)))
                .exceptionally(ex -> { LOG.error("[ASYNC-DTO] ERROR in {} ms -> {}", ms(t0, Instant.now()), rootMessage(ex), ex); return null; });

        blockForDemo(fut, 15);
    }

    private static void demoDetailSync(AlpacaAssetsRestService svc, boolean retries, String idOrSymbol) {
        LOG.info("[SYNC] getAsset(idOrSymbol={})", idOrSymbol);
        Instant t0 = Instant.now();
        try {
            AlpacaAssetResponse dto = svc.getAsset(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, idOrSymbol);
            LOG.info("[SYNC] SUCCESS in {} ms\n{}", ms(t0, Instant.now()), pretty(dto));
            LOG.info("[SYNC] Summary: symbol={} class={} exchange={} tradable={} status={}",
                    dto.symbol(), dto.assetClass(), dto.exchange(), dto.tradable(), dto.status());
        } catch (ApcaRestClientException e) {
            LOG.error("[SYNC] ERROR in {} ms -> {}", ms(t0, Instant.now()), e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("[SYNC] UNEXPECTED in {} ms", ms(t0, Instant.now()), e);
        }
    }

    private static void demoDetailAsync(AlpacaAssetsRestService svc, boolean retries, String idOrSymbol) {
        LOG.info("[ASYNC-DTO] getAsyncAsset(idOrSymbol={})", idOrSymbol);
        Instant t0 = Instant.now();
        CompletableFuture<AlpacaAssetResponse> fut =
                svc.getAsyncAsset(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT, idOrSymbol);

        fut.thenAccept(dto -> LOG.info("[ASYNC-DTO] SUCCESS in {} ms\n{}", ms(t0, Instant.now()), pretty(dto)))
                .exceptionally(ex -> { LOG.error("[ASYNC-DTO] ERROR in {} ms -> {}", ms(t0, Instant.now()), rootMessage(ex), ex); return null; });

        blockForDemo(fut, 15);
    }
}
