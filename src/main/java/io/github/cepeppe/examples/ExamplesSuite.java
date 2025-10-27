package io.github.cepeppe.examples;

import io.github.cepeppe.logging.ApcaRestClientLogger;
import io.github.cepeppe.rest.AlpacaRestBaseEndpoints;


/**
 * ExamplesSuite
 *
 * Convenience launcher that sequentially runs a subset of examples.
 * Adjust the toggles below as you wish.
 */
public final class ExamplesSuite {
    private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(ExamplesSuite.class);

    private ExamplesSuite() {}

    public static void main(String[] args) {
        LOG.info("=== ExamplesSuite START ===");

        // Choose your target (PAPER by default)
        AlpacaRestBaseEndpoints endpoint = AlpacaRestBaseEndpoints.API_V2_PAPER_TRADING;

        // Core Clock & Account
        CoreClockAccountExample.main(new String[]{});

        // Portfolio History
        PortfolioHistoryExample.main(new String[]{});

        // Assets (list + detail)
        AssetsExample.main(new String[]{});

        // Positions (list + detail; close ops commented by default)
        PositionsExample.main(new String[]{});

        // Orders (safe listing/cancel-all demo)
        OrdersListingAndCancelExample.main(new String[]{});

        // Calendar
        CalendarExample.main(new String[]{});

        // Watchlists
        WatchlistsExample.main(new String[]{});

        // Account Activities
        AccountActivitiesExample.main(new String[]{});

        LOG.info("=== ExamplesSuite END ===");
    }
}

