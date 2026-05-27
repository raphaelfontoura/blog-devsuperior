package com.devsuperior.ingestor.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload de entrada do POST /api/reservations. O ingestor nao decide
 * nada sobre a reserva, apenas valida campos chave e enfileira na
 * reservation-queue.fifo. A regra de negocio fica no
 * ms-reservation-handler.
 */
public record ReservationRequest(
        String reservationId,
        String showId,
        String ticketTier,
        Integer quantity,
        BigDecimal unitPriceUsd,
        String buyerEmail,
        Instant requestedAt
) {
}
