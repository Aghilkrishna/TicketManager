package com.example.ticketmanager.service;

import com.example.ticketmanager.dto.AuthDtos;
import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.EmailNotificationAction;
import com.example.ticketmanager.entity.NotificationType;
import com.example.ticketmanager.entity.Ticket;
import com.example.ticketmanager.entity.TicketAttachment;
import com.example.ticketmanager.entity.TicketComment;
import com.example.ticketmanager.entity.TicketPricingModel;
import com.example.ticketmanager.entity.TicketPriority;
import com.example.ticketmanager.entity.TicketServiceType;
import com.example.ticketmanager.entity.TicketSiteVisit;
import com.example.ticketmanager.entity.TicketStatus;
import com.example.ticketmanager.exception.AppException;
import com.example.ticketmanager.repository.TicketCommentRepository;
import com.example.ticketmanager.repository.TicketRepository;
import com.example.ticketmanager.repository.TicketSiteVisitRepository;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final TicketSiteVisitRepository ticketSiteVisitRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final com.example.ticketmanager.config.AppProperties appProperties;
    private final SimpMessagingTemplate messagingTemplate;

    @PreAuthorize("hasAuthority('FEATURE_TICKETS_MANAGE')")
    @Transactional
    public AuthDtos.TicketSummary create(String creatorUsername, AuthDtos.TicketRequest request, MultipartFile[] files) {
        AppUser creator = userService.getByUsername(creatorUsername);
        Ticket ticket = new Ticket();
        applyRequest(ticket, request, creator, creatorUsername, true);
        storeFiles(ticket, files);
        Ticket saved = ticketRepository.save(ticket);
        addInitialComment(saved, creatorUsername, request.initialComment());
        notifyStakeholders(saved, "Ticket created: " + saved.getTitle(), NotificationType.TICKET_UPDATED, EmailNotificationAction.TICKET_CREATED);
        return toSummary(saved);
    }

    @PreAuthorize("hasAnyAuthority('FEATURE_TICKETS_MANAGE','FEATURE_SITE_VISIT_EDIT')")
    @Transactional
    public AuthDtos.TicketSummary update(Long ticketId, String actorUsername, AuthDtos.TicketRequest request, MultipartFile[] files) {
        Ticket ticket = getTicket(ticketId);
        ensureCanUpdate(ticket, userService.getByUsername(actorUsername));
        applyRequest(ticket, request, ticket.getCreatedBy(), actorUsername, false);
        ticket.setUpdatedAt(LocalDateTime.now());
        storeFiles(ticket, files);
        Ticket saved = ticketRepository.save(ticket);
        notifyStakeholders(saved, "Ticket updated: " + saved.getTitle(), NotificationType.TICKET_UPDATED, EmailNotificationAction.TICKET_UPDATED);
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
            List<AuthDtos.TicketSummary> content = filtered.subList(start, end).stream().map(ticket -> toSummary(ticket, username)).toList();
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
        return ticketRepository.findAll(spec, pageable).map(ticket -> toSummary(ticket, username));
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
        return toSummary(ticket, username);
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
        notifyStakeholders(ticket, "New comment on ticket: " + ticket.getTitle(), NotificationType.COMMENT_ADDED, EmailNotificationAction.COMMENT_ADDED);
        if (saved.getParent() != null && !saved.getParent().getAuthor().getId().equals(saved.getAuthor().getId())) {
            notificationService.notify(saved.getParent().getAuthor(), NotificationType.COMMENT_ADDED,
                    "New reply on ticket: " + ticket.getTitle(), "TICKET", ticket.getId(), EmailNotificationAction.COMMENT_ADDED);
        }
        publishCommentEvent(ticket, "ADDED", saved.getId());
        return toCommentResponse(saved, username, List.of());
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.TicketCommentResponse> listComments(Long ticketId, String username, boolean adminScope) {
        Ticket ticket = getTicket(ticketId);
        if (!adminScope && !canAccess(ticket, username)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not allowed to view comments");
        }
        List<TicketComment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        if (isVendorRestrictedView(ticket, username)) {
            comments = comments.stream()
                    .filter(comment -> comment.getAuthor().getUsername().equals(username))
                    .toList();
        }
        Map<Long, List<TicketComment>> repliesByParentId = comments.stream()
                .filter(comment -> comment.getParent() != null)
                .collect(java.util.stream.Collectors.groupingBy(comment -> comment.getParent().getId()));
        return comments.stream()
                .filter(comment -> comment.getParent() == null)
                .map(comment -> toCommentResponse(comment, username, buildReplies(comment.getId(), repliesByParentId, username)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.TicketSiteVisitResponse> listSiteVisits(Long ticketId, String username, boolean adminScope) {
        Ticket ticket = getTicket(ticketId);
        if (!adminScope && !canAccess(ticket, username)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not allowed to view site visit history");
        }
        return ticketSiteVisitRepository.findByTicketIdOrderByVisitedAtDesc(ticketId).stream()
                .map(this::toSiteVisitResponse)
                .toList();
    }

    @Transactional
    public AuthDtos.TicketSiteVisitResponse addSiteVisit(Long ticketId, String username, AuthDtos.TicketSiteVisitRequest request) {
        Ticket ticket = getTicket(ticketId);
        AppUser actor = userService.getByUsername(username);
        if (!userService.hasRole(actor, "ROLE_AGENT")) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only agent users can add site visit history");
        }
        if (!canAccess(ticket, username)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not allowed to update site visit history for this ticket");
        }
        TicketSiteVisit siteVisit = new TicketSiteVisit();
        siteVisit.setTicket(ticket);
        siteVisit.setAgent(actor);
        siteVisit.setVisitedAt(request.visitedAt());
        siteVisit.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        TicketSiteVisit saved = ticketSiteVisitRepository.save(siteVisit);
        ticket.setSiteVisits((ticket.getSiteVisits() == null ? 0 : ticket.getSiteVisits()) + 1);
        ticket.setUpdatedBy(actor);
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketRepository.save(ticket);
        notifyStakeholders(ticket, "Site visit logged for ticket: " + ticket.getTitle(), NotificationType.TICKET_UPDATED, EmailNotificationAction.SITE_VISIT_ADDED);
        return toSiteVisitResponse(saved);
    }

    @Transactional
    public AuthDtos.TicketCommentResponse updateComment(Long ticketId, Long commentId, String username, AuthDtos.TicketCommentUpdateRequest request) {
        TicketComment comment = getComment(ticketId, commentId);
        if (!comment.getAuthor().getUsername().equals(username)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You can edit only your own comments");
        }
        comment.setContent(request.content());
        TicketComment saved = commentRepository.save(comment);
        publishCommentEvent(saved.getTicket(), "UPDATED", saved.getId());
        return toCommentResponse(saved, username, List.of());
    }

    @Transactional
    public void deleteComment(Long ticketId, Long commentId, String username) {
        TicketComment comment = getComment(ticketId, commentId);
        if (!comment.getAuthor().getUsername().equals(username)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You can delete only your own comments");
        }
        Ticket ticket = comment.getTicket();
        Long deletedCommentId = comment.getId();
        commentRepository.delete(comment);
        publishCommentEvent(ticket, "DELETED", deletedCommentId);
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
                || (ticket.getCustomerName() != null && ticket.getCustomerName().toLowerCase().contains(value))
                || (ticket.getCustomerCity() != null && ticket.getCustomerCity().toLowerCase().contains(value))
                || (ticket.getVendorUser() != null && ticket.getVendorUser().getUsername().toLowerCase().contains(value))
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
        return canManageAllTickets(username)
                || ticket.getCreatedBy().getUsername().equals(username)
                || (ticket.getAssignedTo() != null && ticket.getAssignedTo().getUsername().equals(username))
                || ticket.getServiceUsers().stream().anyMatch(user -> user.getUsername().equals(username));
    }

    private boolean canManageAllTickets(String username) {
        AppUser user = userService.getByUsername(username);
        return user.getRoles().stream().anyMatch(role -> role.isActive()
                && ("ROLE_ADMIN".equals(role.getName()) || "ROLE_MANAGER".equals(role.getName())));
    }

    private void applyRequest(Ticket ticket, AuthDtos.TicketRequest request, AppUser creator, String actorUsername, boolean isCreate) {
        AppUser actor = userService.getByUsername(actorUsername);
        boolean vendorActor = userService.hasRole(actor, "ROLE_VENDOR");
        boolean agentActor = userService.hasRole(actor, "ROLE_AGENT");
        if (agentActor && !isCreate) {
            applyAgentSiteVisitUpdate(ticket, request, actor);
            return;
        }
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setAddress(request.address() == null || request.address().isBlank() ? null : request.address().trim());
        ticket.setServiceType(request.serviceType() == null || request.serviceType().isBlank()
                ? null : TicketServiceType.valueOf(request.serviceType()));
        ticket.setLocationLink(request.locationLink() == null || request.locationLink().isBlank()
                ? null : request.locationLink().trim());
        if (isCreate) {
            ticket.setSiteVisits(0);
        } else if (agentActor && request.siteVisits() != null) {
            if (request.siteVisits() < 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Site visits cannot be negative");
            }
            ticket.setSiteVisits(request.siteVisits());
        }
        ticket.setParentTicket(request.parentTicketId() == null ? null : getTicket(request.parentTicketId()));
        ticket.setVendorNotes(request.vendorNotes() == null || request.vendorNotes().isBlank() ? null : request.vendorNotes().trim());
        ticket.setCustomerName(request.customerName() == null || request.customerName().isBlank() ? null : request.customerName().trim());
        ticket.setCustomerFlat(request.customerFlat() == null || request.customerFlat().isBlank() ? null : request.customerFlat().trim());
        ticket.setCustomerStreet(request.customerStreet() == null || request.customerStreet().isBlank() ? null : request.customerStreet().trim());
        ticket.setCustomerCity(request.customerCity() == null || request.customerCity().isBlank() ? null : request.customerCity().trim());
        ticket.setCustomerState(request.customerState() == null || request.customerState().isBlank() ? null : request.customerState().trim());
        ticket.setCustomerPincode(request.customerPincode() == null || request.customerPincode().isBlank() ? null : request.customerPincode().trim());
        ticket.setCustomerLocationLink(request.customerLocationLink() == null || request.customerLocationLink().isBlank() ? null : request.customerLocationLink().trim());
        ticket.setScheduleDate(request.scheduleDate());
        ticket.setPriority(request.priority() == null || request.priority().isBlank()
                ? TicketPriority.MEDIUM : TicketPriority.valueOf(request.priority()));
        ticket.setStatus(request.status() == null || request.status().isBlank()
                ? TicketStatus.OPEN : TicketStatus.valueOf(request.status()));
        ticket.setCreatedBy(creator);
        ticket.setUpdatedBy(actor);
        if (vendorActor) {
            ticket.setVendorUser(actor);
            if (isCreate) {
                ticket.setAssignedTo(null);
                ticket.setServiceUsers(new HashSet<>());
                ticket.setStatus(TicketStatus.OPEN);
            }
        } else {
            ticket.setVendorUser(resolveVendor(request.vendorUserId(), actor, vendorActor));
            AppUser assignedUser = request.assignedToId() == null ? null : userService.getById(request.assignedToId());
            if (assignedUser != null && userService.hasRole(assignedUser, "ROLE_VENDOR")) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Vendor users cannot be assigned as support agents");
            }
            ticket.setAssignedTo(assignedUser);

            Set<AppUser> serviceUsers = new HashSet<>();
            if (request.serviceUserIds() != null) {
                request.serviceUserIds().forEach(id -> {
                    AppUser user = userService.getById(id);
                    if (userService.hasRole(user, "ROLE_VENDOR")) {
                        throw new AppException(HttpStatus.BAD_REQUEST, "Vendor users cannot be added as service users");
                    }
                    serviceUsers.add(user);
                });
            }
            ticket.setServiceUsers(serviceUsers);
        }
        if (!userService.hasRole(actor, "ROLE_AGENT")) {
            ticket.setPricingModel(request.pricingModel() == null || request.pricingModel().isBlank()
                    ? null : TicketPricingModel.valueOf(request.pricingModel()));
            ticket.setEstimatedCost(request.estimatedCost());
            ticket.setActualCost(request.actualCost());
        }
        ticket.setAdditionalNotes(request.additionalNotes() == null || request.additionalNotes().isBlank() ? null : request.additionalNotes().trim());
    }

    private void applyAgentSiteVisitUpdate(Ticket ticket, AuthDtos.TicketRequest request, AppUser actor) {
        ticket.setUpdatedBy(actor);
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

    private void notifyStakeholders(Ticket ticket, String message, NotificationType type, EmailNotificationAction emailAction) {
        Set<AppUser> recipients = new HashSet<>(ticket.getServiceUsers());
        recipients.add(ticket.getCreatedBy());
        if (ticket.getAssignedTo() != null) {
            recipients.add(ticket.getAssignedTo());
        }
        if (ticket.getVendorUser() != null) {
            recipients.add(ticket.getVendorUser());
        }
        recipients.forEach(user -> notificationService.notify(user, type, message, "TICKET", ticket.getId(), emailAction));
    }

    private void publishCommentEvent(Ticket ticket, String action, Long commentId) {
        AuthDtos.TicketCommentEvent event = new AuthDtos.TicketCommentEvent(ticket.getId(), action, commentId);
        Set<AppUser> recipients = new HashSet<>(ticket.getServiceUsers());
        recipients.add(ticket.getCreatedBy());
        if (ticket.getAssignedTo() != null) {
            recipients.add(ticket.getAssignedTo());
        }
        if (ticket.getVendorUser() != null) {
            recipients.add(ticket.getVendorUser());
        }
        recipients.stream()
                .filter(user -> user != null && user.getUsername() != null)
                .forEach(user -> messagingTemplate.convertAndSendToUser(user.getUsername(), "/queue/ticket-comments", event));
    }

    public AuthDtos.TicketSummary toSummary(Ticket ticket) {
        return toSummary(ticket, null);
    }

    public AuthDtos.TicketSummary toSummary(Ticket ticket, String viewerUsername) {
        boolean canViewPrice = viewerUsername == null || !userService.hasRole(viewerUsername, "ROLE_AGENT");
        boolean vendorRestrictedView = viewerUsername != null && isVendorRestrictedView(ticket, viewerUsername);
        return new AuthDtos.TicketSummary(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getAddress(),
                ticket.getServiceType() == null ? null : ticket.getServiceType().name(),
                ticket.getServiceType() == null ? null : ticket.getServiceType().label(),
                ticket.getLocationLink(),
                ticket.getSiteVisits(),
                ticket.getParentTicket() == null ? null : ticket.getParentTicket().getId(),
                ticket.getParentTicket() == null ? null : ticket.getParentTicket().getTitle(),
                ticket.getVendorUser() == null ? null : ticket.getVendorUser().getId(),
                ticket.getVendorUser() == null ? null : ticket.getVendorUser().getUsername(),
                ticket.getVendorUser() == null || vendorRestrictedView ? null : ticket.getVendorUser().getEmail(),
                ticket.getVendorUser() == null || vendorRestrictedView ? null : ticket.getVendorUser().getPhone(),
                ticket.getVendorNotes(),
                ticket.getCustomerName(),
                ticket.getCustomerFlat(),
                ticket.getCustomerStreet(),
                ticket.getCustomerCity(),
                ticket.getCustomerState(),
                ticket.getCustomerPincode(),
                ticket.getCustomerLocationLink(),
                ticket.getPricingModel() == null ? null : ticket.getPricingModel().name(),
                ticket.getPricingModel() == null ? null : ticket.getPricingModel().label(),
                canViewPrice ? ticket.getEstimatedCost() : null,
                canViewPrice ? ticket.getActualCost() : null,
                ticket.getAdditionalNotes(),
                ticket.getStatus().name(),
                ticket.getPriority().name(),
                ticket.getScheduleDate(),
                ticket.getCreatedBy().getUsername(),
                ticket.getUpdatedBy() == null ? null : ticket.getUpdatedBy().getUsername(),
                ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getId(),
                vendorRestrictedView ? null : (ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getUsername()),
                ticket.getServiceUsers().stream().map(AppUser::getId).collect(java.util.stream.Collectors.toSet()),
                vendorRestrictedView ? Set.of() : ticket.getServiceUsers().stream().map(AppUser::getUsername).collect(java.util.stream.Collectors.toSet()),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getAttachments().stream().map(TicketAttachment::getOriginalFileName).toList()
        );
    }

    public List<AuthDtos.TicketSummary> searchVisibleTickets(String username, String query) {
        String value = query == null ? "" : query.trim().toLowerCase();
        List<Ticket> tickets = canManageAllTickets(username)
                ? ticketRepository.findAll()
                : ticketRepository.findVisibleTicketsForUser(userService.getByUsername(username).getId());
        return tickets.stream()
                .filter(ticket -> value.isBlank()
                        || String.valueOf(ticket.getId()).contains(value)
                        || ticket.getTitle().toLowerCase().contains(value)
                        || (ticket.getCustomerName() != null && ticket.getCustomerName().toLowerCase().contains(value)))
                .sorted(Comparator.comparing(Ticket::getUpdatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .limit(10)
                .map(ticket -> toSummary(ticket, username))
                .toList();
    }

    private void addInitialComment(Ticket ticket, String username, String commentText) {
        if (commentText == null || commentText.isBlank()) {
            return;
        }
        TicketComment comment = new TicketComment();
        comment.setTicket(ticket);
        comment.setAuthor(userService.getByUsername(username));
        comment.setContent(commentText.trim());
        commentRepository.save(comment);
    }

    private AppUser resolveVendor(Long vendorUserId, AppUser actor, boolean vendorActor) {
        if (vendorActor) {
            return actor;
        }
        if (vendorUserId == null) {
            return null;
        }
        AppUser vendor = userService.getById(vendorUserId);
        if (!userService.hasRole(vendor, "ROLE_VENDOR")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Selected vendor must have the Vendor role");
        }
        return vendor;
    }

    private void ensureCanUpdate(Ticket ticket, AppUser actor) {
        if (userService.hasRole(actor, "ROLE_VENDOR") && !ticket.getCreatedBy().getId().equals(actor.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Vendors can update only their own created tickets");
        }
    }

    private boolean isVendorRestrictedView(Ticket ticket, String username) {
        AppUser viewer = userService.getByUsername(username);
        return userService.hasRole(viewer, "ROLE_VENDOR") && ticket.getCreatedBy().getId().equals(viewer.getId());
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
                comment.getAuthor().getId(),
                comment.getAuthor().getUsername(),
                comment.getContent(),
                comment.getAuthor().getUsername().equals(username),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                replies
        );
    }

    private AuthDtos.TicketSiteVisitResponse toSiteVisitResponse(TicketSiteVisit siteVisit) {
        return new AuthDtos.TicketSiteVisitResponse(
                siteVisit.getId(),
                siteVisit.getAgent().getId(),
                siteVisit.getAgent().getUsername(),
                siteVisit.getVisitedAt(),
                siteVisit.getNotes()
        );
    }
}
