package io.github.cepeppe.rest.limits;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <h1>AlpacaRateLimitMultiton</h1>
 *
 * <p>
 * A <b>process-wide singleton</b> that implements a per-account <b>multiton</b> registry.
 * For each {@code accountId} (which can be an Alpaca account number or any developer-defined key),
 * it exposes a {@link Tracker} holding the latest <em>rate-limit snapshot</em>:
 * remaining requests in the current 1-minute window and the reset {@link Instant}.
 * </p>
 *
 * <h2>Responsibilities & Operational Contract</h2>
 * <ul>
 *   <li><b>Caller/Programmer responsibility (before sending a request):</b>
 *       consult the registry/tracker <i>before</i> issuing any Alpaca REST call.
 *       Use {@link Tracker#shouldThrottle()}, {@link Tracker#timeUntilReset()},
 *       {@link Tracker#remaining()}, or your own policy to decide what to do
 *       (wait, queue, shed load, or proceed).</li>
 *   <li><b>REST client system responsibility (after receiving a response):</b>
 *       update the registry <i>after every HTTP response</i> using
 *       {@link #updateFromResponse(String, HttpResponse)} (or {@link Tracker#updateFromResponse(HttpResponse)}).
 *       The X-RateLimit-* headers in the response are the <i>single source of truth</i>.</li>
 *   <li><b>No auto-throttling:</b> this component never sleeps, retries, or fabricates resets.
 *       It only stores and exposes snapshots.</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>1) Caller consults before the request; client updates after the response</h3>
 * <pre>{@code
 * var registry = AlpacaRateLimitMultiton.getInstance();
 * var tracker  = registry.forAccount("PAPER:my-key");
 *
 * // Programmer policy BEFORE the call:
 * if (tracker.isInitialized() && tracker.shouldThrottle()) {
 *     Thread.sleep(tracker.timeUntilReset().toMillis());
 * }
 *
 * HttpResponse<String> resp = httpClient.send(request, BodyHandlers.ofString());
 *
 * // REST service responsibility AFTER the call:
 * registry.updateFromResponse("PAPER:my-key", resp);
 * }</pre>
 *
 * <h3>2) Consider staleness if your workflow has long gaps between calls</h3>
 * <pre>{@code
 * Duration ttl = Duration.ofMinutes(2);
 * if (tracker.isStale(ttl)) {
 *     // Treat snapshot as unknown/weak. You may choose to not rely on tryConsumePermit()
 *     // and simply send the next request (server is authoritative), or reduce concurrency.
 * }
 * }</pre>
 *
 * <h2>Lifecycle & Data Freshness</h2>
 * <ul>
 *   <li><b>First-time access:</b> until a real response is processed, the tracker has no snapshot.
 *       {@link Tracker#isInitialized()} is {@code false}, {@link Tracker#remaining()} returns {@code -1},
 *       {@link Tracker#shouldThrottle()} returns {@code false}, and
 *       {@link Tracker#tryConsumePermit()} returns {@code true} (cannot protect without data).</li>
 *   <li><b>Staleness:</b> the registry does not auto-expire snapshots. Use
 *       {@link Tracker#isStale(Duration)} to decide if a cached snapshot is too old for your policy.
 *       The server’s headers remain the source of truth on the next response.</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * <ul>
 *   <li>The registry is backed by a {@link ConcurrentHashMap}.</li>
 *   <li>Each {@link Tracker} stores an immutable {@link AlpacaRateLimitInfo} in an {@link AtomicReference}.</li>
 *   <li>Updates are atomic; snapshots are immediately visible to all threads.</li>
 * </ul>
 *
 * <h2>Merging policy</h2>
 * If multiple responses arrive out of order, the tracker prefers the snapshot with a <b>later</b> {@code resetAt}.
 * If {@code resetAt} ties, the most recent arrival wins.
 *
 * @see AlpacaRateLimitInfo
 * @see AlpacaRateLimitUtils
 */
public final class AlpacaRateLimitMultiton {

    // ---------- Singleton holder pattern ----------
    private static final class Holder {
        private static final AlpacaRateLimitMultiton INSTANCE = new AlpacaRateLimitMultiton();
    }

    /** Returns the process-wide singleton registry. */
    public static AlpacaRateLimitMultiton getInstance() {
        return Holder.INSTANCE;
    }

    private AlpacaRateLimitMultiton() { }

    // ---------- State ----------
    private final ConcurrentMap<String, Tracker> byAccount = new ConcurrentHashMap<>();

    /**
     * Returns (or creates) the {@link Tracker} for the given account.
     * The {@code accountId} can be any developer-defined logical key.
     *
     * @param accountId non-null, non-blank logical account key
     * @return the per-account tracker (multiton entry)
     * @throws NullPointerException if {@code accountId} is null
     * @throws IllegalArgumentException if {@code accountId} is blank
     */
    public Tracker forAccount(String accountId) {
        String key = normalizeKey(accountId);
        return byAccount.computeIfAbsent(key, Tracker::new);
    }

    /** Convenience: returns the latest {@link AlpacaRateLimitInfo} snapshot for an account, if available. */
    public Optional<AlpacaRateLimitInfo> getInfo(String accountId) {
        return Optional.ofNullable(byAccount.get(normalizeKey(accountId)))
                .flatMap(Tracker::getSnapshot);
    }

    /** Parses and updates the per-account tracker from an Alpaca HTTP response (no-op if headers are missing). */
    public void updateFromResponse(String accountId, HttpResponse<?> response) {
        forAccount(accountId).updateFromResponse(response);
    }

    /** Updates the per-account tracker with an explicit snapshot (e.g., in tests or custom decoding). */
    public void update(String accountId, AlpacaRateLimitInfo info) {
        forAccount(accountId).update(info);
    }

    private static String normalizeKey(String accountId) {
        Objects.requireNonNull(accountId, "accountId");
        String key = accountId.trim();
        if (key.isEmpty()) throw new IllegalArgumentException("accountId cannot be blank");
        return key;
    }

    // =====================================================================
    // =                              Tracker                              =
    // =====================================================================

    /**
     * <h2>Tracker</h2>
     * A logical per-account instance that holds the current {@link AlpacaRateLimitInfo} snapshot.
     * All operations are thread-safe and state is visible across threads.
     */
    public static final class Tracker {

        private final String accountId;
        private final AtomicReference<AlpacaRateLimitInfo> state = new AtomicReference<>(null);
        private volatile Instant lastUpdated = null;

        private Tracker(String accountId) {
            this.accountId = accountId;
        }

        /** Extracts X-RateLimit-* headers from the response and updates the snapshot if present/valid. */
        public void updateFromResponse(HttpResponse<?> response) {
            AlpacaRateLimitUtils.extractAlpacaRateLimit(response).ifPresent(this::update);
        }

        /**
         * Updates the snapshot with a "fresher" one.
         * Merge policy:
         * <ul>
         *   <li>If there is no current state, set the incoming one.</li>
         *   <li>Otherwise, prefer the snapshot whose {@code resetAt} is later.
         *       If {@code resetAt} ties, prefer the most recent arrival.</li>
         * </ul>
         *
         * @param incoming non-null snapshot to merge into the current state
         */
        public void update(AlpacaRateLimitInfo incoming) {
            Objects.requireNonNull(incoming, "incoming");
            for (;;) {
                AlpacaRateLimitInfo current = state.get();
                if (current == null) {
                    if (state.compareAndSet(null, incoming)) break;
                    continue;
                }
                boolean newer = incoming.resetAt().isAfter(current.resetAt())
                        || (incoming.resetAt().equals(current.resetAt()) && shouldPreferIncoming());
                if (!newer) break; // keep the current state
                if (state.compareAndSet(current, incoming)) break;
            }
            lastUpdated = Instant.now();
        }

        private boolean shouldPreferIncoming() {
            // Simple tie-breaker: prefer the latest arrival when resetAt is equal.
            return true;
        }

        /** @return the current immutable snapshot, if initialized. */
        public Optional<AlpacaRateLimitInfo> getSnapshot() {
            return Optional.ofNullable(state.get());
        }

        /** @return {@code true} if at least one real snapshot was applied. */
        public boolean isInitialized() {
            return state.get() != null;
        }

        /**
         * @return {@code true} if the snapshot is absent or older than {@code ttl} from now.
         *         Useful to treat very old state as "weak" or "unknown" in your policy.
         */
        public boolean isStale(Duration ttl) {
            Objects.requireNonNull(ttl, "ttl");
            Instant lu = lastUpdated;
            if (lu == null) return true;
            return lu.isBefore(Instant.now().minus(ttl));
        }

        /** @return remaining calls in the current window, or -1 if unknown. */
        public int remaining() {
            AlpacaRateLimitInfo s = state.get();
            return (s != null) ? s.remaining() : -1;
        }

        /** @return the reset instant, or {@link Instant#EPOCH} if unknown. */
        public Instant resetAt() {
            AlpacaRateLimitInfo s = state.get();
            return (s != null) ? s.resetAt() : Instant.EPOCH;
        }

        /** @return the remaining time until reset; {@link Duration#ZERO} if unknown or already past. */
        public Duration timeUntilReset() {
            AlpacaRateLimitInfo s = state.get();
            return (s != null) ? s.timeUntilReset() : Duration.ZERO;
        }

        /**
         * Advisory signal to back off:
         * returns {@code true} when we have a snapshot, {@code remaining <= 0},
         * and the reset instant is still in the future; otherwise {@code false}.
         */
        public boolean shouldThrottle() {
            AlpacaRateLimitInfo s = state.get();
            if (s == null) return false;            // no info → cannot advise throttling
            if (s.remaining() > 0) return false;    // still have budget
            return Instant.now().isBefore(s.resetAt());
        }

        /**
         * Optimistic local consumption of one "permit".
         * <ul>
         *   <li>If there is no snapshot → returns {@code true} (cannot protect without info).</li>
         *   <li>If {@code remaining > 0} → atomically decrements and returns {@code true}.</li>
         *   <li>If {@code remaining <= 0} and reset is still in the future → returns {@code false} (throttle).</li>
         *   <li>If reset is past, this method does <b>not</b> fabricate a new reset;
         *       wait for a new response to refresh the snapshot.</li>
         * </ul>
         */
        public boolean tryConsumePermit() {
            for (;;) {
                AlpacaRateLimitInfo s = state.get();
                if (s == null) return true; // no info → don't block the call

                if (s.remaining() <= 0) {
                    // If reset hasn't happened yet, throttle; if it has, we still wait for a fresh response.
                    return !Instant.now().isBefore(s.resetAt());
                }

                AlpacaRateLimitInfo next = new AlpacaRateLimitInfo(
                        s.limit(), s.remaining() - 1, s.resetAt());
                if (state.compareAndSet(s, next)) return true;
            }
        }

        /** @return the timestamp of the last accepted update (may be {@code null} if never updated). */
        public Instant lastUpdatedAt() {
            return lastUpdated;
        }

        @Override public String toString() {
            AlpacaRateLimitInfo s = state.get();
            return "Tracker{" +
                    "accountId='" + accountId + '\'' +
                    ", state=" + (s == null ? "null" : ("limit=" + s.limit() + ", remaining=" + s.remaining() + ", resetAt=" + s.resetAt())) +
                    ", lastUpdated=" + lastUpdated +
                    '}';
        }
    }
}
