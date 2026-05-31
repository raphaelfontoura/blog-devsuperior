package com.devsuperior.reservation.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mesmo contrato que o ms-ticket-ingestor publica na reservation-queue.fifo.
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
