package com.example.ticketmanager.service;

import com.example.ticketmanager.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {
    private final AppProperties appProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    public String generateOtp() {
        Random random = new Random();
        String otp = String.format("%06d", random.nextInt(1000000));
        log.debug("Generated OTP: {}", otp);
        return otp;
    }

    public boolean sendOtp(String phoneNumber, String otp) {
        if (!appProperties.sms().enabled()) {
            log.info("SMS disabled. Would send OTP {} to {}", otp, phoneNumber);
            return true;
        }

        try {
            String message = String.format("Your verification code for Ticket Manager is: %s. This code will expire in 10 minutes.", otp);
            String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
            String url = String.format("%s?apikey=%s&sender=%s&mobileno=%s&text=%s",
                    appProperties.sms().apiUrl(),
                    appProperties.sms().apiKey(),
                    appProperties.sms().sender(),
                    phoneNumber,
                    encodedMessage);

            log.info("Sending SMS to {}: {}", phoneNumber, url);
            String response = restTemplate.getForObject(url, String.class);
            log.info("SMS API response: {}", response);
            
            // Simple response validation - you may need to adjust based on actual API response format
            boolean success = response != null && !response.toLowerCase().contains("error");
            if (success) {
                log.info("SMS sent successfully to {}", phoneNumber);
            } else {
                log.error("SMS API returned error response: {}", response);
            }
            return success;
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", phoneNumber, e.getMessage(), e);
            return false;
        }
    }
}
