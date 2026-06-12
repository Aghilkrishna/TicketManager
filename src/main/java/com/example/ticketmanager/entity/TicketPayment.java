package com.example.ticketmanager.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ticket_payments")
public class TicketPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20)
    private TicketPaymentType paymentType;

    @Column(name = "expected_price", precision = 12, scale = 2)
    private BigDecimal expectedPrice;

    @Column(name = "actual_price", precision = 12, scale = 2)
    private BigDecimal actualPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", columnDefinition = "varchar(20)")
    private TicketPaymentMode paymentMode;

    @Column(name = "payment_datetime")
    private LocalDateTime paymentDatetime;

    @Column(name = "status", length = 50)
    private String status;
}
