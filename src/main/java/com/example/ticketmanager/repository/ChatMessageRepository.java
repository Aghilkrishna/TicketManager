package com.example.ticketmanager.repository;

import com.example.ticketmanager.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop100ByConversationIdOrderByCreatedAtAsc(Long conversationId);

    List<ChatMessage> findByConversationIdOrderByCreatedAtDesc(Long conversationId);

    long countByConversationIdAndSenderIdNotAndReadAtIsNull(Long conversationId, Long senderId);

    List<ChatMessage> findByConversationIdAndSenderIdNotAndReadAtIsNull(Long conversationId, Long senderId);

    List<ChatMessage> findByConversationIdInOrderByCreatedAtDesc(List<Long> conversationIds);

    @Query("""
            select m.conversation.id, count(m)
            from ChatMessage m
            where m.conversation.id in :conversationIds
              and m.sender.id <> :userId
              and m.readAt is null
            group by m.conversation.id
            """)
    List<Object[]> countUnreadByConversationIds(@Param("conversationIds") List<Long> conversationIds,
                                                @Param("userId") Long userId);
}
