package com.devsuperior.reservation.service;

import com.devsuperior.reservation.event.ReservationConfirmedEvent;
import io.awspring.cloud.sns.core.SnsHeaders;
import io.awspring.cloud.sns.core.SnsTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Publica ReservationConfirmedEvent no topico SNS FIFO ticket-events.fifo.
 * Serializa em JSON via Jackson, propaga X-Correlation-ID nos headers,
 * e passa MessageGroupId=showId / MessageDeduplicationId=reservationId
 * pelos headers SnsHeaders.MESSAGE_GROUP_ID_HEADER /
 * SnsHeaders.MESSAGE_DEDUPLICATION_ID_HEADER.
 */
@Service
public class ReservationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReservationEventPublisher.class);

    private static final String SOURCE = "ms-reservation-handler";

    private final SnsTemplate snsTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.topic.ticket-events}")
    private String ticketEventsTopic;

    public ReservationEventPublisher(SnsTemplate snsTemplate, ObjectMapper objectMapper) {
        this.snsTemplate = snsTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(ReservationConfirmedEvent event, String correlationId) {
        log.info("Publicando ReservationConfirmedEvent reservationId={} showId={} tier={} no topico {}",
                event.reservationId(), event.showId(), event.ticketTier(), ticketEventsTopic);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Falha ao serializar ReservationConfirmedEvent reservationId=" + event.reservationId(), e);
        }
        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Correlation-ID", correlationId != null ? correlationId : "NA");
        headers.put("X-Source", SOURCE);
        headers.put("contentType", "application/json");
        headers.put(SnsHeaders.MESSAGE_GROUP_ID_HEADER, event.showId());
        headers.put(SnsHeaders.MESSAGE_DEDUPLICATION_ID_HEADER, event.reservationId());
        snsTemplate.send(ticketEventsTopic,
                MessageBuilder.withPayload(payload).copyHeaders(headers).build());
        log.info("ReservationConfirmedEvent reservationId={} publicado com sucesso", event.reservationId());
    }
}
