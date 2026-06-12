package com.example.ticketmanager.repository;

import com.example.ticketmanager.entity.TicketPayment;
import com.example.ticketmanager.entity.TicketPaymentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TicketPaymentRepository extends JpaRepository<TicketPayment, Long> {
    Optional<TicketPayment> findByTicketIdAndPaymentType(Long ticketId, TicketPaymentType paymentType);
}
