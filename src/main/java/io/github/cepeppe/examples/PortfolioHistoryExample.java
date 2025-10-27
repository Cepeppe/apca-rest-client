package io.github.cepeppe.examples;

import io.github.cepeppe.exception.ApcaRestClientException;
import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;
import io.github.cepeppe.rest.data.response.AlpacaPortfolioHistoryResponse;
import io.github.cepeppe.rest.trading.v2.account.AlpacaAccountRestService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.github.cepeppe.Constants.ALPACA.DEFAULT_SINGLE_ACC_ID_RATE_LIMIT;
import static io.github.cepeppe.examples.util.ExampleSupport.*;

/**
 * PortfolioHistoryExample
 *
 * Focus: GET /v2/account/portfolio/history in three flavors:
 *   1) Default server-side window (period=1M, timeframe auto)
 *   2) Intraday crypto-style (7D/15Min, continuous, no_reset, cashflow_types=ALL)
 *   3) Custom async (timeframe=1D, start/end, cashflow_types subset)
 */
public final class PortfolioHistoryExample {
    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(PortfolioHistoryExample.class);

    public static void main(String[] args) {
        AlpacaRestBaseEndpoints endpoint = AlpacaRestBaseEndpoints.API_V2_PAPER_TRADING;
        boolean retries = true;

        var account = new AlpacaAccountRestService(endpoint);

        demoDefault(account, retries);
        demoIntradayCrypto(account, retries);
        demoCustomAsync(account, retries);
    }

