package com.example.ticketmanager.repository;

import com.example.ticketmanager.entity.TicketSiteVisit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketSiteVisitRepository extends JpaRepository<TicketSiteVisit, Long> {
    List<TicketSiteVisit> findByTicketIdOrderByVisitedAtDesc(Long ticketId);
}
