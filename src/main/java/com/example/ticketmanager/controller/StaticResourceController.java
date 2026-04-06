package com.example.ticketmanager.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;

@RestController
public class StaticResourceController {

    @GetMapping("/favicon.ico")
    public ResponseEntity<byte[]> favicon() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/favicon.ico");
        if (!resource.exists()) {
            // Return empty response if favicon doesn't exist
            return ResponseEntity.ok().build();
        }
        
        byte[] faviconBytes = Files.readAllBytes(resource.getFile().toPath());
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/x-icon"))
                .cacheControl(org.springframework.http.CacheControl.maxAge(7, java.util.concurrent.TimeUnit.DAYS))
                .body(faviconBytes);
    }

    @GetMapping(path = "/.well-known/appspecific/com.chrome.devtools.json")
    public ResponseEntity<String> chromeDevTools() {
        // Return empty response for Chrome DevTools request
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}");
    }
}
