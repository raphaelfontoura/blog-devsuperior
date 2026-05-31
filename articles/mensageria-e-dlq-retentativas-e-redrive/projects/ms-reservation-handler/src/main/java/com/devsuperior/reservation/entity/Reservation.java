package com.devsuperior.reservation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Reserva persistida no H2. O reservationId e a PK natural, exatamente
 * para que o existsById() funcione como chave de idempotencia no listener
 * (uma mensagem reentregue cai aqui e e ignorada, em vez de virar
 * registro duplicado).
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    private String reservationId;

    @Column(nullable = false)
    private String showId;

    @Column(nullable = false, length = 32)
    private String ticketTier;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private BigDecimal unitPriceUsd;

    @Column(nullable = false)
    private BigDecimal totalAmountUsd;

    @Column(nullable = false)
    private String buyerEmail;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Instant reservedAt;

    public Reservation() {
    }

    public Reservation(String reservationId, String showId, String ticketTier, Integer quantity,
                       BigDecimal unitPriceUsd, BigDecimal totalAmountUsd, String buyerEmail,
                       String status, Instant reservedAt) {
        this.reservationId = reservationId;
        this.showId = showId;
        this.ticketTier = ticketTier;
        this.quantity = quantity;
        this.unitPriceUsd = unitPriceUsd;
        this.totalAmountUsd = totalAmountUsd;
        this.buyerEmail = buyerEmail;
        this.status = status;
        this.reservedAt = reservedAt;
    }

    public String getReservationId() { return reservationId; }
    public String getShowId() { return showId; }
    public String getTicketTier() { return ticketTier; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitPriceUsd() { return unitPriceUsd; }
    public BigDecimal getTotalAmountUsd() { return totalAmountUsd; }
    public String getBuyerEmail() { return buyerEmail; }
    public String getStatus() { return status; }
    public Instant getReservedAt() { return reservedAt; }
}
