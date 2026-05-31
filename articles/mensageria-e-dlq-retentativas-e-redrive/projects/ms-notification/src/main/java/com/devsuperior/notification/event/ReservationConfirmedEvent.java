package com.devsuperior.notification.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Espelho do evento publicado pelo ms-reservation-handler no topico SNS.
 */
public record ReservationConfirmedEvent(
        String reservationId,
        String showId,
        String ticketTier,
        Integer quantity,
        BigDecimal totalAmountUsd,
        String buyerEmail,
        Instant reservedAt
) {
}
