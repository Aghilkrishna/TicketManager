package com.example.ticketmanager.controller.api;

import com.example.ticketmanager.dto.AdminDtos;
import com.example.ticketmanager.dto.AuthDtos;
import com.example.ticketmanager.repository.UserRepository;
import com.example.ticketmanager.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserRestController {
    private final UserService userService;
    private final UserRepository userRepository;

    @GetMapping("/profile")
    public AuthDtos.ProfileResponse profile(Principal principal) {
        return userService.getProfile(principal.getName());
    }

    @PatchMapping("/profile")
    public AuthDtos.ProfileResponse updateProfile(Principal principal, @Valid @RequestBody AuthDtos.ProfileUpdateRequest request) {
        return userService.updateProfile(principal.getName(), request);
    }

    @PostMapping("/profile/password")
    public void changePassword(Principal principal, @Valid @RequestBody AuthDtos.ProfilePasswordChangeRequest request) {
        userService.changePassword(principal.getName(), request);
    }

    @PostMapping(path = "/profile/picture", consumes = {"multipart/form-data"})
    public AuthDtos.ProfileResponse uploadProfilePicture(Principal principal, @RequestParam("file") MultipartFile file) {
        return userService.updateProfileImage(principal.getName(), file);
    }

    @GetMapping("/profile/picture")
    public ResponseEntity<byte[]> profilePicture(Principal principal) {
        byte[] image = userService.getProfileImage(principal.getName());
        if (image == null || image.length == 0) {
            return ResponseEntity.noContent().build();
        }
        String contentType = userService.getProfileImageContentType(principal.getName());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .header(HttpHeaders.CONTENT_TYPE, contentType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType)
                .body(image);
    }

    @GetMapping("/avatar/{userId}")
    public ResponseEntity<byte[]> userAvatar(@PathVariable Long userId) {
        byte[] image = userService.getProfileImage(userId);
        if (image != null && image.length > 0) {
            String contentType = userService.getProfileImageContentType(userId);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                    .header(HttpHeaders.CONTENT_TYPE, contentType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType)
                    .body(image);
        }
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 96 96" role="img" aria-label="User avatar">
                  <rect width="96" height="96" rx="48" fill="#dbeafe"/>
                  <circle cx="48" cy="37" r="18" fill="#93c5fd"/>
                  <path d="M20 82c4-15 16-24 28-24s24 9 28 24" fill="#93c5fd"/>
                </svg>
                """;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .contentType(MediaType.valueOf("image/svg+xml"))
                .body(svg.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/search")
    public Object search(@RequestParam String query) {
        return userRepository.findTop10ByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrPhoneContainingIgnoreCase(
                query, query, query
        ).stream().map(user -> Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "phone", user.getPhone(),
                "roles", user.getRoles().stream()
                        .filter(role -> role.isActive())
                        .map(role -> role.getName())
                        .sorted()
                        .toList()
        )).toList();
    }

    @PostMapping(path = "/id-proof", consumes = {"multipart/form-data"})
    public Map<String, Object> uploadIdProof(Principal principal, 
                                             @RequestParam("file") MultipartFile file,
                                             @RequestParam("idProofType") String idProofType) {
        try {
            userService.uploadIdProof(principal.getName(), file, idProofType);
            return Map.of("message", "ID proof uploaded successfully");
        } catch (Exception e) {
            return Map.of("message", e.getMessage());
        }
    }

    @GetMapping("/id-proof/{docId}/view")
    public ResponseEntity<ByteArrayResource> viewIdProof(@PathVariable Long docId, Principal principal) {
        try {
            var doc = userService.getIdProofDocumentData(docId, principal.getName());
            ByteArrayResource resource = new ByteArrayResource(doc.getIdProofDocument());
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getIdProofFileName() + "\"")
                    .contentType(MediaType.parseMediaType(doc.getIdProofContentType()))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/id-proof/list")
    public List<AdminDtos.IdProofDocumentResponse> getIdProofList(Principal principal) {
        var user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return userService.getUserIdProofs(user.getId());
    }
}
