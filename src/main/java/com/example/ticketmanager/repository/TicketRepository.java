package com.example.ticketmanager.repository;

import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.Ticket;
import com.example.ticketmanager.entity.TicketPriority;
import com.example.ticketmanager.entity.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {
    Page<Ticket> findByCreatedByOrAssignedToOrServiceUsersContains(AppUser createdBy, AppUser assignedTo, AppUser serviceUser, Pageable pageable);

    @Query(
            value = """
                    select distinct t from Ticket t
                    left join t.assignedTo assignedUser
                    left join t.serviceUsers su
                    where (assignedUser.id = :userId or su.id = :userId)
                      and (:status is null or t.status = :status)
                      and (:priority is null or t.priority = :priority)
                      and (:assignedToId is null or assignedUser.id = :assignedToId)
                      and (:search is null or lower(str(t.id)) like concat('%', lower(cast(:search as string)), '%')
                           or lower(t.title) like concat('%', lower(cast(:search as string)), '%')
                           or lower(t.description) like concat('%', lower(cast(:search as string)), '%'))
                    """,
            countQuery = """
                    select count(distinct t.id) from Ticket t
                    left join t.assignedTo assignedUser
                    left join t.serviceUsers su
                    where (assignedUser.id = :userId or su.id = :userId)
                      and (:status is null or t.status = :status)
                      and (:priority is null or t.priority = :priority)
                      and (:assignedToId is null or assignedUser.id = :assignedToId)
                      and (:search is null or lower(str(t.id)) like concat('%', lower(cast(:search as string)), '%')
                           or lower(t.title) like concat('%', lower(cast(:search as string)), '%')
                           or lower(assignedUser.username) like concat('%', lower(cast(:search as string)), '%')
                           or lower(su.username) like concat('%', lower(cast(:search as string)), '%')
                           or lower(t.description) like concat('%', lower(cast(:search as string)), '%'))
                    """
    )
    @EntityGraph(attributePaths = {"createdBy", "assignedTo", "serviceUsers"})
    Page<Ticket> findAssignedTicketsForUser(
            @Param("userId") Long userId,
            @Param("status") TicketStatus status,
            @Param("priority") TicketPriority priority,
            @Param("assignedToId") Long assignedToId,
            @Param("search") String search,
            Pageable pageable
    );

    @Query(
            value = """
                    select distinct t from Ticket t
                    left join t.createdBy createdUser
                    left join t.assignedTo assignedUser
                    left join t.serviceUsers su
                    where (createdUser.id = :userId or assignedUser.id = :userId or su.id = :userId)
                      and (:status is null or t.status = :status)
                      and (:priority is null or t.priority = :priority)
                      and (:assignedToId is null or assignedUser.id = :assignedToId)
                      and (:search is null or lower(str(t.id)) like concat('%', lower(cast(:search as string)), '%')
                           or lower(t.title) like concat('%', lower(cast(:search as string)), '%')
                           or lower(assignedUser.username) like concat('%', lower(cast(:search as string)), '%')
                           or lower(su.username) like concat('%', lower(cast(:search as string)), '%')
                           or lower(t.description) like concat('%', lower(cast(:search as string)), '%'))
                    """,
            countQuery = """
                    select count(distinct t.id) from Ticket t
                    left join t.createdBy createdUser
                    left join t.assignedTo assignedUser
                    left join t.serviceUsers su
                    where (createdUser.id = :userId or assignedUser.id = :userId or su.id = :userId)
                      and (:status is null or t.status = :status)
                      and (:priority is null or t.priority = :priority)
                      and (:assignedToId is null or assignedUser.id = :assignedToId)
                      and (:search is null or lower(str(t.id)) like concat('%', lower(cast(:search as string)), '%')
                           or lower(t.title) like concat('%', lower(cast(:search as string)), '%')
                           or lower(assignedUser.username) like concat('%', lower(cast(:search as string)), '%')
                           or lower(su.username) like concat('%', lower(cast(:search as string)), '%')
                           or lower(t.description) like concat('%', lower(cast(:search as string)), '%'))
                    """
    )
    @EntityGraph(attributePaths = {"createdBy", "assignedTo", "serviceUsers"})
    Page<Ticket> findVisibleTicketsForUser(
            @Param("userId") Long userId,
            @Param("status") TicketStatus status,
            @Param("priority") TicketPriority priority,
            @Param("assignedToId") Long assignedToId,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
            select distinct t from Ticket t
            left join t.assignedTo assignedUser
            left join t.serviceUsers su
            where (assignedUser.id = :userId or su.id = :userId)
            """)
    java.util.List<Ticket> findAssignedTicketsForUser(@Param("userId") Long userId);

    @Query("""
            select distinct t from Ticket t
            left join t.createdBy createdUser
            left join t.assignedTo assignedUser
            left join t.serviceUsers su
            where (createdUser.id = :userId or assignedUser.id = :userId or su.id = :userId)
            """)
    java.util.List<Ticket> findVisibleTicketsForUser(@Param("userId") Long userId);

    @Query(
            value = """
                    select distinct t from Ticket t
                    left join t.createdBy createdUser
                    left join t.assignedTo assignedUser
                    left join t.serviceUsers su
                    where (:status is null or t.status = :status)
                      and (:priority is null or t.priority = :priority)
                      and (:assignedToId is null or assignedUser.id = :assignedToId)
                      and (:search is null or lower(str(t.id)) like concat('%', lower(cast(:search as string)), '%')
                           or lower(t.title) like concat('%', lower(cast(:search as string)), '%')
                           or lower(createdUser.username) like concat('%', lower(cast(:search as string)), '%')
                           or lower(assignedUser.username) like concat('%', lower(cast(:search as string)), '%')
                           or lower(su.username) like concat('%', lower(cast(:search as string)), '%')
                           or lower(t.description) like concat('%', lower(cast(:search as string)), '%'))
                    """,
            countQuery = """
                    select count(distinct t.id) from Ticket t
                    left join t.createdBy createdUser
                    left join t.assignedTo assignedUser
                    left join t.serviceUsers su
                    where (:status is null or t.status = :status)
                      and (:priority is null or t.priority = :priority)
                      and (:assignedToId is null or assignedUser.id = :assignedToId)
                      and (:search is null or lower(str(t.id)) like concat('%', lower(cast(:search as string)), '%')
                           or lower(t.title) like concat('%', lower(cast(:search as string)), '%')
                           or lower(createdUser.username) like concat('%', lower(cast(:search as string)), '%')
                           or lower(assignedUser.username) like concat('%', lower(cast(:search as string)), '%')
                           or lower(su.username) like concat('%', lower(cast(:search as string)), '%')
                           or lower(t.description) like concat('%', lower(cast(:search as string)), '%'))
                    """
    )
    @EntityGraph(attributePaths = {"createdBy", "assignedTo", "serviceUsers"})
    Page<Ticket> findAllTicketsForAdmin(
            @Param("status") TicketStatus status,
            @Param("priority") TicketPriority priority,
            @Param("assignedToId") Long assignedToId,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
            select distinct t from Ticket t
            left join t.createdBy createdUser
            left join t.assignedTo assignedUser
            left join t.serviceUsers su
            where (createdUser.id = :userId or assignedUser.id = :userId or su.id = :userId)
              and (:excludeTicketId is null or t.id <> :excludeTicketId)
              and (
                    lower(str(t.id)) like concat('%', lower(cast(:search as string)), '%')
                    or lower(t.title) like concat('%', lower(cast(:search as string)), '%')
                    or lower(coalesce(t.customerName, '')) like concat('%', lower(cast(:search as string)), '%')
                  )
            order by t.updatedAt desc
            """)
    @EntityGraph(attributePaths = {"createdBy", "assignedTo"})
    List<Ticket> searchVisibleParentTicketCandidates(
            @Param("userId") Long userId,
            @Param("search") String search,
            @Param("excludeTicketId") Long excludeTicketId,
            Pageable pageable
    );

    @Query("""
            select distinct t from Ticket t
            left join t.createdBy createdUser
            where (:excludeTicketId is null or t.id <> :excludeTicketId)
              and (
                    lower(str(t.id)) like concat('%', lower(cast(:search as string)), '%')
                    or lower(t.title) like concat('%', lower(cast(:search as string)), '%')
                    or lower(coalesce(t.customerName, '')) like concat('%', lower(cast(:search as string)), '%')
                    or lower(coalesce(createdUser.username, '')) like concat('%', lower(cast(:search as string)), '%')
                  )
            order by t.updatedAt desc
            """)
    @EntityGraph(attributePaths = {"createdBy", "assignedTo"})
    List<Ticket> searchAllParentTicketCandidates(
            @Param("search") String search,
            @Param("excludeTicketId") Long excludeTicketId,
            Pageable pageable
    );

}
