package io.github.cepeppe.rest.data.response;

import java.io.Serializable;

/**
 * <h1>AlpacaAccountActivityResponse</h1>
 *
 * Interfaccia sigillata (JDK 21) per rappresentare le attività di conto Alpaca:
 * <ul>
 *   <li><b>GET /v2/account/activities</b> – array eterogeneo (FILL + Non-Trade)</li>
 *   <li><b>GET /v2/account/activities/{activity_type}</b> – array omogeneo (un solo tipo)</li>
 * </ul>
 *
 * <p>Implementazioni:
 * <ul>
 *   <li>{@link AlpacaAccountTradeActivityResponse} — FILL (trade)</li>
 *   <li>{@link AlpacaAccountNonTradeActivityResponse} — tutte le Non-Trade Activities</li>
 * </ul>
 *
 * <h3>Parsing suggerito</h3>
 * Dispatch basato su {@code activity_type}: "FILL" → record trade, altrimenti → record non-trade.
 *
 * <h3>Costanti di riferimento</h3>
 * Si vedano le classi nel wrapper {@code constants.AlpacaResponseConstants}
 * per:
 * <ul>
 *   <li>tutti gli {@code activity_type} ( {@code AlpacaActivityTypes} )</li>
 *   <li>i {@code type} delle FILL ( {@code AlpacaFillEventTypes} )</li>
 *   <li>il {@code side} delle FILL ( {@code AlpacaTradeSide} )</li>
 *   <li>gli {@code order_status} possibili ( {@code AlpacaOrderStatus} )</li>
 * </ul>
 */
public sealed interface AlpacaAccountActivityResponse extends Serializable
        permits AlpacaAccountTradeActivityResponse, AlpacaAccountNonTradeActivityResponse {
}
