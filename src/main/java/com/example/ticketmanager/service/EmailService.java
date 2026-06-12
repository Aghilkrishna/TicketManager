package com.example.ticketmanager.service;

import com.example.ticketmanager.config.AppProperties;
import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.Ticket;
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
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    public record TicketDigestRow(
            Long id,
            String title,
            String statusLabel,
            String statusColor,
            String priorityLabel,
            String assignedToName,
            String customerName,
            String updatedAt,
            String ticketUrl
    ) {}

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

    public void sendScheduleReminderEmail(Ticket ticket, AppUser assignedUser, List<String> ccEmails, String reminderType) {
        boolean isDayBefore = "DAY_BEFORE".equals(reminderType);
        String headline = isDayBefore ? "Reminder: Scheduled Visit Tomorrow" : "Reminder: Scheduled Visit Today";
        String badgeLabel = isDayBefore ? "Tomorrow" : "Today";
        String subject = isDayBefore
                ? "Ticket #" + ticket.getId() + " is scheduled for tomorrow"
                : "Ticket #" + ticket.getId() + " is scheduled today";

        String scheduledStr = ticket.getScheduleDate() != null
                ? ticket.getScheduleDate().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))
                : "Not set";

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("userDisplayName", displayName(assignedUser));
        variables.put("headline", headline);
        variables.put("badgeLabel", badgeLabel);
        variables.put("isDayBefore", isDayBefore);
        variables.put("ticketId", ticket.getId());
        variables.put("ticketTitle", ticket.getTitle());
        variables.put("ticketStatus", ticket.getStatus().name().replace('_', ' '));
        variables.put("ticketPriority", ticket.getPriority().name());
        variables.put("serviceTypeLabel", ticket.getServiceType() != null ? ticket.getServiceType().label() : "-");
        variables.put("scheduleDateTime", scheduledStr);
        variables.put("customerName", ticket.getCustomerName() != null ? ticket.getCustomerName() : "-");
        variables.put("address", ticket.getAddress() != null ? ticket.getAddress() : "-");
        variables.put("actionUrl", appProperties.baseUrl() + "/tickets/view?id=" + ticket.getId());

        Context context = new Context();
        context.setVariable("baseUrl", appProperties.baseUrl());
        variables.forEach(context::setVariable);
        String html = templateEngine.process("email/schedule-reminder", context);

        if (!appProperties.mail().enabled()) {
            log.info("Mail disabled. Schedule reminder skipped. To: {} Ticket: #{}", assignedUser.getEmail(), ticket.getId());
            return;
        }
        sendHtmlWithCc(assignedUser.getEmail(), subject, html, ccEmails);
    }

    public void sendOpenTicketsDigest(AppUser user, List<TicketDigestRow> tickets, String generatedDate) {
        Context context = new Context();
        context.setVariable("baseUrl", appProperties.baseUrl());
        context.setVariable("userDisplayName", displayName(user));
        context.setVariable("tickets", tickets);
        context.setVariable("totalCount", tickets.size());
        context.setVariable("generatedDate", generatedDate);
        String subject = "Your Open Tickets — " + generatedDate + " | Ticket Manager";
        String html = templateEngine.process("email/open-tickets-digest", context);
        if (!appProperties.mail().enabled()) {
            log.info("Mail disabled. Open tickets digest skipped. To: {} Tickets: {}", user.getEmail(), tickets.size());
            return;
        }
        sendHtml(user.getEmail(), subject, html);
    }

    public void sendFollowupAdminDigest(List<String> adminEmails, List<TicketDigestRow> tickets, String generatedDate) {
        Context context = new Context();
        context.setVariable("baseUrl", appProperties.baseUrl());
        context.setVariable("tickets", tickets);
        context.setVariable("totalCount", tickets.size());
        context.setVariable("generatedDate", generatedDate);
        String subject = "Follow-up & Site Revisit Tickets — " + generatedDate + " | Ticket Manager";
        String html = templateEngine.process("email/followup-admin-digest", context);
        if (!appProperties.mail().enabled()) {
            log.info("Mail disabled. Admin follow-up digest skipped. To: {} Tickets: {}", adminEmails, tickets.size());
            return;
        }
        sendHtmlToMultiple(adminEmails, subject, html);
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

    private void sendHtmlWithCc(String to, String subject, String html, List<String> ccEmails) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            if (ccEmails != null && !ccEmails.isEmpty()) {
                helper.setCc(ccEmails.stream().filter(e -> !e.equalsIgnoreCase(to)).distinct().toArray(String[]::new));
            }
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

    private void sendHtmlToMultiple(List<String> toEmails, String subject, String html) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(appProperties.mail().fromAddress(), appProperties.mail().fromName());
            helper.setTo(toEmails.toArray(String[]::new));
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send multi-recipient email to {}: {}", toEmails, e.getMessage(), e);
        }
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
            String htmlContent = createReportEmailTemplate(message, List.of(filename));
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

    public void sendReportEmailWithAttachments(String to, String subject, String message, Map<String, byte[]> attachments) {
        if (!appProperties.mail().enabled()) {
            log.info("Mail disabled. To: {} Subject: {} Attachments: {}", to, subject, attachments != null ? attachments.keySet() : "none");
            return;
        }

        try {
            var mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

            String[] recipients = to.split(",");
            helper.setTo(recipients);
            helper.setSubject(subject);
            List<String> attachmentNames = attachments == null ? List.of() : attachments.keySet().stream().toList();
            helper.setText(createReportEmailTemplate(message, attachmentNames), true);

            if (attachments != null) {
                for (Map.Entry<String, byte[]> entry : attachments.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        helper.addAttachment(entry.getKey(), new ByteArrayResource(entry.getValue()));
                    }
                }
            }

            String fromAddress = appProperties.mail().fromAddress();
            String fromName = appProperties.mail().fromName();
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(new InternetAddress(fromAddress, fromName == null ? "" : fromName).toString());
            }

            mailSender.send(mimeMessage);
            log.info("Report email sent successfully to: {} with attachments: {}", to, attachments != null ? attachments.keySet() : "none");
        } catch (MessagingException | java.io.UnsupportedEncodingException ex) {
            log.error("Failed to send report email with attachments", ex);
            throw new IllegalStateException("Failed to send report email with attachments", ex);
        }
    }

    private String createReportEmailTemplate(String message, List<String> filenames) {
        String attachmentItems = (filenames == null || filenames.isEmpty())
                ? "<li>No attachments</li>"
                : filenames.stream()
                .map(name -> "<li><strong>" + escapeHtml(name) + "</strong> <span style=\"color:#667085;\">(Excel .xlsx)</span></li>")
                .reduce("", String::concat);
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Ticket Manager Reports</title>
                <style>
                    body { margin:0; padding:24px; background:#f3f6fb; color:#172b4d; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif; }
                    .card { max-width:680px; margin:0 auto; background:#ffffff; border:1px solid #e6edf5; border-radius:16px; overflow:hidden; box-shadow:0 12px 32px rgba(15,23,42,0.08); }
                    .header { background:linear-gradient(135deg,#0f172a 0%,#0f6cbd 65%,#14b8a6 100%); color:#fff; padding:28px 24px; }
                    .header h1 { margin:0; font-size:22px; font-weight:700; }
                    .header p { margin:6px 0 0; font-size:14px; color:rgba(255,255,255,0.9); }
                    .content { padding:24px; }
                    .message { background:#f8fbff; border:1px solid #d6e7ff; border-radius:12px; padding:14px 16px; margin:0 0 16px; color:#243b53; }
                    .meta { background:#f8fafc; border:1px solid #e5e7eb; border-radius:12px; padding:14px 16px; margin-bottom:16px; }
                    .meta h3 { margin:0 0 8px; font-size:14px; color:#334155; text-transform:uppercase; letter-spacing:0.04em; }
                    .meta ul { margin:0; padding-left:18px; }
                    .meta li { margin:6px 0; }
                    .footer { border-top:1px solid #e6edf5; padding:16px 24px; font-size:12px; color:#667085; background:#fcfdff; }
                    a { color:#0f6cbd; text-decoration:none; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="header">
                        <h1>Daily Reports</h1>
                        <p>Ticket Manager automated reporting</p>
                    </div>
                    <div class="content">
                        <p>Hello Admin Team,</p>
                        <div class="message">MESSAGE_PLACEHOLDER</div>
                        <div class="meta">
                            <h3>Report Details</h3>
                            <ul>
                                <li><strong>Scope:</strong> All tickets and all users</li>
                                <li><strong>Generated At:</strong> DATE_PLACEHOLDER</li>
                                <li><strong>Attachments:</strong></li>
                            </ul>
                            <ul>ATTACHMENTS_PLACEHOLDER</ul>
                        </div>
                        <p>You can open these files in Microsoft Excel or any compatible spreadsheet tool.</p>
                    </div>
                    <div class="footer">
                        <div>Ticket Manager</div>
                        <div><a href="BASE_URL_PLACEHOLDER">Open Application</a></div>
                    </div>
                </div>
            </body>
            </html>
            """
                .replace("MESSAGE_PLACEHOLDER", escapeHtml(message == null ? "" : message))
                .replace("DATE_PLACEHOLDER", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' hh:mm a")))
                .replace("ATTACHMENTS_PLACEHOLDER", attachmentItems)
                .replace("BASE_URL_PLACEHOLDER", appProperties.baseUrl());
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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
