package com.example.ticketmanager.controller.api;

import com.example.ticketmanager.dto.AdminDtos;
import com.example.ticketmanager.repository.UserRepository;
import com.example.ticketmanager.service.AdminService;
import com.example.ticketmanager.service.TicketService;
import com.example.ticketmanager.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRestController {
    private final TicketService ticketService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final AdminService adminService;

    @GetMapping("/report")
    public Map<String, Object> report() {
        return Map.of(
                "ticketCount", ticketService.countAll(),
                "userCount", userRepository.count()
        );
    }

    @GetMapping("/users")
    public Page<AdminDtos.UserSummary> listUsers(@RequestParam(required = false) String query,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return userService.listUsers(query, page, size);
    }

    @PutMapping("/users/{userId}")
    public AdminDtos.UserSummary updateUser(@PathVariable Long userId,
                                            @Valid @RequestBody AdminDtos.UserUpdateRequest request,
                                            Principal principal) {
        return userService.updateUser(userId, request, principal.getName());
    }

    @GetMapping("/roles")
    public List<AdminDtos.RoleSummary> listRoles() {
        return adminService.listRoles();
    }

    @PostMapping("/roles")
    public AdminDtos.RoleSummary createRole(@Valid @RequestBody AdminDtos.RoleRequest request) {
        return adminService.createRole(request);
    }

    @PutMapping("/roles/{roleId}")
    public AdminDtos.RoleSummary updateRole(@PathVariable Long roleId, @Valid @RequestBody AdminDtos.RoleRequest request) {
        return adminService.updateRole(roleId, request);
    }

    @DeleteMapping("/roles/{roleId}")
    public void deleteRole(@PathVariable Long roleId) {
        adminService.deleteRole(roleId);
    }

    @GetMapping("/features")
    public List<AdminDtos.FeatureResponse> listFeatures() {
        return adminService.listFeatures();
    }

    @PutMapping("/roles/{roleId}/features")
    public AdminDtos.RoleSummary updateRoleFeatures(@PathVariable Long roleId,
                                                    @RequestBody AdminDtos.RoleFeatureUpdateRequest request) {
        return adminService.updateRoleFeatures(roleId, request.features());
    }
}
