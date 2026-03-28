package com.example.ticketmanager.controller.api;

import com.example.ticketmanager.dto.AuthDtos;
import com.example.ticketmanager.repository.UserRepository;
import com.example.ticketmanager.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.concurrent.TimeUnit;
import java.util.Map;

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

    @GetMapping("/search")
    public Object search(@RequestParam String query) {
        return userRepository.findTop10ByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrPhoneContainingIgnoreCase(
                query, query, query
        ).stream().map(user -> Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "phone", user.getPhone()
        )).toList();
    }
}
