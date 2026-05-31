package com.devsuperior.notification.service;

import com.devsuperior.notification.event.ReservationConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mantem listas de SseEmitter por reservationId. Cada cliente que abre o
 * GET /api/notifications/stream/{reservationId} fica em uma das listas.
 * Quando o evento de confirmacao chega, faz broadcast apenas para os
 * emitters daquela reserva. Entries vazias sao removidas, evitando leak
 * ao longo do tempo.
 *
 * Por design, a notification-queue.fifo NAO tem DLQ. Se a falha for
 * transiente (rede), o SQS reentrega ate o consumer responder OK ou
 * a mensagem expirar (4 dias por default). Se for permanente (bug),
 * descartar uma notificacao expirada e melhor que insistir e atrasar
 * outras reservas atras dela.
 */
@Service
public class NotificationBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(NotificationBroadcaster.class);

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByReservationId = new ConcurrentHashMap<>();

    public void register(String reservationId, SseEmitter emitter) {
        emittersByReservationId
                .computeIfAbsent(reservationId, key -> new CopyOnWriteArrayList<>())
                .add(emitter);

        emitter.onCompletion(() -> removeEmitter(reservationId, emitter, "completion"));
        emitter.onTimeout(() -> removeEmitter(reservationId, emitter, "timeout"));
    }

    public void broadcast(ReservationConfirmedEvent event) {
        List<SseEmitter> targets = emittersByReservationId.get(event.reservationId());
        if (targets == null || targets.isEmpty()) {
            log.info("Nenhum cliente conectado ao stream da reserva {}, evento descartado para SSE",
                    event.reservationId());
            return;
        }

        log.info("Broadcast da reserva {} para {} cliente(s) conectado(s)",
                event.reservationId(), targets.size());

        for (SseEmitter emitter : targets) {
            try {
                emitter.send(SseEmitter.event()
                        .name("reservation-confirmed")
                        .data(event));
            } catch (IOException e) {
                log.warn("Falha ao enviar evento para emitter da reserva {}, removendo: {}",
                        event.reservationId(), e.getMessage());
                removeEmitter(event.reservationId(), emitter, "io-error");
            }
        }
    }

    private void removeEmitter(String reservationId, SseEmitter emitter, String reason) {
        CopyOnWriteArrayList<SseEmitter> list = emittersByReservationId.get(reservationId);
        if (list == null) {
            return;
        }
        list.remove(emitter);
        if (list.isEmpty()) {
            emittersByReservationId.remove(reservationId, list);
        }
        log.info("Emitter da reserva {} removido (motivo: {}), restam {}",
                reservationId, reason, list.size());
    }
}
