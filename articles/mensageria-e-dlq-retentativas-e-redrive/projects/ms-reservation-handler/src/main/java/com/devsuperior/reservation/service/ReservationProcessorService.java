package com.devsuperior.reservation.service;

import com.devsuperior.reservation.dto.ReservationRequest;
import com.devsuperior.reservation.entity.Reservation;
import com.devsuperior.reservation.event.ReservationConfirmedEvent;
import com.devsuperior.reservation.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Orquestra o consumo da reservation-queue.fifo:
 *   1. checa idempotencia pelo reservationId no H2
 *   2. calcula o total com taxa de servico de 8%
 *   3. persiste a reserva
 *   4. publica ReservationConfirmedEvent no SNS ticket-events.fifo
 */
@Service
public class ReservationProcessorService {

    private static final Logger log = LoggerFactory.getLogger(ReservationProcessorService.class);

    private static final BigDecimal SERVICE_FEE_MULTIPLIER = new BigDecimal("1.08");

    private final ReservationRepository repository;
    private final ReservationEventPublisher eventPublisher;

    public ReservationProcessorService(ReservationRepository repository,
                                       ReservationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public void process(ReservationRequest request, String correlationId) {
        if (repository.existsById(request.reservationId())) {
            log.warn("Reserva {} ja foi processada, ignorando duplicata", request.reservationId());
            return;
        }

        BigDecimal totalAmount = request.unitPriceUsd()
                .multiply(BigDecimal.valueOf(request.quantity()))
                .multiply(SERVICE_FEE_MULTIPLIER)
                .setScale(2, RoundingMode.HALF_UP);

        log.info("Calculo de valor para reserva {}: unit={} qty={} total={}",
                request.reservationId(), request.unitPriceUsd(), request.quantity(), totalAmount);

        Reservation reservation = new Reservation(
                request.reservationId(),
                request.showId(),
                request.ticketTier(),
                request.quantity(),
                request.unitPriceUsd(),
                totalAmount,
                request.buyerEmail(),
                "CONFIRMED",
                Instant.now()
        );
        repository.save(reservation);

        log.info("Reserva persistida: reservationId={} total={} USD",
                reservation.getReservationId(), totalAmount);

        ReservationConfirmedEvent event = new ReservationConfirmedEvent(
                reservation.getReservationId(),
                reservation.getShowId(),
                reservation.getTicketTier(),
                reservation.getQuantity(),
                reservation.getTotalAmountUsd(),
                reservation.getBuyerEmail(),
                reservation.getReservedAt()
        );
        eventPublisher.publish(event, correlationId);
    }
}
