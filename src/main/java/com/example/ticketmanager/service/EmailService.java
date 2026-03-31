package com.example.ticketmanager.service;

import com.example.ticketmanager.config.AppProperties;
import com.example.ticketmanager.entity.AppUser;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;
    private final AppProperties appProperties;
    private final TemplateEngine templateEngine;

    public void sendVerificationEmail(AppUser user, String verificationLink) {
        sendTemplate(
                user.getEmail(),
                "Verify your Ticket Manager account",
                "email/account-verification",
                Map.of(
                        "userDisplayName", displayName(user),
                        "actionUrl", verificationLink,
                        "actionLabel", "Verify Email",
                        "headline", "Verify your email address",
                        "intro", "Welcome to Ticket Manager. Confirm your email address to activate your account and start managing support requests."
                )
        );
    }

    public void sendPasswordResetEmail(AppUser user, String resetLink) {
        sendTemplate(
                user.getEmail(),
                "Reset your Ticket Manager password",
                "email/password-reset",
                Map.of(
                        "userDisplayName", displayName(user),
                        "actionUrl", resetLink,
                        "actionLabel", "Reset Password",
                        "headline", "Reset your password",
                        "intro", "We received a request to reset your password. Use the button below to choose a new password."
                )
        );
    }

    public void sendTicketNotificationEmail(AppUser user, String subject, String headline, String intro, String actionUrl, String actionLabel) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("userDisplayName", displayName(user));
        variables.put("headline", headline);
        variables.put("intro", intro);
        variables.put("actionUrl", actionUrl);
        variables.put("actionLabel", actionLabel);
        sendTemplate(
                user.getEmail(),
                subject,
                "email/ticket-notification",
                variables
        );
    }

    public void send(String to, String subject, String text) {
        if (!appProperties.mail().enabled()) {
            log.info("Mail disabled. To: {} Subject: {} Body: {}", to, subject, text);
            return;
        }
        sendHtml(to, subject, text.replace("\n", "<br>"));
    }

    private void sendTemplate(String to, String subject, String template, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariable("baseUrl", appProperties.baseUrl());
        variables.forEach(context::setVariable);
        String html = templateEngine.process(template, context);
        if (!appProperties.mail().enabled()) {
            log.info("Mail disabled. To: {} Subject: {} Template: {} Vars: {}", to, subject, template, variables);
            return;
        }
        sendHtml(to, subject, html);
    }

    private void sendHtml(String to, String subject, String html) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            String fromAddress = appProperties.mail().fromAddress();
            String fromName = appProperties.mail().fromName();
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(new InternetAddress(fromAddress, fromName == null ? "" : fromName).toString());
            }
            mailSender.send(message);
        } catch (MessagingException | java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException("Failed to send email", ex);
        }
    }

    private String displayName(AppUser user) {
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        if (firstName != null && !firstName.isBlank()) {
            return (firstName + " " + (lastName == null ? "" : lastName)).trim();
        }
        return user.getUsername();
    }
}