    private static void demoDefault(AlpacaAccountRestService svc, boolean retries) {
        LOG.info("[SYNC] portfolioHistory DEFAULT (server)");
        Instant t0 = Instant.now();
        try {
            AlpacaPortfolioHistoryResponse dto = svc.getAccountPortfolioHistory(retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT);
            int n = dto.timestamp() != null ? dto.timestamp().size() : -1;
            LOG.info("[SYNC] OK in {} ms (points={}) timeframe={} baseValue={} baseValueAsOf={}",
                    ms(t0, Instant.now()), n, dto.timeframe(), dto.baseValue(), dto.baseValueAsOf());
            briefSample(dto);
            LOG.info("[SYNC] Preview:\n{}", safePreview(pretty(dto), 350));
            verifyDecoded("DEFAULT", dto);
        } catch (ApcaRestClientException e) {
            LOG.error("[SYNC] ERROR in {} ms -> {}", ms(t0, Instant.now()), e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("[SYNC] UNEXPECTED in {} ms", ms(t0, Instant.now()), e);
        }
    }

    private static void demoIntradayCrypto(AlpacaAccountRestService svc, boolean retries) {
        LOG.info("[SYNC] portfolioHistory 7D/15Min continuous no_reset cashflow=ALL");
        Instant t0 = Instant.now();
        try {
            AlpacaPortfolioHistoryResponse dto = svc.getAccountPortfolioHistory(
                    retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT,
                    "7D", "15Min", "continuous",
                    null, null, "no_reset", null, "ALL"
            );
            int n = dto.timestamp() != null ? dto.timestamp().size() : -1;
            LOG.info("[SYNC] OK in {} ms (points={}) timeframe={} baseValue={} baseValueAsOf={} cashflowKeys={}",
                    ms(t0, Instant.now()), n, dto.timeframe(), dto.baseValue(), dto.baseValueAsOf(),
                    dto.cashflow() != null ? dto.cashflow().keySet() : List.of());
            briefSample(dto);
            LOG.info("[SYNC] Preview:\n{}", safePreview(pretty(dto), 350));
            verifyDecoded("CRYPTO", dto);
        } catch (ApcaRestClientException e) {
            LOG.error("[SYNC] ERROR in {} ms -> {}", ms(t0, Instant.now()), e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("[SYNC] UNEXPECTED in {} ms", ms(t0, Instant.now()), e);
        }
    }

    private static void demoCustomAsync(AlpacaAccountRestService svc, boolean retries) {
        Instant now = Instant.now();
        Instant start = now.minus(Duration.ofDays(21));
        Instant end = now;

        LOG.info("[ASYNC-DTO] portfolioHistory timeframe=1D start/end cashflow=[CFEE,DIV]");
        Instant t0 = Instant.now();
        var fut = svc.getAsyncAccountPortfolioHistory(
                retries, DEFAULT_SINGLE_ACC_ID_RATE_LIMIT,
                null, "1D", null,
                start, end, null, null,
                List.of("CFEE", "DIV")
        );

        fut.thenAccept(dto -> {
                    int n = dto.timestamp() != null ? dto.timestamp().size() : -1;
                    LOG.info("[ASYNC-DTO] OK in {} ms (points={}) timeframe={} baseValue={} baseValueAsOf={} cashflowKeys={}",
                            ms(t0, Instant.now()), n, dto.timeframe(), dto.baseValue(), dto.baseValueAsOf(),
                            dto.cashflow() != null ? dto.cashflow().keySet() : List.of());
                    briefSample(dto);
                    LOG.info("[ASYNC-DTO] Preview:\n{}", safePreview(pretty(dto), 350));
                    verifyDecoded("CUSTOM-ASYNC", dto);
                })
                .exceptionally(ex -> { LOG.error("[ASYNC-DTO] ERROR in {} ms -> {}", ms(t0, Instant.now()), rootMessage(ex), ex); return null; });

        blockForDemo(fut, 20);
    }

    /* ---- small validators/summaries (example-purpose only) ---- */

    private static void verifyDecoded(String tag, AlpacaPortfolioHistoryResponse dto) {
        if (dto == null) { LOG.warn("[CHK:{}] null dto", tag); return; }
        var ts = dto.timestamp();
        var eq = dto.equity();
        var pl = dto.profitLoss();
        var plPct = dto.profitLossPct();
        if (ts == null || ts.isEmpty()) { LOG.warn("[CHK:{}] missing timestamp", tag); return; }

        int n = ts.size();
        boolean okEq   = (eq == null)   || eq.size()    == n;
        boolean okPl   = (pl == null)   || pl.size()    == n;
        boolean okPlPc = (plPct == null)|| plPct.size() == n;
        if (!okEq || !okPl || !okPlPc) {
            LOG.warn("[CHK:{}] size mismatch: ts={} eq={} pl={} plPct={}", tag, n,
                    (eq==null?"null":eq.size()), (pl==null?"null":pl.size()), (plPct==null?"null":plPct.size()));
        }
        if (dto.cashflow()!=null && !dto.cashflow().isEmpty()) {
            for (Map.Entry<String, List<BigDecimal>> e : dto.cashflow().entrySet()) {
                if (e.getValue()==null || e.getValue().size()!=n) {
                    LOG.warn("[CHK:{}] cashflow[{}] size vs ts: {} vs {}", tag, e.getKey(),
                            (e.getValue()==null?"null":e.getValue().size()), n);
                }
            }
        }
    }

    private static void briefSample(AlpacaPortfolioHistoryResponse dto) {
        int n = dto.timestamp()!=null ? dto.timestamp().size() : 0;
        if (n>0) {
            int last = n-1;
            LOG.info("First:  t={} eq={} pnl={} pnl%={}",
                    dto.timestamp().get(0),
                    safe(dto.equity(),0), safe(dto.profitLoss(),0), safe(dto.profitLossPct(),0));
            LOG.info("Last:   t={} eq={} pnl={} pnl%={}",
                    dto.timestamp().get(last),
                    safe(dto.equity(),last), safe(dto.profitLoss(),last), safe(dto.profitLossPct(),last));
        }
    }

    private static BigDecimal safe(List<BigDecimal> list, int i) {
        return (list==null || i<0 || i>=list.size()) ? null : list.get(i);
    }
}
