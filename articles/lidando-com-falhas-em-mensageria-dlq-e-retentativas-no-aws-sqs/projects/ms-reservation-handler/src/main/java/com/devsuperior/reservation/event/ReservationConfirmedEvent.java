package com.devsuperior.reservation.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Evento de dominio publicado no topico SNS ticket-events.fifo apos a
 * reserva ser persistida com sucesso. Esse e o evento que dispara o
 * fan-out para notification e fulfillment.
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
