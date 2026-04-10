package com.example.ticketmanager.controller.api;

import com.example.ticketmanager.dto.AdminDtos;
import com.example.ticketmanager.entity.TicketBillingStatus;
import com.example.ticketmanager.exception.AppException;
import com.example.ticketmanager.repository.UserRepository;
import com.example.ticketmanager.service.AdminService;
import com.example.ticketmanager.service.StaffBillingService;
import com.example.ticketmanager.service.TicketService;
import com.example.ticketmanager.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminRestController {
    private final TicketService ticketService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final AdminService adminService;
    private final StaffBillingService staffBillingService;

    @GetMapping("/report")
    @PreAuthorize("hasAuthority('FEATURE_ADMIN_REPORT_ACCESS')")
    public Map<String, Object> report() {
        return Map.of(
                "ticketCount", ticketService.countAll(),
                "userCount", userRepository.count()
        );
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('FEATURE_ADMIN_USER_MANAGEMENT')")
    public Page<AdminDtos.UserSummary> listUsers(@RequestParam(required = false) String query,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return userService.listUsers(query, page, size);
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('FEATURE_ADMIN_USER_MANAGEMENT')")
    public AdminDtos.UserSummary updateUser(@PathVariable Long userId,
                                            @Valid @RequestBody AdminDtos.UserUpdateRequest request,
                                            Principal principal) {
        return userService.updateUser(userId, request, principal.getName());
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('FEATURE_ADMIN_ROLE_MANAGEMENT')")
    public List<AdminDtos.RoleSummary> listRoles() {
        return adminService.listRoles();
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('FEATURE_ADMIN_ROLE_MANAGEMENT')")
    public AdminDtos.RoleSummary createRole(@Valid @RequestBody AdminDtos.RoleRequest request) {
        return adminService.createRole(request);
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('FEATURE_ADMIN_ROLE_MANAGEMENT')")
    public AdminDtos.RoleSummary updateRole(@PathVariable Long roleId, @Valid @RequestBody AdminDtos.RoleRequest request) {
        return adminService.updateRole(roleId, request);
    }

    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('FEATURE_ADMIN_ROLE_MANAGEMENT')")
    public void deleteRole(@PathVariable Long roleId) {
        adminService.deleteRole(roleId);
    }

    @GetMapping("/features")
    @PreAuthorize("hasAuthority('FEATURE_ADMIN_ROLE_FEATURE_ASSIGNMENT')")
    public List<AdminDtos.FeatureResponse> listFeatures() {
        return adminService.listFeatures();
    }

    @GetMapping("/email-notifications")
    @PreAuthorize("hasAuthority('FEATURE_ADMIN_EMAIL_NOTIFICATION_MANAGEMENT')")
    public List<AdminDtos.EmailNotificationSettingResponse> listEmailNotifications() {
        return adminService.listEmailNotificationSettings();
    }

    @PutMapping("/email-notifications")
    @PreAuthorize("hasAuthority('FEATURE_ADMIN_EMAIL_NOTIFICATION_MANAGEMENT')")
    public List<AdminDtos.EmailNotificationSettingResponse> updateEmailNotifications(
            @RequestBody List<AdminDtos.EmailNotificationSettingUpdateItem> items) {
        return adminService.updateEmailNotificationSettings(items);
    }

    @PutMapping("/roles/{roleId}/features")
    @PreAuthorize("hasAuthority('FEATURE_ADMIN_ROLE_FEATURE_ASSIGNMENT')")
    public AdminDtos.RoleSummary updateRoleFeatures(@PathVariable Long roleId,
                                                    @RequestBody AdminDtos.RoleFeatureUpdateRequest request) {
        return adminService.updateRoleFeatures(roleId, request.features());
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('FEATURE_ADMIN_USER_MANAGEMENT')")
    public AdminDtos.UserDetailsResponse getUserDetails(@PathVariable Long id) {
        return userService.getUserDetails(id);
    }

    @GetMapping("/users/{id}/id-proofs")
    @PreAuthorize("hasAuthority('FEATURE_ADMIN_USER_MANAGEMENT')")
    public List<AdminDtos.IdProofDocumentResponse> getUserIdProofs(@PathVariable Long id) {
        return userService.getUserIdProofs(id);
    }

    @PutMapping("/users/{userId}/verify-id-proofs")
    @PreAuthorize("hasAuthority('FEATURE_ADMIN_USER_MANAGEMENT')")
    public Map<String, Object> verifyIdProofs(@PathVariable Long userId,
                                              @RequestBody Map<String, String> verificationData) {
        userService.verifyIdProofs(userId, verificationData);
        return Map.of("message", "ID proof verification updated successfully");
    }

    @GetMapping("/users/{userId}/id-proof/{idProofType}")
    @PreAuthorize("hasAuthority('FEATURE_ADMIN_USER_MANAGEMENT')")
    public ResponseEntity<ByteArrayResource> viewUserIdProof(@PathVariable Long userId,
                                                              @PathVariable String idProofType) {
        try {
            var doc = userService.getIdProofDocumentDataByType(userId, idProofType);
            ByteArrayResource resource = new ByteArrayResource(doc.getIdProofDocument());
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getIdProofFileName() + "\"")
                    .contentType(MediaType.parseMediaType(doc.getIdProofContentType()))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/staff-billing/{userId}/status")
    @PreAuthorize("hasAuthority('FEATURE_ADMIN_STAFF_BILLING') and hasAuthority('ROLE_ADMIN')")
    public Map<String, Object> updateStaffBillingStatus(@PathVariable Long userId,
                                                        @Valid @RequestBody AdminDtos.StaffBillingStatusUpdateRequest request) {
        TicketBillingStatus status;
        try {
            status = TicketBillingStatus.valueOf(request.status().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid billing status");
        }
        staffBillingService.updateClosedTicketBillingStatus(userId, status);
        return Map.of(
                "message", "PAID".equals(status.name()) ? "Closed-ticket billing marked as paid" : "Closed-ticket billing marked as unpaid",
                "status", status.name()
        );
    }

    @PutMapping("/staff-billing/tickets/{ticketId}/billing-status")
    @PreAuthorize("hasAuthority('FEATURE_ADMIN_STAFF_BILLING') and hasAuthority('ROLE_ADMIN')")
    public Map<String, Object> updateSingleTicketBillingStatus(@PathVariable Long ticketId,
                                                               @Valid @RequestBody AdminDtos.StaffBillingStatusUpdateRequest request) {
        TicketBillingStatus status;
        try {
            status = TicketBillingStatus.valueOf(request.status().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid billing status");
        }
        staffBillingService.updateSingleTicketBillingStatus(ticketId, status);
        return Map.of(
                "message", "PAID".equals(status.name()) ? "Ticket billing marked as paid" : "Ticket billing marked as unpaid",
                "status", status.name()
        );
    }
}
