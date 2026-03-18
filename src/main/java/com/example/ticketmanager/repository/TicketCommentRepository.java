package com.example.ticketmanager.repository;

import com.example.ticketmanager.entity.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {
    List<TicketComment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    List<TicketComment> findByTicketIdAndParentIsNullOrderByCreatedAtAsc(Long ticketId);
}
