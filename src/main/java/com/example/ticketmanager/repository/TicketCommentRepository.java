package com.example.ticketmanager.repository;

import com.example.ticketmanager.entity.TicketComment;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {
    @Cacheable("ticketComments")
    List<TicketComment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    @Cacheable("ticketComments")
    List<TicketComment> findByTicketIdAndParentIsNullOrderByCreatedAtAsc(Long ticketId);
}
