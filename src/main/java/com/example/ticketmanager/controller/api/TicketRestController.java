package com.example.ticketmanager.controller.api;

import com.example.ticketmanager.dto.AuthDtos;
import com.example.ticketmanager.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketRestController {
    private final TicketService ticketService;

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('FEATURE_TICKETS_MANAGE')")
    @PostMapping(consumes = {"multipart/form-data"})
    public AuthDtos.TicketSummary create(Principal principal,
                                         @Valid @ModelAttribute AuthDtos.TicketRequest request,
                                         @RequestParam(required = false) MultipartFile[] files) {
        return ticketService.create(principal.getName(), request, files);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAnyAuthority('FEATURE_TICKETS_MANAGE','FEATURE_SITE_VISIT_EDIT')")
    @PatchMapping(path = "/{ticketId}", consumes = {"multipart/form-data"})
    public AuthDtos.TicketSummary update(Principal principal,
                                         @PathVariable Long ticketId,
                                         @Valid @ModelAttribute AuthDtos.TicketRequest request,
                                         @RequestParam(required = false) MultipartFile[] files) {
        return ticketService.update(ticketId, principal.getName(), request, files);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('FEATURE_TICKETS_VIEW')")
    @GetMapping
    public Page<AuthDtos.TicketSummary> list(Principal principal,
                                             @RequestParam(defaultValue = "false") boolean adminScope,
                                             @RequestParam(defaultValue = "true") boolean assignedOnly,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String priority,
                                             @RequestParam(required = false) Long assignedToId,
                                             @RequestParam(required = false) String search,
                                             @RequestParam(defaultValue = "updatedAt") String sortBy,
                                             @RequestParam(defaultValue = "desc") String direction,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return ticketService.list(principal.getName(), adminScope, assignedOnly, status, priority, assignedToId, search, page, size, sortBy, direction);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('FEATURE_TICKETS_VIEW')")
    @GetMapping("/search")
    public List<AuthDtos.TicketSummary> search(Principal principal, @RequestParam String query) {
        return ticketService.searchVisibleTickets(principal.getName(), query);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('FEATURE_TICKETS_VIEW')")
    @GetMapping("/parent-search")
    public List<AuthDtos.TicketSummary> searchParentTickets(Principal principal,
                                                            @RequestParam String query,
                                                            @RequestParam(required = false) Long excludeTicketId) {
        return ticketService.searchParentTicketCandidates(principal.getName(), query, excludeTicketId);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('FEATURE_TICKETS_VIEW')")
    @GetMapping("/{ticketId}")
    public AuthDtos.TicketSummary get(Principal principal,
                                      @PathVariable Long ticketId,
                                      @RequestParam(defaultValue = "false") boolean adminScope) {
        return ticketService.get(ticketId, principal.getName(), adminScope);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('FEATURE_TICKETS_VIEW')")
    @DeleteMapping("/{ticketId}")
    public void delete(Principal principal, @PathVariable Long ticketId) {
        ticketService.delete(ticketId, principal.getName());
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('FEATURE_TICKETS_VIEW')")
    @PostMapping("/{ticketId}/comments")
    public AuthDtos.TicketCommentResponse addComment(Principal principal,
                                                     @PathVariable Long ticketId,
                                                     @Valid @RequestBody AuthDtos.TicketCommentRequest request) {
        return ticketService.addComment(ticketId, principal.getName(), request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('FEATURE_TICKETS_VIEW')")
    @GetMapping("/{ticketId}/comments")
    public List<AuthDtos.TicketCommentResponse> comments(Principal principal,
                                                         @PathVariable Long ticketId,
                                                         @RequestParam(defaultValue = "false") boolean adminScope) {
        return ticketService.listComments(ticketId, principal.getName(), adminScope);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('FEATURE_TICKETS_VIEW')")
    @GetMapping("/{ticketId}/site-visits")
    public List<AuthDtos.TicketSiteVisitResponse> siteVisits(Principal principal,
                                                             @PathVariable Long ticketId,
                                                             @RequestParam(defaultValue = "false") boolean adminScope) {
        return ticketService.listSiteVisits(ticketId, principal.getName(), adminScope);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAnyAuthority('FEATURE_TICKETS_MANAGE','FEATURE_SITE_VISIT_EDIT')")
    @PostMapping("/{ticketId}/site-visits")
    public AuthDtos.TicketSiteVisitResponse addSiteVisit(Principal principal,
                                                         @PathVariable Long ticketId,
                                                         @Valid @RequestBody AuthDtos.TicketSiteVisitRequest request) {
        return ticketService.addSiteVisit(ticketId, principal.getName(), request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('FEATURE_TICKETS_VIEW')")
    @PatchMapping("/{ticketId}/comments/{commentId}")
    public AuthDtos.TicketCommentResponse updateComment(Principal principal,
                                                        @PathVariable Long ticketId,
                                                        @PathVariable Long commentId,
                                                        @Valid @RequestBody AuthDtos.TicketCommentUpdateRequest request) {
        return ticketService.updateComment(ticketId, commentId, principal.getName(), request);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('FEATURE_TICKETS_VIEW')")
    @DeleteMapping("/{ticketId}/comments/{commentId}")
    public void deleteComment(Principal principal,
                              @PathVariable Long ticketId,
                              @PathVariable Long commentId) {
        ticketService.deleteComment(ticketId, commentId, principal.getName());
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('FEATURE_TICKETS_VIEW')")
    @GetMapping("/metrics")
    public Object metrics(Principal principal, @RequestParam(defaultValue = "false") boolean adminScope) {
        return ticketService.metrics(principal.getName(), adminScope);
    }
}
