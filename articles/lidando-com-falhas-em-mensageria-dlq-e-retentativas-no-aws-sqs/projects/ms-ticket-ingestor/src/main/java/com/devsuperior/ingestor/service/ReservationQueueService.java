package com.devsuperior.ingestor.service;

import com.devsuperior.ingestor.dto.ReservationRequest;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Enfileira a ReservationRequest na reservation-queue.fifo via SqsTemplate.
 * MessageGroupId=showId mantem a ordem das reservas do mesmo show.
 * MessageDeduplicationId=reservationId garante idempotencia no proprio SQS
 * (mesma reserva enviada duas vezes na janela de 5 minutos vira uma so).
 */
@Service
public class ReservationQueueService {

    private static final Logger log = LoggerFactory.getLogger(ReservationQueueService.class);

    private static final String SOURCE = "ms-ticket-ingestor";

    private final SqsTemplate sqsTemplate;

    @Value("${app.queue.reservation}")
    private String reservationQueue;

    public ReservationQueueService(SqsTemplate sqsTemplate) {
        this.sqsTemplate = sqsTemplate;
    }

    public void enqueue(ReservationRequest request, String correlationId) {
        log.info("Enfileirando reserva {} (show {}) na reservation-queue.fifo",
                request.reservationId(), request.showId());
        sqsTemplate.send(to -> to
                .queue(reservationQueue)
                .payload(request)
                .header("X-Correlation-ID", correlationId)
                .header("X-Source", SOURCE)
                .header(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER, request.showId())
                .header(SqsHeaders.MessageSystemAttributes.SQS_MESSAGE_DEDUPLICATION_ID_HEADER, request.reservationId()));
        log.info("Reserva {} enfileirada com MessageGroupId={} MessageDeduplicationId={}",
                request.reservationId(), request.showId(), request.reservationId());
    }
}
