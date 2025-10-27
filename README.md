
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
git clone https://github.com/Cepeppe/apca-rest-client.git
cd apca-rest-client
mvn -q clean install   # add -DskipTests if desired
```

### Use as a dependency (example coordinates)
```xml
<dependency>
  <groupId>io.github.cepeppe</groupId>
  <artifactId>apca-rest-client</artifactId>
  <version>1.1.1</version>
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

## 💡 Examples

All **examples** live under:

```
src/main/java/io/github/cepeppe/examples/
```

> Note: the project previously used “playgrounds”. All references were updated: they are now **examples**.

### What’s included (single `examples` package)

- `ExamplesSuite` — convenience launcher that runs a curated subset of examples.
- `CoreClockAccountExample` — `GET /v2/clock` and `GET /v2/account` (sync + async).
- `PortfolioHistoryExample` — `GET /v2/account/portfolio/history` (default, intraday-style, custom async).
- `AssetsExample` — `GET /v2/assets` (list + filters) and `GET /v2/assets/{idOrSymbol}`.
- `PositionsExample` — `GET /v2/positions` (list) and `GET /v2/positions/{idOrSymbol}` (404→null).  
  Includes **danger-zone** close operations (commented by default).
- `OrdersListingAndCancelExample` — list/get/cancel/cancel-all. **Safe** (no live creates).
- `OrdersLifecycleExample` — **create → get → replace → cancel** (paper). **DRY RUN by default** (see flags below).
- `CalendarExample` — `GET /v2/calendar` (range & single day).
- `WatchlistsExample` — `GET/POST/DELETE /v2/watchlists` and helpers.
- `AccountActivitiesExample` — `GET /v2/account/activities` (base/date/multi-type) and `/v2/account/activities/{activity_type}`.

> Each class has a `public static void main(String[] args)` and can be run standalone.

### How to run the examples

#### 1) From your IDE (simplest)
- Open the project in your IDE (IntelliJ/Eclipse/VS Code).
- Ensure `APCA_API_KEY_ID` and `APCA_API_SECRET_KEY` are set (Run/Debug configuration or environment).
- Run any class under `io.github.cepeppe.examples.*` (e.g., `ExamplesSuite`).

#### 2) From Maven (exec plugin)

Add (once) to your root `pom.xml` if not present:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.codehaus.mojo</groupId>
      <artifactId>exec-maven-plugin</artifactId>
      <version>3.3.0</version>
      <configuration>
        <cleanupDaemonThreads>true</cleanupDaemonThreads>
      </configuration>
    </plugin>
  </plugins>
</build>
```

Then run, for example:

```bash
# Run the suite (multiple demos)
mvn -q -Dexec.mainClass=io.github.cepeppe.examples.ExamplesSuite exec:java

# Or run a single demo:
mvn -q -Dexec.mainClass=io.github.cepeppe.examples.CoreClockAccountExample exec:java
mvn -q -Dexec.mainClass=io.github.cepeppe.examples.PortfolioHistoryExample exec:java
mvn -q -Dexec.mainClass=io.github.cepeppe.examples.AssetsExample exec:java
mvn -q -Dexec.mainClass=io.github.cepeppe.examples.PositionsExample exec:java
mvn -q -Dexec.mainClass=io.github.cepeppe.examples.OrdersListingAndCancelExample exec:java
mvn -q -Dexec.mainClass=io.github.cepeppe.examples.OrdersLifecycleExample exec:java
mvn -q -Dexec.mainClass=io.github.cepeppe.examples.CalendarExample exec:java
mvn -q -Dexec.mainClass=io.github.cepeppe.examples.WatchlistsExample exec:java
mvn -q -Dexec.mainClass=io.github.cepeppe.examples.AccountActivitiesExample exec:java
```

> Tip: if you prefer, you can also create run profiles in your IDE bound to these commands.

### Credentials & base URLs

Before running, ensure the following are available in your environment (or as JVM system properties):

- `APCA_API_KEY_ID` / `APCA_API_SECRET_KEY`
- Optional base-URL overrides (if you’re not using the built-in defaults):
  - `ALPACA_TRADING_PAPER_API_V2_URL`, `ALPACA_TRADING_PRODUCTION_API_V2_URL`, etc.

### LIVE order toggles (for `OrdersLifecycleExample`)

By default, `OrdersLifecycleExample` is in **DRY RUN**. To enable **live order submission (paper)**:

- JVM property: `-Dmm.playground.liveOrders=true`
- or environment variable: `MM_PLAYGROUND_LIVE_ORDERS=true`

Optional parameters (system property with ENV fallback):

- `mm.playground.symbol.crypto` (default: `BTCUSD`)
- `mm.playground.notional` (default: `1.00` USD)
- `mm.playground.limitPrice` (default: `100.00` — intentionally far from market to keep the order open)

> ⚠️ **Danger zone**: Be mindful when using `PositionsExample` close operations or `cancelAllOrders` in `OrdersListingAndCancelExample`. These produce side effects.

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

## 🛠️ Build, Test, Run

- Build: `mvn clean verify` (or `mvn -DskipTests clean package`)
- Run examples: via IDE or Maven Exec as shown above
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
