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
import java.util.Arrays;
import java.util.EnumSet;

@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketRepository ticketRepository;
    private final TicketCommentRepository commentRepository;
    private final TicketSiteVisitRepository ticketSiteVisitRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final EmailNotificationSettingsService emailNotificationSettingsService;
    private final com.example.ticketmanager.config.AppProperties appProperties;
    private final SimpMessagingTemplate messagingTemplate;

    @PreAuthorize("hasAnyAuthority('FEATURE_TICKETS_MANAGE','FEATURE_TICKETS_CREATE_STANDARD','FEATURE_TICKETS_CREATE_VENDOR')")
    @Transactional
    public AuthDtos.TicketSummary create(String creatorUsername, AuthDtos.TicketRequest request, MultipartFile[] files) {
        AppUser creator = userService.getByEmail(creatorUsername);
        Ticket ticket = new Ticket();
        applyRequest(ticket, request, creator, creatorUsername, true);
        storeFiles(ticket, files);
        Ticket saved = ticketRepository.save(ticket);
        addInitialComment(saved, creatorUsername, request.initialComment());
        publishTicketListRefreshEvent("CREATED", saved);
        notifyStakeholders(saved, "Ticket created: " + saved.getTitle(), NotificationType.TICKET_UPDATED, EmailNotificationAction.TICKET_CREATED);
        notifyAdditionalTicketAudiences(saved, "Ticket created: " + saved.getTitle(), NotificationType.TICKET_UPDATED);
        return toSummary(saved);
    }

    @PreAuthorize("hasAnyAuthority('FEATURE_TICKETS_MANAGE','FEATURE_SITE_VISIT_EDIT')")
    @Transactional
    public AuthDtos.TicketSummary update(Long ticketId, String actorUsername, AuthDtos.TicketRequest request, MultipartFile[] files) {
        Ticket ticket = getTicket(ticketId);
        ensureCanUpdate(ticket, userService.getByEmail(actorUsername));
        applyRequest(ticket, request, ticket.getCreatedBy(), actorUsername, false);
        ticket.setUpdatedAt(LocalDateTime.now());
        storeFiles(ticket, files);
        Ticket saved = ticketRepository.save(ticket);
        publishTicketListRefreshEvent("UPDATED", saved);
        notifyStakeholders(saved, "Ticket updated: " + saved.getTitle(), NotificationType.TICKET_UPDATED, EmailNotificationAction.TICKET_UPDATED);
        notifyAdditionalTicketAudiences(saved, "Ticket updated: " + saved.getTitle(), NotificationType.TICKET_UPDATED);
        return toSummary(saved);
    }

    @Transactional
    public void delete(Long ticketId, String username) {
        Ticket ticket = getTicket(ticketId);
        if (!canManageAllTickets(username) && !canAccess(ticket, username)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not allowed to delete this ticket");
        }
        publishTicketListRefreshEvent("DELETED", ticket);
        ticketRepository.delete(ticket);
    }

    @Transactional(readOnly = true)
    public Page<AuthDtos.TicketSummary> list(String username, boolean adminScope, boolean assignedOnly, boolean createdOnly, String status, String priority,
                                             Long assignedToId, Long vendorUserId, String search, int page, int size,
                                             String sortBy, String direction) {
        AppUser user = userService.getByEmail(username);
        // Use already-loaded user to avoid a second getByEmail inside canManageAllTickets
        boolean effectiveAdminScope = adminScope && userService.hasAuthority(user, "FEATURE_TICKETS_ALL_VIEW");
        Sort sort = Sort.by("desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC,
                mapSortProperty(sortBy));
        Pageable pageable = PageRequest.of(page, size, sort);
        Set<TicketStatus> statusFilter = parseStatusFilter(status);
        TicketPriority priorityFilter = priority == null || priority.isBlank() ? null : TicketPriority.valueOf(priority);
        String searchFilter = search == null || search.isBlank() ? null : search;

        Specification<Ticket> spec = Specification.where(buildScopeSpecification(user, effectiveAdminScope, assignedOnly, createdOnly))
                .and(statusesSpecification(statusFilter))
                .and(prioritySpecification(priorityFilter))
                .and(assignedToSpecification(assignedToId))
                .and(vendorSpecification(vendorUserId))
                .and(searchSpecification(searchFilter));

        // Pre-compute per-viewer constants once so the mapping lambda
        // does not call getByEmail() for every ticket in the page (N+1 fix).
        final boolean isVendorViewer = userService.hasRole(user, "ROLE_VENDOR");
        final Long viewerUserId = user.getId();

        return ticketRepository.findAll(spec, pageable)
                .map(ticket -> toListSummary(ticket, isVendorViewer, viewerUserId));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> metrics(String username, boolean adminScope) {
        boolean effectiveAdminScope = adminScope && canManageAllTickets(username);
        Specification<Ticket> scope = Specification.where(null);
        if (!effectiveAdminScope) {
            AppUser user = userService.getByEmail(username);
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
        boolean effectiveAdminScope = adminScope && canManageAllTickets(username);
        Ticket ticket = getTicket(ticketId);
        if (!effectiveAdminScope && !canManageAllTickets(username) && !canAccess(ticket, username)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not allowed to view this ticket");
        }
        return toSummary(ticket, username);
    }

    @Transactional(readOnly = true)
    public AttachmentContent getAttachment(Long ticketId, Long attachmentId, String username, boolean adminScope) {
        boolean effectiveAdminScope = adminScope && canManageAllTickets(username);
        Ticket ticket = getTicket(ticketId);
        if (!effectiveAdminScope && !canAccess(ticket, username)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not allowed to view this attachment");
        }
        TicketAttachment attachment = ticket.getAttachments().stream()
                .filter(item -> item.getId().equals(attachmentId))
                .findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Attachment not found"));
        Path filePath = Path.of(appProperties.uploadDir()).resolve(attachment.getStoredFileName());
        try {
            return new AttachmentContent(
                    attachment.getOriginalFileName(),
                    attachment.getContentType() == null || attachment.getContentType().isBlank()
                            ? org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE
                            : attachment.getContentType(),
                    Files.readAllBytes(filePath)
            );
        } catch (IOException ex) {
            throw new AppException(HttpStatus.NOT_FOUND, "Attachment file not found");
        }
    }

    @Transactional
    public AuthDtos.TicketCommentResponse addComment(Long ticketId, String username, AuthDtos.TicketCommentRequest request) {
        Ticket ticket = getTicket(ticketId);
        if (!canAccess(ticket, username)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not allowed to comment on this ticket");
        }
        TicketComment comment = new TicketComment();
        comment.setTicket(ticket);
        comment.setAuthor(userService.getByEmail(username));
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
        notifyAdditionalTicketAudiences(ticket, "New comment on ticket: " + ticket.getTitle(), NotificationType.COMMENT_ADDED);
        publishCommentEvent(ticket, "ADDED", saved.getId());
        return toCommentResponse(saved, username, List.of());
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.TicketCommentResponse> listComments(Long ticketId, String username, boolean adminScope) {
        boolean effectiveAdminScope = adminScope && canManageAllTickets(username);
        Ticket ticket = getTicket(ticketId);
        if (!effectiveAdminScope && !canAccess(ticket, username)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not allowed to view comments");
        }
        List<TicketComment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        if (isVendorRestrictedView(ticket, username)) {
            comments = comments.stream()
                    .filter(comment -> comment.getAuthor().getEmail().equals(username))
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
        boolean effectiveAdminScope = adminScope && canManageAllTickets(username);
        Ticket ticket = getTicket(ticketId);
        if (!effectiveAdminScope && !canAccess(ticket, username)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not allowed to view site visit history");
        }
        return ticketSiteVisitRepository.findByTicketIdOrderByVisitedAtDesc(ticketId).stream()
                .map(this::toSiteVisitResponse)
                .toList();
    }

    @Transactional
    public AuthDtos.TicketSiteVisitResponse addSiteVisit(Long ticketId, String username, AuthDtos.TicketSiteVisitRequest request) {
        Ticket ticket = getTicket(ticketId);
        AppUser actor = userService.getByEmail(username);
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
        siteVisit.setLatitude(validateLatitude(request.latitude()));
        siteVisit.setLongitude(validateLongitude(request.longitude()));
        siteVisit.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        TicketSiteVisit saved = ticketSiteVisitRepository.save(siteVisit);
        ticket.setSiteVisits((ticket.getSiteVisits() == null ? 0 : ticket.getSiteVisits()) + 1);
        ticket.setUpdatedBy(actor);
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketRepository.save(ticket);
        notifyStakeholders(ticket, "Site visit logged for ticket: " + ticket.getTitle(), NotificationType.TICKET_UPDATED, EmailNotificationAction.SITE_VISIT_ADDED);
        notifyAdditionalTicketAudiences(ticket, "Site visit logged for ticket: " + ticket.getTitle(), NotificationType.TICKET_UPDATED);
        return toSiteVisitResponse(saved);
    }

    @Transactional
    public AuthDtos.TicketCommentResponse updateComment(Long ticketId, Long commentId, String username, AuthDtos.TicketCommentUpdateRequest request) {
        TicketComment comment = getComment(ticketId, commentId);
        if (!comment.getAuthor().getEmail().equals(username)) {
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
        if (!comment.getAuthor().getEmail().equals(username)) {
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

    private Set<TicketStatus> parseStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(status.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(TicketStatus::valueOf)
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(TicketStatus.class)));
    }

    private Specification<Ticket> buildScopeSpecification(AppUser user, boolean effectiveAdminScope, boolean assignedOnly, boolean createdOnly) {
        if (effectiveAdminScope) {
            return Specification.where(null);
        }
        if (userService.hasRole(user, "ROLE_VENDOR")) {
            return vendorVisibleSpecification(user.getId());
        }
        if (createdOnly) {
            return createdByUserSpecification(user.getId());
        }
        if (userService.hasRole(user, "ROLE_AGENT")) {
            return assignedToUserSpecification(user.getId());
        }
        return scopeForUser(user.getId(), assignedOnly);
    }

    private Specification<Ticket> createdByUserSpecification(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("createdBy").get("id"), userId);
    }

    private Specification<Ticket> assignedToUserSpecification(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("assignedTo").get("id"), userId);
    }

    private Specification<Ticket> vendorVisibleSpecification(Long userId) {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("createdBy").get("id"), userId),
                cb.equal(root.get("assignedTo").get("id"), userId),
                cb.equal(root.get("vendorUser").get("id"), userId)
        );
    }

    private Specification<Ticket> statusesSpecification(Set<TicketStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return Specification.where(null);
        }
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    private Specification<Ticket> prioritySpecification(TicketPriority priority) {
        if (priority == null) {
            return Specification.where(null);
        }
        return (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    private Specification<Ticket> assignedToSpecification(Long assignedToId) {
        if (assignedToId == null) {
            return Specification.where(null);
        }
        return (root, query, cb) -> cb.equal(root.get("assignedTo").get("id"), assignedToId);
    }

    private Specification<Ticket> vendorSpecification(Long vendorUserId) {
        if (vendorUserId == null) {
            return Specification.where(null);
        }
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("vendorUser").get("id"), vendorUserId),
                cb.equal(root.get("createdBy").get("id"), vendorUserId)
        );
    }

    private Specification<Ticket> searchSpecification(String search) {
        if (search == null || search.isBlank()) {
            return Specification.where(null);
        }
        return (root, query, cb) -> {
            query.distinct(true);
            String likeValue = "%" + search.toLowerCase() + "%";
            var createdBy = root.join("createdBy", JoinType.LEFT);
            var assignedTo = root.join("assignedTo", JoinType.LEFT);
            var serviceUsers = root.join("serviceUsers", JoinType.LEFT);
            return cb.or(
                    cb.like(root.get("id").as(String.class), likeValue),
                    cb.like(cb.lower(root.get("title")), likeValue),
                    cb.like(cb.lower(root.get("description")), likeValue),
                    cb.like(cb.lower(createdBy.get("username")), likeValue),
                    cb.like(cb.lower(assignedTo.get("username")), likeValue),
                    cb.like(cb.lower(serviceUsers.get("username")), likeValue)
            );
        };
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
        AppUser user = userService.getByEmail(username);
        boolean isVendor = userService.hasRole(user, "ROLE_VENDOR");
        boolean isAgent = userService.hasRole(user, "ROLE_AGENT");

        if (isVendor) {
            return (ticket.getCreatedBy() != null && ticket.getCreatedBy().getId().equals(user.getId()))
                    || (ticket.getAssignedTo() != null && ticket.getAssignedTo().getId().equals(user.getId()))
                    || (ticket.getVendorUser() != null && ticket.getVendorUser().getId().equals(user.getId()));
        }
        
        if (isAgent) {
            // Agent users can only access tickets assigned to them
            return ticket.getAssignedTo() != null && ticket.getAssignedTo().getEmail().equals(username);
        }
        
        return canManageAllTickets(username)
                || ticket.getCreatedBy().getEmail().equals(username)
                || (ticket.getAssignedTo() != null && ticket.getAssignedTo().getEmail().equals(username))
                || ticket.getServiceUsers().stream().anyMatch(serviceUser -> serviceUser.getEmail().equals(username));
    }

    public boolean canManageAllTickets(String username) {
        AppUser user = userService.getByEmail(username);
        return userService.hasAuthority(user, "FEATURE_TICKETS_ALL_VIEW");
    }

    private String mapSortProperty(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "updatedAt";
        }
        return switch (sortBy) {
            case "createdBy" -> "createdBy.username";
            case "assignedTo" -> "assignedTo.username";
            case "serviceType" -> "serviceType";
            default -> sortBy;
        };
    }

    private void applyRequest(Ticket ticket, AuthDtos.TicketRequest request, AppUser creator, String actorUsername, boolean isCreate) {
        AppUser actor = userService.getByEmail(actorUsername);
        boolean vendorActor = userService.hasRole(actor, "ROLE_VENDOR");
        boolean agentActor = userService.hasRole(actor, "ROLE_AGENT");
        if (agentActor && !isCreate) {
            applyAgentSiteVisitUpdate(ticket, request, actor);
            if (request.status() != null && !request.status().isBlank()) {
                TicketStatus newStatus = TicketStatus.valueOf(request.status());
                // Agent users cannot set ticket status to CLOSED
                if (newStatus == TicketStatus.CLOSED) {
                    throw new AppException(HttpStatus.FORBIDDEN, "Agent users cannot close tickets");
                }
                ticket.setStatus(newStatus);
            }
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
        } else if (request.siteVisits() != null) {
            if (request.siteVisits() < 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Site visits cannot be negative");
            }
            ticket.setSiteVisits(request.siteVisits());
        }
        ticket.setParentTicket(request.parentTicketId() == null ? null : getTicket(request.parentTicketId()));
        ticket.setVendorNotes(request.vendorNotes() == null || request.vendorNotes().isBlank() ? null : request.vendorNotes().trim());
        ticket.setCustomerName(request.customerName() == null || request.customerName().isBlank() ? null : request.customerName().trim());
        ticket.setCustomerEmail(request.customerEmail() == null || request.customerEmail().isBlank() ? null : request.customerEmail().trim());
        ticket.setCustomerPhone(request.customerPhone() == null || request.customerPhone().isBlank() ? null : request.customerPhone().trim());
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
        if (request.siteVisits() != null) {
            if (request.siteVisits() < 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Site visits cannot be negative");
            }
            ticket.setSiteVisits(request.siteVisits());
        }
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

    private void notifyAdditionalTicketAudiences(Ticket ticket, String message, NotificationType type) {
        boolean adminEmailEnabled = emailNotificationSettingsService.isEmailEnabled(EmailNotificationAction.ADMIN_TICKET_ACTIVITY);
        boolean adminSmsEnabled = emailNotificationSettingsService.isSmsEnabled(EmailNotificationAction.ADMIN_TICKET_ACTIVITY);
        boolean vendorEmailEnabled = emailNotificationSettingsService.isEmailEnabled(EmailNotificationAction.VENDOR_CREATED_TICKET_ACTIVITY);
        boolean vendorSmsEnabled = emailNotificationSettingsService.isSmsEnabled(EmailNotificationAction.VENDOR_CREATED_TICKET_ACTIVITY);

        if (!adminEmailEnabled && !adminSmsEnabled && !vendorEmailEnabled && !vendorSmsEnabled) {
            return;
        }

        boolean vendorCreatedTicket = ticket.getCreatedBy() != null && userService.hasRole(ticket.getCreatedBy(), "ROLE_VENDOR");
        Map<Long, ChannelPreference> recipientPreferences = new java.util.LinkedHashMap<>();

        for (AppUser admin : userService.getAdmins()) {
            if (admin == null || admin.getId() == null) {
                continue;
            }
            ChannelPreference preference = recipientPreferences.computeIfAbsent(admin.getId(), ignored -> new ChannelPreference(admin));
            preference.emailEnabled |= adminEmailEnabled;
            preference.smsEnabled |= adminSmsEnabled;
            if (vendorCreatedTicket) {
                preference.emailEnabled |= vendorEmailEnabled;
                preference.smsEnabled |= vendorSmsEnabled;
            }
        }

        if (vendorCreatedTicket) {
            AppUser vendorRecipient = ticket.getVendorUser() != null ? ticket.getVendorUser() : ticket.getCreatedBy();
            if (vendorRecipient != null && vendorRecipient.getId() != null) {
                ChannelPreference preference = recipientPreferences.computeIfAbsent(vendorRecipient.getId(), ignored -> new ChannelPreference(vendorRecipient));
                preference.emailEnabled |= vendorEmailEnabled;
                preference.smsEnabled |= vendorSmsEnabled;
            }
        }

        recipientPreferences.values().stream()
                .filter(ChannelPreference::hasAnyChannelEnabled)
                .forEach(preference -> notificationService.sendOutboundNotification(
                        preference.user,
                        type,
                        message,
                        "TICKET",
                        ticket.getId(),
                        preference.emailEnabled,
                        preference.smsEnabled
                ));
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
                .filter(user -> user != null && user.getEmail() != null)
                .forEach(user -> messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/ticket-comments", event));
    }

    private void publishTicketListRefreshEvent(String action, Ticket ticket) {
        messagingTemplate.convertAndSend("/topic/tickets-refresh", Map.of(
                "action", action,
                "ticketId", ticket.getId(),
                "status", ticket.getStatus().name(),
                "updatedAt", LocalDateTime.now().toString()
        ));
    }

    public AuthDtos.TicketSummary toSummary(Ticket ticket) {
        return toSummary(ticket, null);
    }

    public AuthDtos.TicketSummary toListSummary(Ticket ticket, String viewerUsername) {
        boolean vendorRestrictedView = viewerUsername != null && isVendorRestrictedView(ticket, viewerUsername);
        return buildListSummary(ticket, vendorRestrictedView);
    }

    /**
     * Efficient list-row mapping that accepts pre-computed viewer flags,
     * avoiding a getByEmail() DB round-trip for every ticket in the page.
     */
    private AuthDtos.TicketSummary toListSummary(Ticket ticket, boolean isVendorViewer, Long viewerUserId) {
        boolean vendorRestrictedView = isVendorViewer
                && ticket.getCreatedBy() != null
                && ticket.getCreatedBy().getId().equals(viewerUserId);
        return buildListSummary(ticket, vendorRestrictedView);
    }

    private AuthDtos.TicketSummary buildListSummary(Ticket ticket, boolean vendorRestrictedView) {
        return new AuthDtos.TicketSummary(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                null,
                ticket.getServiceType() == null ? null : ticket.getServiceType().name(),
                ticket.getServiceType() == null ? null : ticket.getServiceType().label(),
                null,
                ticket.getSiteVisits(),
                null,
                null,
                ticket.getVendorUser() == null ? null : ticket.getVendorUser().getId(),
                ticket.getVendorUser() == null ? null : ticket.getVendorUser().getUsername(),
                ticket.getVendorUser() == null ? null : resolveVendorDisplayName(ticket.getVendorUser()),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                ticket.getStatus().name(),
                ticket.getPriority().name(),
                ticket.getScheduleDate(),
                ticket.getCreatedBy().getUsername(),
                ticket.getUpdatedBy() == null ? null : ticket.getUpdatedBy().getUsername(),
                ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getId(),
                vendorRestrictedView ? null : (ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getUsername()),
                vendorRestrictedView ? Set.of() : ticket.getServiceUsers().stream().map(AppUser::getId).collect(java.util.stream.Collectors.toSet()),
                vendorRestrictedView ? Set.of() : ticket.getServiceUsers().stream().map(AppUser::getUsername).collect(java.util.stream.Collectors.toSet()),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                List.<String>of(),
                !ticket.getAttachments().isEmpty(),
                List.of()
        );
    }

    public AuthDtos.TicketSummary toSummary(Ticket ticket, String viewerUsername) {
        boolean isAgent = viewerUsername != null && userService.hasRole(viewerUsername, "ROLE_AGENT");
        boolean canViewPrice = !isAgent;
        boolean vendorRestrictedView = viewerUsername != null && isVendorRestrictedView(ticket, viewerUsername);

        String customerEmail = ticket.getCustomerEmail();
        String customerPhone = ticket.getCustomerPhone();

        if (isAgent && appProperties.masking().enabled()) {
            customerEmail = maskEmail(customerEmail);
            customerPhone = maskPhone(customerPhone);
        }

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
                ticket.getVendorUser() == null ? null : resolveVendorDisplayName(ticket.getVendorUser()),
                ticket.getVendorUser() == null || vendorRestrictedView ? null : ticket.getVendorUser().getEmail(),
                ticket.getVendorUser() == null || vendorRestrictedView ? null : ticket.getVendorUser().getPhone(),
                ticket.getVendorNotes(),
                ticket.getCustomerName(),
                customerEmail,
                customerPhone,
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
                ticket.getAttachments().stream().map(TicketAttachment::getOriginalFileName).toList(),
                !ticket.getAttachments().isEmpty(),
                ticket.getAttachments().stream()
                        .map(this::toAttachmentInfo)
                        .toList()
        );
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf("@");
        String namePart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);
        if (namePart.length() <= 2) {
            return "**" + domainPart;
        }
        return namePart.substring(0, 2) + "****" + domainPart;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String p = phone.trim();
        if (p.length() <= 4) {
            return "****";
        }
        return p.substring(0, p.length() - 4) + "****";
    }

    private String resolveVendorDisplayName(AppUser user) {
        if (user == null) {
            return null;
        }
        if (user.getCompanyName() != null && !user.getCompanyName().isBlank()) {
            return user.getCompanyName().trim();
        }
        return user.getUsername();
    }

    private AuthDtos.TicketAttachmentInfo toAttachmentInfo(TicketAttachment attachment) {
        return new AuthDtos.TicketAttachmentInfo(
                attachment.getId(),
                attachment.getOriginalFileName(),
                attachment.getContentType() == null || attachment.getContentType().isBlank()
                        ? org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE
                        : attachment.getContentType(),
                attachment.getFileSize()
        );
    }

    public record AttachmentContent(String fileName, String contentType, byte[] content) {
    }

    private static final class ChannelPreference {
        private final AppUser user;
        private boolean emailEnabled;
        private boolean smsEnabled;

        private ChannelPreference(AppUser user) {
            this.user = user;
        }

        private boolean hasAnyChannelEnabled() {
            return emailEnabled || smsEnabled;
        }
    }

    public List<AuthDtos.TicketSummary> searchVisibleTickets(String username, String query) {
        String value = query == null ? "" : query.trim().toLowerCase();
        List<Ticket> tickets;
        
        if (canManageAllTickets(username)) {
            tickets = ticketRepository.findAll();
        } else {
            AppUser user = userService.getByEmail(username);
            boolean isVendor = userService.hasRole(user, "ROLE_VENDOR");
            boolean isAgent = userService.hasRole(user, "ROLE_AGENT");

            if (isVendor) {
                tickets = ticketRepository.findVendorVisibleTickets(user.getId());
            } else
            if (isAgent) {
                // Agent users can only see tickets assigned to them
                tickets = ticketRepository.findAssignedTicketsForUser(user.getId());
            } else {
                // Other users use the existing logic
                tickets = ticketRepository.findVisibleTicketsForUser(user.getId());
            }
        }
        
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

    @Transactional(readOnly = true)
    public List<AuthDtos.TicketSummary> searchParentTicketCandidates(String username, String query, Long excludeTicketId) {
        String value = query == null ? "" : query.trim().replaceFirst("^#+", "").trim();
        if (value.isBlank()) {
            return List.of();
        }
        Pageable limit = PageRequest.of(0, 8);
        List<Ticket> tickets;
        
        if (canManageAllTickets(username)) {
            tickets = ticketRepository.searchAllParentTicketCandidates(value, excludeTicketId, limit);
        } else {
            AppUser user = userService.getByEmail(username);
            boolean isVendor = userService.hasRole(user, "ROLE_VENDOR");
            boolean isAgent = userService.hasRole(user, "ROLE_AGENT");

            if (isVendor) {
                tickets = ticketRepository.searchVendorParentTicketCandidates(user.getId(), value, excludeTicketId, limit);
            } else
            if (isAgent) {
                // Agent users can only see tickets assigned to them
                tickets = ticketRepository.searchVisibleParentTicketCandidates(user.getId(), value, excludeTicketId, limit)
                        .stream()
                        .filter(ticket -> ticket.getAssignedTo() != null && ticket.getAssignedTo().getId().equals(user.getId()))
                        .toList();
            } else {
                // Other users use the existing logic
                tickets = ticketRepository.searchVisibleParentTicketCandidates(user.getId(), value, excludeTicketId, limit);
            }
        }
        
        return tickets.stream()
                .map(ticket -> toSummary(ticket, username))
                .toList();
    }

    private void addInitialComment(Ticket ticket, String username, String commentText) {
        if (commentText == null || commentText.isBlank()) {
            return;
        }
        TicketComment comment = new TicketComment();
        comment.setTicket(ticket);
        comment.setAuthor(userService.getByEmail(username));
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
        AppUser viewer = userService.getByEmail(username);
        return userService.hasRole(viewer, "ROLE_VENDOR")
                && ticket.getCreatedBy() != null
                && ticket.getCreatedBy().getId().equals(viewer.getId());
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
                comment.getAuthor().getEmail().equals(username),
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
                siteVisit.getLatitude(),
                siteVisit.getLongitude(),
                siteVisit.getNotes()
        );
    }

    private double validateLatitude(Double latitude) {
        if (latitude == null || latitude < -90 || latitude > 90) {
            throw new AppException(HttpStatus.BAD_REQUEST, "A valid current location is required to log a site visit");
        }
        return latitude;
    }

    private double validateLongitude(Double longitude) {
        if (longitude == null || longitude < -180 || longitude > 180) {
            throw new AppException(HttpStatus.BAD_REQUEST, "A valid current location is required to log a site visit");
        }
        return longitude;
    }
}
