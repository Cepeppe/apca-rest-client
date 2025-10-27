<!-- NOTE: When copying from certain chat clients, this README is wrapped in a 4-backtick code fence to preserve Markdown. Copy everything between the outer fences into `README.md` and remove this comment. -->

# Alpaca REST Client (Java)

> **Unofficial** Java client for the **Alpaca** REST APIs (Trading v2, Market Data v2/v1beta3, Broker v1).  
> This project is community-driven and **not affiliated with Alpaca**.

---

## 👋 Welcome, Contributions & Bug Reports

Contributions are **very welcome**. Please see **`CONTRIBUTING.md`** for guidelines (PR flow, code style, tests).  
If you find problems or bugs, **opening an issue** helps everyone — thank you!

> ℹ️ **Documentation language**
>
> Most docs and code comments are currently in **Italian** — apologies.  
> An **English translation is in progress**. Method names and signatures are intentionally **simple, uniform, and self-descriptive**, so the code should be understandable; translation tools can help for comments until the English docs are complete.

---

## 🧭 Overview

This library provides a pragmatic, production-oriented wrapper around the **Alpaca REST endpoints**, with:

- Consistent **sync & async** methods per endpoint.
- A robust HTTP layer (Java 21 `HttpClient`) with **exponential backoff + jitter** (retries only for **idempotent** methods).
- Opinionated **JSON/time** handling via Jackson (`Instant` everywhere, clear serialization policy).
- Minimal, production-friendly **logging** (SLF4J/Logback via a thin façade).
- A **per-account rate-limit tracker** (multiton) fed by response headers to help you respect server limits.

The focus is **clarity, safety, and observability**, rather than a “kitchen-sink” SDK.

---

## ✅ Endpoint Coverage (and what’s TODO)

- **Trading API v2** — **most endpoints are already exposed and mapped** (Clock, Account, Assets, Orders, Positions, Watchlists, Calendar, Account Activities, etc.).  
  Some less common/specialized endpoints are still **TODO**. The current surface should **cover the vast majority of users and use cases**.
- **Market Data v2 / v1beta3** — a practical subset is implemented; further helpers are on the roadmap.
- **Broker API v1** — foundational support; expansion planned.

If an endpoint you need is missing, please open an issue or a PR — it helps prioritize the roadmap.

---

## 🚀 Getting Started

### Requirements
- **Java 21+**
- **Maven 3.9+**

### Build from source
```bash
git clone https://github.com/<your-org-or-user>/apca-rest-client.git
cd apca-rest-client
mvn -q clean install   # add -DskipTests if desired
```

### Use as a dependency (example coordinates)
> Replace with your actual published coordinates when available.
```xml
<dependency>
  <groupId>io.github.your-gh-username</groupId>
  <artifactId>apca-rest-client</artifactId>
  <version>0.1.0</version>
</dependency>
```

---

## 🧩 Configuration

The client reads configuration with this precedence:

1. **Java System properties** (`-Dkey=value`)
2. **Environment variables**
3. Optional **`.env`** file in the project root

Using a `.env` file is perfectly fine. Example:

```dotenv
# === Http client config ===
BASE_BACKOFF_MS = 200
MAX_ATTEMPTS    = 3
BACKOFF_CAP_MS  = 5000
JITTER_MIN      = 0.5
JITTER_MAX      = 1.5

# === Alpaca ===
ALPACA_WS_URL      = wss://stream.data.alpaca.markets/v2/delayed_sip
ALPACA_WS_URL_TEST = wss://stream.data.alpaca.markets/v2/test

# Trading API v2
ALPACA_TRADING_PRODUCTION_API_V2_URL = https://api.alpaca.markets/v2/
ALPACA_TRADING_PAPER_API_V2_URL      = https://paper-api.alpaca.markets/v2/

# Market Data API v2
ALPACA_MARKET_DATA_PRODUCTION_API_V2_URL = https://data.alpaca.markets/v2/
ALPACA_MARKET_DATA_SANDBOX_API_V2_URL    = https://data.sandbox.alpaca.markets/v2/

# Market Data API v1beta3
ALPACA_MARKET_DATA_PRODUCTION_API_V1BETA3_URL = https://data.alpaca.markets/v1beta3/
ALPACA_MARKET_DATA_SANDBOX_API_V1BETA3_URL    = https://data.sandbox.alpaca.markets/v1beta3/

# Broker API v1
ALPACA_BROKER_PRODUCTION_API_V1_URL = https://broker-api.alpaca.markets/v1/
ALPACA_BROKER_SANDBOX_API_V1_URL    = https://broker-api.sandbox.alpaca.markets/v1/

APCA_API_KEY_ID     = your_api_key
APCA_API_SECRET_KEY = your_api_secret

ALPACA_ENV = paper   # or 'live'

# === JSON / Jackson ===
# false -> serialize Instant as ISO-8601 (human-readable); true -> epoch millis (compact)
JSON_DATES_EPOCH_MILLIS = false

# === Misc ===
MM_LOG_LEVEL = INFO
```

