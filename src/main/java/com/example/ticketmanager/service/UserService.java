package com.example.ticketmanager.service;

import com.example.ticketmanager.dto.AdminDtos;
import com.example.ticketmanager.dto.AuthDtos;
import com.example.ticketmanager.entity.*;
import com.example.ticketmanager.exception.AppException;
import com.example.ticketmanager.repository.PasswordResetTokenRepository;
import com.example.ticketmanager.repository.RoleRepository;
import com.example.ticketmanager.repository.UserIdProofRepository;
import com.example.ticketmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final String USERS_BY_EMAIL_CACHE = "usersByEmail";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserIdProofRepository userIdProofRepository;
    private final EmailService emailService;
    private final com.example.ticketmanager.config.AppProperties appProperties;
    private final EmailNotificationSettingsService emailNotificationSettingsService;
    private final SimpMessagingTemplate messagingTemplate;
    private final CacheManager cacheManager;

    @Cacheable(value = "usersByEmail", key = "#email")
    public AppUser getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public AppUser getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public AuthDtos.ProfileResponse getProfile(String email) {
        AppUser user = getByEmail(email);
        return toProfile(user);
    }

    @Transactional
    @CacheEvict(value = "usersByEmail", key = "#email")
    public AuthDtos.ProfileResponse updateProfile(String email, AuthDtos.ProfileUpdateRequest request) {
        AppUser user = getByEmail(email);
        if (!user.getEmail().equals(request.email()) && userRepository.existsByEmail(request.email())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Email already in use");
        }
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setFirstName(normalize(request.firstName()));
        user.setLastName(normalize(request.lastName()));
        user.setFlat(normalize(request.flat()));
        user.setBuilding(normalize(request.building()));
        user.setArea(normalize(request.area()));
        user.setCity(normalize(request.city()));
        user.setState(normalize(request.state()));
        user.setCountry(normalize(request.country()));
        user.setPincode(normalize(request.pincode()));
        return toProfile(userRepository.save(user));
    }

    @Transactional
    @CacheEvict(value = "usersByEmail", key = "#email")
    public void changePassword(String email, AuthDtos.ProfilePasswordChangeRequest request) {
        AppUser user = getByEmail(email);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "New password and confirm password do not match");
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "New password must be different from the current password");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional
    @CacheEvict(value = "usersByEmail", key = "#email")
    public AuthDtos.ProfileResponse updateProfileImage(String email, MultipartFile file) {
        AppUser user = getByEmail(email);
        if (file == null || file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Profile picture is required");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Profile picture must be 5MB or smaller");
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equalsIgnoreCase("image/png")
                && !contentType.equalsIgnoreCase("image/jpeg")
                && !contentType.equalsIgnoreCase("image/jpg"))) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Only PNG and JPEG images are allowed");
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
            if (source == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Invalid image file");
            }
            BufferedImage cropped = cropSquare(source);
            BufferedImage resized = resize(cropped, 256, 256);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(resized, "png", outputStream);
            user.setProfileImage(outputStream.toByteArray());
            user.setProfileImageContentType("image/png");
            return toProfile(userRepository.save(user));
        } catch (IOException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Failed to process image");
        }
    }

    @Transactional(readOnly = true)
    public byte[] getProfileImage(String email) {
        return getByEmail(email).getProfileImage();
    }

    @Transactional(readOnly = true)
    public String getProfileImageContentType(String email) {
        return getByEmail(email).getProfileImageContentType();
    }

    @Transactional(readOnly = true)
    public byte[] getProfileImage(Long userId) {
        return getById(userId).getProfileImage();
    }

    @Transactional(readOnly = true)
    public String getProfileImageContentType(Long userId) {
        return getById(userId).getProfileImageContentType();
    }

    public void createPasswordReset(String email) {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Email not found"));
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(LocalDateTime.now().plusHours(2));
        passwordResetTokenRepository.save(token);
        String link = appProperties.baseUrl() + "/reset-password?token=" + token.getToken();
        if (emailNotificationSettingsService.isEnabled(EmailNotificationAction.PASSWORD_RESET)) {
            emailService.sendPasswordResetEmail(user, link);
        }
    }

    @Transactional
    public void resetPassword(AuthDtos.PasswordResetConfirmRequest request) {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(request.token())
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Invalid reset token"));
        if (token.isUsed() || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Reset token expired");
        }
        AppUser user = token.getUser();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        token.setUsed(true);
    }

    public Set<String> getRoleNames(AppUser user) {
        return user.getRoles().stream()
                .filter(Role::isActive)
                .map(Role::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<String> getRoleLabels(AppUser user) {
        return user.getRoles().stream()
                .filter(Role::isActive)
                .map(Role::getName)
                .map(this::toRoleLabel)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<String> getFeatureAuthorities(AppUser user) {
        return user.getRoles().stream()
                .filter(Role::isActive)
                .flatMap(role -> role.getFeatures().stream())
                .map(AppFeature::authority)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public String toRoleLabel(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return "";
        }
        String normalized = roleName.startsWith("ROLE_") ? roleName.substring(5) : roleName;
        return Arrays.stream(normalized.split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1) + part.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    public boolean hasRole(AppUser user, String roleName) {
        return user.getRoles().stream().anyMatch(role -> role.isActive() && roleName.equals(role.getName()));
    }

    public boolean hasRole(String email, String roleName) {
        return hasRole(getByEmail(email), roleName);
    }

    public boolean hasAuthority(AppUser user, String authority) {
        return getFeatureAuthorities(user).contains(authority);
    }

    public boolean hasAuthority(String email, String authority) {
        return hasAuthority(getByEmail(email), authority);
    }

    @Transactional(readOnly = true)
    public Page<AdminDtos.UserSummary> listUsers(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "username"));
        String search = query == null ? "" : query.trim();
        if (search.isBlank()) {
            return userRepository.findAll(pageable).map(this::toUserSummary);
        }
        return userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrPhoneContainingIgnoreCase(
                search, search, search, pageable
        ).map(this::toUserSummary);
    }

    @Transactional
    public AdminDtos.UserSummary updateUser(Long userId, AdminDtos.UserUpdateRequest request, String actorEmail) {
        AppUser user = getById(userId);
        AppUser actor = getByEmail(actorEmail);
        String oldEmail = user.getEmail();
        boolean previousEnabled = user.isEnabled();
        Set<String> previousRoles = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        validateUniqueFields(user, request.username(), request.email());
        Set<Role> roles = resolveRoles(request.roleIds());
        preventUnsafeSelfUpdate(actor, user, request.enabled(), roles);
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setEnabled(request.enabled());
        user.setRoles(roles);
        AppUser saved = userRepository.save(user);

        // Evict both the old and new email so getByEmail always returns fresh data
        evictUserCache(oldEmail);
        if (!oldEmail.equals(saved.getEmail())) {
            evictUserCache(saved.getEmail());
        }

        Set<String> updatedRoles = saved.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        boolean statusChanged = previousEnabled != saved.isEnabled();
        boolean rolesChanged = !previousRoles.equals(updatedRoles);
        if (statusChanged || rolesChanged) {
            messagingTemplate.convertAndSend("/topic/admin-users-refresh", Map.of(
                    "userId", saved.getId(),
                    "statusChanged", statusChanged,
                    "rolesChanged", rolesChanged,
                    "updatedAt", LocalDateTime.now().toString()
            ));
        }

        return toUserSummary(saved);
    }

    public void evictUserCache(String email) {
        Cache cache = cacheManager.getCache(USERS_BY_EMAIL_CACHE);
        if (cache != null && email != null && !email.isBlank()) {
            cache.evict(email);
        }
    }

    private void validateUniqueFields(AppUser user, String username, String email) {
        if (!user.getUsername().equals(username) && userRepository.existsByUsername(username)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Username already in use");
        }
        if (!user.getEmail().equals(email) && userRepository.existsByEmail(email)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Email already in use");
        }
    }

    private Set<Role> resolveRoles(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "At least one role must be assigned");
        }
        return roleIds.stream()
                .map(id -> roleRepository.findById(id)
                        .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Role not found")))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void preventUnsafeSelfUpdate(AppUser actor, AppUser target, boolean enabled, Set<Role> roles) {
        if (!actor.getId().equals(target.getId())) {
            return;
        }
        if (!enabled) {
            throw new AppException(HttpStatus.BAD_REQUEST, "You cannot deactivate your own account");
        }
        boolean retainsAdmin = roles.stream().anyMatch(role -> "ROLE_ADMIN".equals(role.getName()) && role.isActive());
        if (!retainsAdmin) {
            throw new AppException(HttpStatus.BAD_REQUEST, "You cannot remove your own admin role");
        }
    }

    public AdminDtos.UserSummary toUserSummary(AppUser user) {
        return new AdminDtos.UserSummary(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.isEnabled(),
                user.isEmailVerified(),
                user.getRoles().stream().map(Role::getName).collect(Collectors.toCollection(LinkedHashSet::new)),
                user.getRoles().stream().map(Role::getName).map(this::toRoleLabel).collect(Collectors.toCollection(LinkedHashSet::new))
        );
    }

    public AdminDtos.UserDetailsResponse getUserDetails(Long userId) {
        AppUser user = getById(userId);
        java.util.List<UserIdProof> userIdProofs = userIdProofRepository.findByUser(user);
        boolean hasAadhar = userIdProofs.stream().anyMatch(id -> "Aadhar Card".equals(id.getIdProofType()));
        boolean hasPan = userIdProofs.stream().anyMatch(id -> "PAN Card".equals(id.getIdProofType()));
        boolean hasMandatoryIdProofs = hasAadhar && hasPan;
        boolean idProofVerified = userIdProofs.stream().anyMatch(id -> Boolean.TRUE.equals(id.getVerified()));
        boolean hasPendingVerification = userIdProofs.stream().anyMatch(id -> "PENDING_VERIFICATION".equals(id.getUploadStatus()));
        
        return new AdminDtos.UserDetailsResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getFirstName(),
                user.getLastName(),
                user.getFlat(),
                user.getBuilding(),
                user.getArea(),
                user.getCity(),
                user.getState(),
                user.getCountry(),
                user.getPincode(),
                user.isEmailVerified(),
                user.isPhoneVerified(),
                getRoleNames(user),
                getRoleLabels(user),
                user.getProfileImage() != null && user.getProfileImage().length > 0,
                null,
                null,
                false,
                hasAadhar,
                hasPan,
                hasMandatoryIdProofs,
                idProofVerified,
                hasPendingVerification
        );
    }

    public List<AdminDtos.IdProofDocumentResponse> getUserIdProofs(Long userId) {
        AppUser user = getById(userId);
        java.util.List<UserIdProof> userIdProofs = userIdProofRepository.findByUser(user);
        
        return userIdProofs.stream()
                .map(idProof -> new AdminDtos.IdProofDocumentResponse(
                        idProof.getId(),
                        idProof.getIdProofType(),
                        idProof.getIdProofFileName(),
                        idProof.getCreatedAt(),
                        idProof.getUploadStatus(),
                        idProof.getVerified(),
                        idProof.getVerificationNotes()
                ))
                .toList();
    }

    public AuthDtos.ProfileResponse toProfile(AppUser user) {
        return new AuthDtos.ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getFirstName(),
                user.getLastName(),
                user.getFlat(),
                user.getBuilding(),
                user.getArea(),
                user.getCity(),
                user.getState(),
                user.getCountry(),
                user.getPincode(),
                user.isEmailVerified(),
                user.isPhoneVerified(),
                getRoleNames(user),
                user.getProfileImage() != null && user.getProfileImage().length > 0
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BufferedImage cropSquare(BufferedImage source) {
        int size = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - size) / 2;
        int y = (source.getHeight() - size) / 2;
        return source.getSubimage(x, y, size, size);
    }

    private BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return output;
    }

    @Transactional
    public void uploadIdProof(String email, MultipartFile file, String idProofType) {
        AppUser user = getByEmail(email);
        
        // Check if document type already exists and is verified
        Optional<UserIdProof> existingDoc = userIdProofRepository.findByUserAndIdProofType(user, idProofType);
        if (existingDoc.isPresent() && Boolean.TRUE.equals(existingDoc.get().getVerified())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "This document type is already verified and cannot be re-uploaded");
        }
        
        // Validate file
        if (file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        
        if (file.getSize() > 5 * 1024 * 1024) { // 5MB
            throw new AppException(HttpStatus.BAD_REQUEST, "File size must be less than 5MB");
        }
        
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.equals("application/pdf"))) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Only image files and PDF are allowed");
        }
        
        try {
            UserIdProof idProof = existingDoc.orElse(new UserIdProof());
            idProof.setUser(user);
            idProof.setIdProofType(idProofType);
            idProof.setIdProofDocument(file.getBytes());
            idProof.setIdProofContentType(contentType);
            idProof.setIdProofFileName(file.getOriginalFilename());
            idProof.setFileSize(file.getSize());
            idProof.setUploadStatus("PENDING_VERIFICATION");
            idProof.setVerified(false);
            idProof.setVerificationNotes(null);
            
            userIdProofRepository.save(idProof);
        } catch (IOException e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process file: " + e.getMessage());
        }
    }

    public AdminDtos.IdProofDocumentResponse getIdProofDocument(Long docId, String email) {
        AppUser user = getByEmail(email);
        UserIdProof idProof = userIdProofRepository.findById(docId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));
        
        if (!idProof.getUser().getId().equals(user.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied");
        }
        
        return new AdminDtos.IdProofDocumentResponse(
                idProof.getId(),
                idProof.getIdProofType(),
                idProof.getIdProofFileName(),
                idProof.getCreatedAt(),
                idProof.getUploadStatus(),
                idProof.getVerified(),
                idProof.getVerificationNotes()
        );
    }

    public UserIdProof getIdProofDocumentData(Long docId, String email) {
        AppUser user = getByEmail(email);
        UserIdProof idProof = userIdProofRepository.findById(docId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));
        
        if (!idProof.getUser().getId().equals(user.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied");
        }
        
        return idProof;
    }

    public UserIdProof getIdProofDocumentDataByType(Long userId, String idProofType) {
        AppUser user = getById(userId);
        return userIdProofRepository.findByUserAndIdProofType(user, idProofType)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    @Transactional
    public void verifyIdProofs(Long userId, Map<String, String> verificationData) {
        AppUser user = getById(userId);
        
        // Process Aadhar Card verification
        if (verificationData.containsKey("aadharCardStatus")) {
            Optional<UserIdProof> aadharDoc = userIdProofRepository.findByUserAndIdProofType(user, "Aadhar Card");
            if (aadharDoc.isPresent()) {
                UserIdProof doc = aadharDoc.get();
                doc.setUploadStatus(verificationData.get("aadharCardStatus"));
                doc.setVerified("VERIFIED".equals(verificationData.get("aadharCardStatus")));
                doc.setVerificationNotes(verificationData.get("aadharCardNotes"));
                userIdProofRepository.save(doc);
            }
        }
        
        // Process PAN Card verification
        if (verificationData.containsKey("panCardStatus")) {
            Optional<UserIdProof> panDoc = userIdProofRepository.findByUserAndIdProofType(user, "PAN Card");
            if (panDoc.isPresent()) {
                UserIdProof doc = panDoc.get();
                doc.setUploadStatus(verificationData.get("panCardStatus"));
                doc.setVerified("VERIFIED".equals(verificationData.get("panCardStatus")));
                doc.setVerificationNotes(verificationData.get("panCardNotes"));
                userIdProofRepository.save(doc);
            }
        }
    }

    public AdminDtos.IdProofDocumentResponse getIdProofDocumentByType(Long userId, String idProofType) {
        AppUser user = getById(userId);
        UserIdProof idProof = userIdProofRepository.findByUserAndIdProofType(user, idProofType)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));
        
        return new AdminDtos.IdProofDocumentResponse(
                idProof.getId(),
                idProof.getIdProofType(),
                idProof.getIdProofFileName(),
                idProof.getCreatedAt(),
                idProof.getUploadStatus(),
                idProof.getVerified(),
                idProof.getVerificationNotes()
        );
    }
}
