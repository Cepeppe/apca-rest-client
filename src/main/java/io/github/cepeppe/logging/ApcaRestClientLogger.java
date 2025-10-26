package io.github.cepeppe.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ApcaRestClientLogger - Facciata minima per il logging
 *
 * Perché usarla:
 * - API pulita e stabile (se cambi backend, non tocchi il resto del codice).
 * - Metodi essenziali per i livelli tipici.
 *
 * Uso:
 *   private static final ApcaRestClientLogger LOG = ApcaRestClientLogger.getLogger(MyClass.class);
 *   LOG.info("Ready");
 *   LOG.debug("Valore x={}", x);
 *   LOG.error("Errore", e);
 */
public final class ApcaRestClientLogger {

    private final Logger delegate;

    private ApcaRestClientLogger(Logger delegate) {
        this.delegate = delegate;
    }

    /** Restituisce un logger legato alla classe chiamante. */
    public static ApcaRestClientLogger getLogger(Class<?> clazz) {
        return new ApcaRestClientLogger(LoggerFactory.getLogger(clazz));
    }

    // Livelli standard
    public void trace(String msg, Object... args) { delegate.trace(msg, args); }
    public void debug(String msg, Object... args) { delegate.debug(msg, args); }
    public void info (String msg, Object... args) { delegate.info (msg, args); }
    public void warn (String msg, Object... args) { delegate.warn (msg, args); }
    public void error(String msg, Object... args){ delegate.error(msg, args); }

    /*
        Trace - Only when I would be "tracing" the code and trying to
            find one part of a function specifically.
        Debug - Information that is diagnostically helpful to people
            more than just developers (IT, sysadmins, etc.).
        Info - Generally useful information to log (service start/stop,
            configuration assumptions, etc). Info I want to always have
            available but usually don't care about under normal circumstances.
            This is my out-of-the-box config level.
        Warn - Anything that can potentially cause application oddities, but
            for which I am automatically recovering. (Such as switching from
            a primary to backup server, retrying an operation, missing secondary data, etc.)
        Error - Any error which is fatal to the operation, but not the service
            or application (can't open a required file, missing data, etc.).
            These errors will force user (administrator, or direct user) intervention.
            These are usually reserved (in my apps) for incorrect connection strings,
            missing services, etc.
        Fatal - Any error that is forcing a shutdown of the service or application to
            prevent data loss (or further data loss). I reserve these only for the most
            heinous errors and situations where there is guaranteed to have been data corruption or loss.
     */

    // Overload comodo con Throwable
    public void warn (String msg, Throwable t)   { delegate.warn (msg, t); }
    public void error(String msg, Throwable t)   { delegate.error(msg, t); }
}
