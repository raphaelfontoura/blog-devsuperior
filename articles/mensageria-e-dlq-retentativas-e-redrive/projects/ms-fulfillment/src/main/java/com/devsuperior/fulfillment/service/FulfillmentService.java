package com.devsuperior.fulfillment.service;

import com.devsuperior.fulfillment.event.ReservationConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Servico que simula a liberacao do QR code da reserva. Quando o profile
 * "gateway-down" esta ativo, a feature flag app.fulfillment.gateway-down
 * vira true e o servico simula um gateway de impressao fora do ar, para
 * o leitor exercitar o ciclo de retentativa do SQS e ver a mensagem
 * caindo na DLQ apos o maxReceiveCount.
 */
@Service
public class FulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentService.class);

    @Value("${app.fulfillment.gateway-down:false}")
    private boolean gatewayDown;

    public void releaseTickets(ReservationConfirmedEvent event) {
        if (gatewayDown) {
            log.warn("Gateway de impressao marcado como indisponivel, simulando falha transiente para reserva {}",
                    event.reservationId());
            throw new RuntimeException("Gateway de impressao indisponivel");
        }
        log.info("Liberando QR code para reserva {} (show {}, tier {})",
                event.reservationId(), event.showId(), event.ticketTier());
    }
}
