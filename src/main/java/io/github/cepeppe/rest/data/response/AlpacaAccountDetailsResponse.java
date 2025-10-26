package io.github.cepeppe.rest.data.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Response DTO per i dettagli dell'account Alpaca.
 *
 * Note:
 * - Tutti i valori DECIMALI/percentuali sono modellati come BigDecimal (anche quando nel JSON arrivano come stringhe).
 * - Timestamp: created_at -> Instant (ISO-8601 con suffisso 'Z').
 * - balance_asof è una data pura (YYYY-MM-DD), quindi LocalDate è più naturale.
 * - Le configurazioni admin/user sono JsonNode per tollerare strutture variabili, oggetti vuoti ({}) e null.
 *
 * Se vuoi allineare il nome alla convenzione DTO del progetto, possiamo rinominarlo in
 * "AlpacaAccountDetailsResponseDto" senza cambiare il contenuto.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaAccountDetailsResponse(

        @JsonProperty("id")
        String id, // Identificativo univoco dell'account (UUID lato broker).

        @JsonProperty("admin_configurations")
        JsonNode adminConfigurations, // Configurazioni amministrative applicate dall'intermediario (oggetto arbitrario, può essere {}).

        @JsonProperty("user_configurations")
        JsonNode userConfigurations, // Preferenze/configurazioni impostate dall'utente (può essere null).

        @JsonProperty("account_number")
        String accountNumber, // Numero di conto trading (formato specifico Alpaca, es. "PA3KO5QRT1F0").

        @JsonProperty("status")
        String status, // Stato complessivo del conto (es. ACTIVE, INACTIVE, CLOSED).

        @JsonProperty("crypto_status")
        String cryptoStatus, // Stato dell'abilitazione crypto (es. ACTIVE/INACTIVE/SUSPENDED).

        @JsonProperty("options_approved_level")
        int optionsApprovedLevel, // Livello di approvazione opzioni concesso dal broker (soggetto a KYC/esperienza).

        @JsonProperty("options_trading_level")
        int optionsTradingLevel, // Livello operativo effettivo per le opzioni (può coincidere o essere inferiore all'approvato).

        @JsonProperty("currency")
        String currency, // Valuta base del conto (ISO 4217, tipicamente "USD").

        @JsonProperty("buying_power")
        BigDecimal buyingPower, // Buying Power totale disponibile (considera leva/regolamenti del conto).

        @JsonProperty("regt_buying_power")
        BigDecimal regtBuyingPower, // Buying Power secondo Reg T (normativa USA per margine multi-day).

        @JsonProperty("daytrading_buying_power")
        BigDecimal daytradingBuyingPower, // Buying Power intraday per day trading (DTBP), rilevante per PDT.

        @JsonProperty("effective_buying_power")
        BigDecimal effectiveBuyingPower, // Buying Power “effettivo” applicabile ora (spesso min(buying_power, regt/dtbp)).

        @JsonProperty("non_marginable_buying_power")
        BigDecimal nonMarginableBuyingPower, // Potere d’acquisto per asset non marginabili (cash-only).

        @JsonProperty("options_buying_power")
        BigDecimal optionsBuyingPower, // Potere d’acquisto specifico per operazioni in opzioni.

        @JsonProperty("bod_dtbp")
        BigDecimal bodDtbp, // Day-Trading Buying Power a inizio giornata (BOD = Beginning Of Day).

        @JsonProperty("cash")
        BigDecimal cash, // Cash disponibile/settled al momento dello snapshot.

        @JsonProperty("accrued_fees")
        BigDecimal accruedFees, // Commissioni/oneri maturati ma non ancora regolati.

        @JsonProperty("portfolio_value")
        BigDecimal portfolioValue, // Valore totale del portafoglio (equity + valore posizioni).

        @JsonProperty("pattern_day_trader")
        boolean patternDayTrader, // Flag PDT (Pattern Day Trader) ai sensi della normativa USA.

        @JsonProperty("trading_blocked")
        boolean tradingBlocked, // Se true, il broker blocca l’operatività (ordini non accettati).

        @JsonProperty("transfers_blocked")
        boolean transfersBlocked, // Se true, trasferimenti (depositi/prelievi) bloccati.

        @JsonProperty("account_blocked")
        boolean accountBlocked, // Se true, conto globalmente bloccato (include trading/transfers).

        @JsonProperty("created_at")
        Instant createdAt, // Timestamp di creazione del conto (UTC, ISO-8601).

        @JsonProperty("trade_suspended_by_user")
        boolean tradeSuspendedByUser, // Se true, l’utente ha sospeso volontariamente le negoziazioni.

        @JsonProperty("multiplier")
        BigDecimal multiplier, // Moltiplicatore di leva del conto (es. 2 = leva 2x; 1 = cash-only).

        @JsonProperty("shorting_enabled")
        boolean shortingEnabled, // True se è consentito aprire posizioni short.

        @JsonProperty("equity")
        BigDecimal equity, // Equity attuale del conto (cash + PnL unrealized + altri aggiustamenti).

        @JsonProperty("last_equity")
        BigDecimal lastEquity, // Equity di riferimento “precedente” (es. fine giornata precedente).

        @JsonProperty("long_market_value")
        BigDecimal longMarketValue, // Valore di mercato delle posizioni long.

        @JsonProperty("short_market_value")
        BigDecimal shortMarketValue, // Valore assoluto delle posizioni short (spesso riportato come numero positivo).

        @JsonProperty("position_market_value")
        BigDecimal positionMarketValue, // Valore di mercato complessivo delle posizioni (long + short, convenzione broker).

        @JsonProperty("initial_margin")
        BigDecimal initialMargin, // Margine iniziale richiesto per mantenere le posizioni.

        @JsonProperty("maintenance_margin")
        BigDecimal maintenanceMargin, // Margine di mantenimento attuale richiesto.

        @JsonProperty("last_maintenance_margin")
        BigDecimal lastMaintenanceMargin, // Margine di mantenimento alla fotografia precedente (es. EOD).

        @JsonProperty("sma")
        BigDecimal sma, // Special Memorandum Account (linea di credito Reg T; semantica broker-specifica).

        @JsonProperty("daytrade_count")
        int daytradeCount, // Numero di day trade negli ultimi 5 giorni lavorativi (rilevante per PDT).

        @JsonProperty("balance_asof")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate balanceAsof, // Data (senza orario) a cui si riferiscono i saldi riportati.

        @JsonProperty("crypto_tier")
        int cryptoTier, // Livello/tier abilitazione crypto (dipende da KYC/abilitazioni del broker).

        @JsonProperty("intraday_adjustments")
        BigDecimal intradayAdjustments, // Aggiustamenti intraday applicati dall’intermediario (es. correzioni margine/fee).

        @JsonProperty("pending_reg_taf_fees")
        BigDecimal pendingRegTafFees // Tasse/regulatory fees (TAF) pendenti ma non ancora addebitate.
) {}