> **Logging**: a sample `logback.xml` is provided (colored console + daily rolling file).  
> Tune with JVM props like `-Dmm.level=INFO` and `-Dmm.logsDir=logs`.

---

## 💡 Quick Examples

### Synchronous & Asynchronous calls
```java
var base       = /* choose paper or live base endpoints */;
var clockSvc   = new AlpacaClockRestService(base);
var accountSvc = new AlpacaAccountRestService(base);

// Use a stable, per-account tag to aggregate rate-limit info (e.g., "PAPER:my-account"):
String accTag = "PAPER:my-account";

// GETs support retries when 'enableRetries=true'
var clock = clockSvc.getMarketClockInfo(true, accTag);
System.out.println("Market open? " + clock.isOpen());

accountSvc.getAsyncAccountDetails(true, accTag)
          .thenAccept(acc -> System.out.println("Account: " + acc.getId()))
          .join();
```

### Rate-limit monitoring (after your first API call)
The library automatically updates a **multiton** from response headers.  
You can **consult** it via **`AlpacaRateLimitMultiton`** (after at least one API call for a given tag):

```java
// Example (method names may vary slightly across releases):
// RateLimitSnapshot snap = AlpacaRateLimitMultiton.forTag(accTag).snapshot();
// System.out.printf("limit=%d remaining=%d resetAt=%s%n",
//     snap.getLimit(), snap.getRemaining(), snap.getResetInstant());
```

> The multiton is keyed by your **account tag** and reflects the latest `X-RateLimit-*` headers seen for that tag.

---

## 🧱 Design Highlights

- **HTTP client & retries**
    - Retries are enabled **only for idempotent methods** (GET/HEAD/PUT/DELETE/OPTIONS).
    - Backoff, cap, and jitter controlled by env (`BASE_BACKOFF_MS`, `BACKOFF_CAP_MS`, `JITTER_*`, etc.).
    - For **non-idempotent** operations (e.g., `POST`), handle idempotency yourself if you introduce retries (e.g., idempotency keys).

- **JSON/time policy**
    - All timestamps as `java.time.Instant`.
    - Default serialization: ISO-8601 UTC; optional epoch-millis for compactness.

- **Errors**
    - Non-OK HTTP or decoding issues raise domain exceptions with compact, useful context (without leaking secrets).

---

## 🧪 Examples & Playgrounds

The repository includes **example code** under playground packages for **documentation purposes**.  
They demonstrate base endpoint selection (paper vs live), sync/async flows, safe logging, and rate-limit snapshots.

> Treat them as **learning material**; names/structure may change in future refactors.

---

## 🛠️ Build, Test, Run

- Build: `mvn clean verify` (or `mvn -DskipTests clean package`)
- Run examples: from your IDE or via Maven `exec:java` if configured
- Configure logging via JVM props, e.g. `-Dmm.level=INFO -Dmm.logsDir=logs`

---

## 🗺️ Roadmap

- Complete remaining **Trading v2** TODOs and extend **Market Data** helpers.
- More examples and full **English** documentation.
- Publication guidance for Maven Central (if/when applicable).

---

## 📄 License & Notices

- Licensed under **Apache License 2.0** — see `LICENSE` and `NOTICE`.
- Third-party attributions in `THIRD-PARTY.txt`.

---

## ⚠️ Disclaimer

This is **unofficial** software. Use at your own risk. Trading involves risk; always validate in **paper** first and ensure proper risk controls.
