package com.example.ticketmanager.service;

import com.example.ticketmanager.config.AppProperties;
import com.example.ticketmanager.entity.AppUser;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
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

    public void sendReportEmail(String to, String subject, String message, byte[] reportData, String filename) {
        if (!appProperties.mail().enabled()) {
            log.info("Mail disabled. To: {} Subject: {} Report: {}", to, subject, filename);
            return;
        }

        try {
            var mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            
            // Set recipients and subject
            String[] recipients = to.split(",");
            helper.setTo(recipients);
            helper.setSubject(subject);
            
            // Create HTML content with application theme
            String htmlContent = createReportEmailTemplate(message, filename);
            helper.setText(htmlContent, true);
            
            // Add attachment
            helper.addAttachment(filename, new ByteArrayResource(reportData));
            
            // Set from address
            String fromAddress = appProperties.mail().fromAddress();
            String fromName = appProperties.mail().fromName();
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(new InternetAddress(fromAddress, fromName == null ? "" : fromName).toString());
            }
            
            mailSender.send(mimeMessage);
            log.info("Report email sent successfully to: {} with attachment: {}", to, filename);
            
        } catch (MessagingException | java.io.UnsupportedEncodingException ex) {
            log.error("Failed to send report email", ex);
            throw new IllegalStateException("Failed to send report email", ex);
        }
    }

    private String createReportEmailTemplate(String message, String filename) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Report - Ticket Manager</title>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                        line-height: 1.6;
                        color: #333;
                        max-width: 600px;
                        margin: 0 auto;
                        padding: 20px;
                        background-color: #f8f9fa;
                    }
                    .container {
                        background: white;
                        border-radius: 8px;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                        overflow: hidden;
                    }
                    .header {
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        padding: 30px 20px;
                        text-align: center;
                    }
                    .header h1 {
                        margin: 0;
                        font-size: 24px;
                        font-weight: 600;
                    }
                    .header .brand {
                        font-size: 18px;
                        opacity: 0.9;
                        margin-top: 5px;
                    }
                    .content {
                        padding: 30px 20px;
                    }
                    .message {
                        background-color: #f8f9fa;
                        border-left: 4px solid #667eea;
                        padding: 20px;
                        margin: 20px 0;
                        border-radius: 0 4px 4px 0;
                    }
                    .file-info {
                        background-color: #e7f3ff;
                        border: 1px solid #b3d9ff;
                        padding: 15px;
                        border-radius: 6px;
                        margin: 20px 0;
                    }
                    .file-info strong {
                        color: #0066cc;
                    }
                    .footer {
                        background-color: #f8f9fa;
                        padding: 20px;
                        text-align: center;
                        border-top: 1px solid #dee2e6;
                        font-size: 14px;
                        color: #6c757d;
                    }
                    .footer a {
                        color: #667eea;
                        text-decoration: none;
                    }
                    .footer a:hover {
                        text-decoration: underline;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Report Generated</h1>
                        <div class="brand">Ticket Manager</div>
                    </div>
                    <div class="content">
                        <p>Hello,</p>
                        <div class="message">
                            <p>MESSAGE_PLACEHOLDER</p>
                        </div>
                        <div class="file-info">
                            <p><strong>Report Details:</strong></p>
                            <ul>
                                <li><strong>Filename:</strong> FILENAME_PLACEHOLDER</li>
                                <li><strong>Format:</strong> Excel (.xlsx)</li>
                                <li><strong>Generated:</strong> DATE_PLACEHOLDER</li>
                            </ul>
                        </div>
                        <p>The report is attached to this email. You can open it using Microsoft Excel or any compatible spreadsheet application.</p>
                        <p>If you have any questions or need assistance, please don't hesitate to contact our support team.</p>
                        <p>Best regards,<br>Ticket Manager Team</p>
                    </div>
                    <div class="footer">
                        <p>&copy; 2026 Ticket Manager. All rights reserved.</p>
                        <p><a href="BASE_URL_PLACEHOLDER">Visit Ticket Manager</a></p>
                    </div>
                </div>
            </body>
            </html>
            """.replace("MESSAGE_PLACEHOLDER", message)
             .replace("FILENAME_PLACEHOLDER", filename)
             .replace("DATE_PLACEHOLDER", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' hh:mm a")))
             .replace("BASE_URL_PLACEHOLDER", appProperties.baseUrl());
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
