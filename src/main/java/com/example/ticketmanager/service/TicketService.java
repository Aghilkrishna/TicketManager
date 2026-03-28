package com.example.ticketmanager.service;

import com.example.ticketmanager.dto.AuthDtos;
import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.NotificationType;
import com.example.ticketmanager.entity.Ticket;
import com.example.ticketmanager.entity.TicketAttachment;
import com.example.ticketmanager.entity.TicketComment;
import com.example.ticketmanager.entity.TicketPriority;
import com.example.ticketmanager.entity.TicketServiceType;
import com.example.ticketmanager.entity.TicketStatus;
import com.example.ticketmanager.exception.AppException;
import com.example.ticketmanager.repository.TicketCommentRepository;
import com.example.ticketmanager.repository.TicketRepository;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketRepository ticketRepository;
    private final TicketCommentRepository commentRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final com.example.ticketmanager.config.AppProperties appProperties;

    @PreAuthorize("hasAuthority('FEATURE_TICKETS_MANAGE')")
    @Transactional
    public AuthDtos.TicketSummary create(String creatorUsername, AuthDtos.TicketRequest request, MultipartFile[] files) {
        AppUser creator = userService.getByUsername(creatorUsername);
        Ticket ticket = new Ticket();
        applyRequest(ticket, request, creator);
        storeFiles(ticket, files);
        Ticket saved = ticketRepository.save(ticket);
        notifyStakeholders(saved, "Ticket created: " + saved.getTitle(), NotificationType.TICKET_UPDATED);
        return toSummary(saved);
    }

    @PreAuthorize("hasAuthority('FEATURE_TICKETS_MANAGE')")
    @Transactional
    public AuthDtos.TicketSummary update(Long ticketId, AuthDtos.TicketRequest request, MultipartFile[] files) {
        Ticket ticket = getTicket(ticketId);
        applyRequest(ticket, request, ticket.getCreatedBy());
        ticket.setUpdatedAt(LocalDateTime.now());
        storeFiles(ticket, files);
        Ticket saved = ticketRepository.save(ticket);
        notifyStakeholders(saved, "Ticket updated: " + saved.getTitle(), NotificationType.TICKET_UPDATED);
        return toSummary(saved);
    }

    @Transactional
    public void delete(Long ticketId, String username) {
        Ticket ticket = getTicket(ticketId);
        if (!canManageAllTickets(username) && !canAccess(ticket, username)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not allowed to delete this ticket");
        }
        ticketRepository.delete(ticket);
    }

    @Transactional(readOnly = true)
    public Page<AuthDtos.TicketSummary> list(String username, boolean adminScope, boolean assignedOnly, String status, String priority,
                                             Long assignedToId, String search, int page, int size,
                                             String sortBy, String direction) {
        AppUser user = userService.getByUsername(username);
        Sort sort = Sort.by("desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC,
                sortBy == null || sortBy.isBlank() ? "updatedAt" : sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        TicketStatus statusFilter = status == null || status.isBlank() ? null : TicketStatus.valueOf(status);
        TicketPriority priorityFilter = priority == null || priority.isBlank() ? null : TicketPriority.valueOf(priority);
        String searchFilter = search == null || search.isBlank() ? null : search;

        if (!adminScope) {
            List<Ticket> tickets = assignedOnly
                    ? ticketRepository.findAssignedTicketsForUser(user.getId())
                    : ticketRepository.findVisibleTicketsForUser(user.getId());
            List<Ticket> filtered = tickets.stream()
                    .filter(ticket -> statusFilter == null || ticket.getStatus() == statusFilter)
                    .filter(ticket -> priorityFilter == null || ticket.getPriority() == priorityFilter)
                    .filter(ticket -> assignedToId == null || (ticket.getAssignedTo() != null && assignedToId.equals(ticket.getAssignedTo().getId())))
                    .filter(ticket -> matchesSearch(ticket, searchFilter))
                    .sorted(ticketComparator(sortBy, direction))
                    .toList();
            int start = Math.min(page * size, filtered.size());
            int end = Math.min(start + size, filtered.size());
            List<AuthDtos.TicketSummary> content = filtered.subList(start, end).stream().map(this::toSummary).toList();
            return new PageImpl<>(content, pageable, filtered.size());
        }

        Specification<Ticket> spec = Specification.where(null);
        if (statusFilter != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), statusFilter));
        }
        if (priorityFilter != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("priority"), priorityFilter));
        }
        if (assignedToId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("assignedTo").get("id"), assignedToId));
        }
        if (searchFilter != null) {
            String like = "%" + searchFilter.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("id").as(String.class)), like),
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("description")), like),
                    cb.like(cb.lower(root.get("address")), like),
                    cb.like(cb.lower(root.get("locationLink")), like),
                    cb.like(cb.lower(root.get("serviceType").as(String.class)), like),
                    cb.like(cb.lower(root.join("createdBy", JoinType.LEFT).get("username")), like),
                    cb.like(cb.lower(root.join("assignedTo", JoinType.LEFT).get("username")), like),
                    cb.like(cb.lower(root.join("serviceUsers", JoinType.LEFT).get("username")), like)
            ));
        }
        return ticketRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> metrics(String username, boolean adminScope) {
        Specification<Ticket> scope = Specification.where(null);
        if (!adminScope) {
            AppUser user = userService.getByUsername(username);
            scope = scope.and(scopeForUser(user.getId(), false));
        }
        return Map.of(
                "open", countByStatus(scope, TicketStatus.OPEN),
                "inProgress", countByStatus(scope, TicketStatus.IN_PROGRESS),
                "pending", countByStatus(scope, TicketStatus.ON_HOLD),
                "resolved", countByStatus(scope, TicketStatus.RESOLVED),
                "closed", countByStatus(scope, TicketStatus.CLOSED)
        );
    }

    @Transactional(readOnly = true)
    public AuthDtos.TicketSummary get(Long ticketId, String username, boolean adminScope) {
        Ticket ticket = getTicket(ticketId);
        if (!adminScope && !canManageAllTickets(username) && !canAccess(ticket, username)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not allowed to view this ticket");
        }
        return toSummary(ticket);
    }

    @Transactional
    public AuthDtos.TicketCommentResponse addComment(Long ticketId, String username, AuthDtos.TicketCommentRequest request) {
        Ticket ticket = getTicket(ticketId);
        if (!canAccess(ticket, username)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not allowed to comment on this ticket");
        }
        TicketComment comment = new TicketComment();
        comment.setTicket(ticket);
        comment.setAuthor(userService.getByUsername(username));
        comment.setContent(request.content());
        if (request.parentId() != null) {
            TicketComment parent = commentRepository.findById(request.parentId())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Parent comment not found"));
            comment.setParent(parent);
        }
        TicketComment saved = commentRepository.save(comment);
        notifyStakeholders(ticket, "New comment on ticket: " + ticket.getTitle(), NotificationType.COMMENT_ADDED);
        if (saved.getParent() != null && !saved.getParent().getAuthor().getId().equals(saved.getAuthor().getId())) {
            notificationService.notify(saved.getParent().getAuthor(), NotificationType.COMMENT_ADDED,
                    "New reply on ticket: " + ticket.getTitle(), "TICKET", ticket.getId());
        }
        return toCommentResponse(saved, username, List.of());
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.TicketCommentResponse> listComments(Long ticketId, String username, boolean adminScope) {
        Ticket ticket = getTicket(ticketId);
        if (!adminScope && !canAccess(ticket, username)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not allowed to view comments");
        }
        List<TicketComment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        Map<Long, List<TicketComment>> repliesByParentId = comments.stream()
                .filter(comment -> comment.getParent() != null)
                .collect(java.util.stream.Collectors.groupingBy(comment -> comment.getParent().getId()));
        return comments.stream()
                .filter(comment -> comment.getParent() == null)
                .map(comment -> toCommentResponse(comment, username, buildReplies(comment.getId(), repliesByParentId, username)))
                .toList();
    }

    @Transactional
    public AuthDtos.TicketCommentResponse updateComment(Long ticketId, Long commentId, String username, AuthDtos.TicketCommentUpdateRequest request) {
        TicketComment comment = getComment(ticketId, commentId);
        if (!comment.getAuthor().getUsername().equals(username)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You can edit only your own comments");
        }
        comment.setContent(request.content());
        TicketComment saved = commentRepository.save(comment);
        return toCommentResponse(saved, username, List.of());
    }

    @Transactional
    public void deleteComment(Long ticketId, Long commentId, String username) {
        TicketComment comment = getComment(ticketId, commentId);
        if (!comment.getAuthor().getUsername().equals(username)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You can delete only your own comments");
        }
        commentRepository.delete(comment);
    }

    public long countAll() {
        return ticketRepository.count();
    }

    private long countByStatus(Specification<Ticket> scope, TicketStatus status) {
        return ticketRepository.count(scope.and((root, query, cb) -> cb.equal(root.get("status"), status)));
    }

    private boolean matchesSearch(Ticket ticket, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String value = search.toLowerCase();
        return String.valueOf(ticket.getId()).contains(value)
                || ticket.getTitle().toLowerCase().contains(value)
                || ticket.getDescription().toLowerCase().contains(value)
                || (ticket.getAddress() != null && ticket.getAddress().toLowerCase().contains(value))
                || (ticket.getLocationLink() != null && ticket.getLocationLink().toLowerCase().contains(value))
                || (ticket.getServiceType() != null && ticket.getServiceType().name().toLowerCase().contains(value))
                || ticket.getStatus().name().toLowerCase().contains(value)
                || ticket.getPriority().name().toLowerCase().contains(value)
                || ticket.getCreatedBy().getUsername().toLowerCase().contains(value)
                || (ticket.getAssignedTo() != null && ticket.getAssignedTo().getUsername().toLowerCase().contains(value))
                || ticket.getServiceUsers().stream().anyMatch(user -> user.getUsername().toLowerCase().contains(value));
    }

    private Comparator<Ticket> ticketComparator(String sortBy, String direction) {
        Comparator<Ticket> comparator = switch (sortBy == null ? "updatedAt" : sortBy) {
            case "id" -> Comparator.comparing(Ticket::getId, Comparator.nullsLast(Long::compareTo));
            case "title" -> Comparator.comparing(ticket -> ticket.getTitle().toLowerCase(), Comparator.nullsLast(String::compareTo));
            case "serviceType" -> Comparator.comparing(ticket -> ticket.getServiceType() == null ? null : ticket.getServiceType().name(), Comparator.nullsLast(String::compareTo));
            case "createdBy" -> Comparator.comparing(ticket -> ticket.getCreatedBy().getUsername().toLowerCase(), Comparator.nullsLast(String::compareTo));
            case "status" -> Comparator.comparing(ticket -> ticket.getStatus().name(), Comparator.nullsLast(String::compareTo));
            case "priority" -> Comparator.comparing(ticket -> ticket.getPriority().name(), Comparator.nullsLast(String::compareTo));
            case "createdAt" -> Comparator.comparing(Ticket::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
            case "scheduleDate" -> Comparator.comparing(Ticket::getScheduleDate, Comparator.nullsLast(java.time.LocalDate::compareTo));
            default -> Comparator.comparing(Ticket::getUpdatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
        };
        return "asc".equalsIgnoreCase(direction) ? comparator : comparator.reversed();
    }

    private Specification<Ticket> scopeForUser(Long userId, boolean assignedOnly) {
        return (root, query, cb) -> {
            query.distinct(true);
            var serviceUsers = root.join("serviceUsers", JoinType.LEFT);
            if (assignedOnly) {
                return cb.or(
                        cb.equal(root.get("assignedTo").get("id"), userId),
                        cb.equal(serviceUsers.get("id"), userId)
                );
            }
            return cb.or(
                    cb.equal(root.get("createdBy").get("id"), userId),
                    cb.equal(root.get("assignedTo").get("id"), userId),
                    cb.equal(serviceUsers.get("id"), userId)
            );
        };
    }

    private Ticket getTicket(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Ticket not found"));
    }

    private TicketComment getComment(Long ticketId, Long commentId) {
        TicketComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Comment not found"));
        if (!comment.getTicket().getId().equals(ticketId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Comment does not belong to the ticket");
        }
        return comment;
    }

    private boolean canAccess(Ticket ticket, String username) {
        return ticket.getCreatedBy().getUsername().equals(username)
                || (ticket.getAssignedTo() != null && ticket.getAssignedTo().getUsername().equals(username))
                || ticket.getServiceUsers().stream().anyMatch(user -> user.getUsername().equals(username));
    }

    private boolean canManageAllTickets(String username) {
        AppUser user = userService.getByUsername(username);
        return user.getRoles().stream().anyMatch(role -> role.isActive() && "ROLE_ADMIN".equals(role.getName()));
    }

    private void applyRequest(Ticket ticket, AuthDtos.TicketRequest request, AppUser creator) {
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setAddress(request.address() == null || request.address().isBlank() ? null : request.address().trim());
        ticket.setServiceType(request.serviceType() == null || request.serviceType().isBlank()
                ? null : TicketServiceType.valueOf(request.serviceType()));
        ticket.setLocationLink(request.locationLink() == null || request.locationLink().isBlank()
                ? null : request.locationLink().trim());
        ticket.setScheduleDate(request.scheduleDate());
        ticket.setPriority(request.priority() == null || request.priority().isBlank()
                ? TicketPriority.MEDIUM : TicketPriority.valueOf(request.priority()));
        ticket.setStatus(request.status() == null || request.status().isBlank()
                ? TicketStatus.OPEN : TicketStatus.valueOf(request.status()));
        ticket.setCreatedBy(creator);
        ticket.setAssignedTo(request.assignedToId() == null ? null : userService.getById(request.assignedToId()));
        Set<AppUser> serviceUsers = new HashSet<>();
        if (request.serviceUserIds() != null) {
            request.serviceUserIds().forEach(id -> serviceUsers.add(userService.getById(id)));
        }
        ticket.setServiceUsers(serviceUsers);
    }

    private void storeFiles(Ticket ticket, MultipartFile[] files) {
        if (files == null) {
            return;
        }
        Path uploadDir = Path.of(appProperties.uploadDir());
        try {
            Files.createDirectories(uploadDir);
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String stored = UUID.randomUUID() + "-" + file.getOriginalFilename();
                Files.copy(file.getInputStream(), uploadDir.resolve(stored), StandardCopyOption.REPLACE_EXISTING);
                TicketAttachment attachment = new TicketAttachment();
                attachment.setTicket(ticket);
                attachment.setOriginalFileName(file.getOriginalFilename());
                attachment.setStoredFileName(stored);
                attachment.setContentType(file.getContentType());
                attachment.setFileSize(file.getSize());
                ticket.getAttachments().add(attachment);
            }
        } catch (IOException ex) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store attachment");
        }
    }

    private void notifyStakeholders(Ticket ticket, String message, NotificationType type) {
        Set<AppUser> recipients = new HashSet<>(ticket.getServiceUsers());
        recipients.add(ticket.getCreatedBy());
        if (ticket.getAssignedTo() != null) {
            recipients.add(ticket.getAssignedTo());
        }
        recipients.forEach(user -> notificationService.notify(user, type, message, "TICKET", ticket.getId()));
    }

    public AuthDtos.TicketSummary toSummary(Ticket ticket) {
        return new AuthDtos.TicketSummary(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getAddress(),
                ticket.getServiceType() == null ? null : ticket.getServiceType().name(),
                ticket.getServiceType() == null ? null : ticket.getServiceType().label(),
                ticket.getLocationLink(),
                ticket.getStatus().name(),
                ticket.getPriority().name(),
                ticket.getScheduleDate(),
                ticket.getCreatedBy().getUsername(),
                ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getId(),
                ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getUsername(),
                ticket.getServiceUsers().stream().map(AppUser::getId).collect(java.util.stream.Collectors.toSet()),
                ticket.getServiceUsers().stream().map(AppUser::getUsername).collect(java.util.stream.Collectors.toSet()),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getAttachments().stream().map(TicketAttachment::getOriginalFileName).toList()
        );
    }

    private List<AuthDtos.TicketCommentResponse> buildReplies(Long parentId, Map<Long, List<TicketComment>> repliesByParentId, String username) {
        return repliesByParentId.getOrDefault(parentId, List.of()).stream()
                .map(reply -> toCommentResponse(reply, username, buildReplies(reply.getId(), repliesByParentId, username)))
                .toList();
    }

    private AuthDtos.TicketCommentResponse toCommentResponse(TicketComment comment, String username, List<AuthDtos.TicketCommentResponse> replies) {
        return new AuthDtos.TicketCommentResponse(
                comment.getId(),
                comment.getParent() == null ? null : comment.getParent().getId(),
                comment.getAuthor().getUsername(),
                comment.getContent(),
                comment.getAuthor().getUsername().equals(username),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                replies
        );
    }
}
