package com.devsuperior.ingestor.controller;

import com.devsuperior.ingestor.dto.ReservationRequest;
import com.devsuperior.ingestor.filter.CorrelationIdFilter;
import com.devsuperior.ingestor.service.ReservationQueueService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private static final Logger log = LoggerFactory.getLogger(ReservationController.class);

    private final ReservationQueueService queueService;

    public ReservationController(ReservationQueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createReservation(
            @RequestBody ReservationRequest request,
            HttpServletRequest httpRequest) {

        // Valida o minimo necessario para nao enfileirar lixo, o restante
        // da validacao de negocio fica no reservation-handler.
        if (request.reservationId() == null || request.reservationId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "reservationId obrigatorio"));
        }
        if (request.showId() == null || request.showId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "showId obrigatorio"));
        }

        String correlationId = (String) httpRequest.getAttribute(CorrelationIdFilter.REQUEST_ATTR);
        log.info("Reserva recebida: reservationId={} showId={} tier={}",
                request.reservationId(), request.showId(), request.ticketTier());
        queueService.enqueue(request, correlationId);
        return ResponseEntity.accepted().body(Map.of(
                "status", "accepted",
                "correlationId", correlationId,
                "reservationId", request.reservationId()));
    }
}
