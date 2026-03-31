package com.example.ticketmanager.repository;

import com.example.ticketmanager.entity.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {
    @Query("""
            select distinct c from ChatConversation c
            left join fetch c.participants
            where c.id = :id
            """)
    Optional<ChatConversation> findByIdWithParticipants(@Param("id") Long id);

    @Query("""
            select c from ChatConversation c
            join c.participants p1
            join c.participants p2
            where p1.id = :userId
              and p2.id = :otherUserId
              and size(c.participants) = 2
            """)
    Optional<ChatConversation> findDirectConversation(@Param("userId") Long userId, @Param("otherUserId") Long otherUserId);

    @Query("""
            select distinct c from ChatConversation c
            join fetch c.participants participants
            join c.participants member
            where member.id = :userId
            """)
    java.util.List<ChatConversation> findAllByParticipantIdWithParticipants(@Param("userId") Long userId);
}
