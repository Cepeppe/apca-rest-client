package io.github.cepeppe.rest.limits;

import java.time.Duration;
import java.time.Instant;

/**
 * <h1>AlpacaRateLimitInfo</h1>
 *
 * Value object immutabile che rappresenta la "finestra" di rate-limit restituita da Alpaca
 * (tramite gli header standard: X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset).
 *
 * <p><b>Scelte di modellazione</b></p>
 * <ul>
 *   <li><b>limit</b>        – massimo numero di richieste consentite nel minuto corrente.</li>
 *   <li><b>remaining</b>    – quante richieste restano nel minuto corrente.</li>
 *   <li><b>resetAt</b>      – istante assoluto (UTC) in cui il contatore si resetta
 *       (header espresso come UNIX epoch, qui esposto come {@link Instant}).</li>
 * </ul>
 *
 * <p>Metodi di comodo:</p>
 * <ul>
 *   <li>{@link #isDepleted()} – vero se il budget residuo è esaurito (remaining &lt;= 0)</li>
 *   <li>{@link #timeUntilReset()} – durata residua al reset, calcolata rispetto a {@code Instant.now()}</li>
 * </ul>
 */
public record AlpacaRateLimitInfo(int limit, int remaining, Instant resetAt) {

    /** @return true se non restano più richieste disponibili nella finestra corrente. */
    public boolean isDepleted() {
        return remaining <= 0;
    }

    /**
     * Calcola la durata residua al reset della finestra rispetto all'orologio di sistema.
     * @return durata non negativa; 0 se il reset è già passato.
     */
    public Duration timeUntilReset() {
        Instant now = Instant.now();
        return resetAt.isAfter(now) ? Duration.between(now, resetAt) : Duration.ZERO;
    }
}
