package com.example.ticketmanager.service;

import com.example.ticketmanager.config.AppProperties;
import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportSchedulerService {

    private final ReportService reportService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final AppProperties appProperties;

    @Scheduled(cron = "${app.reports.schedule.cron}") // Configurable cron from application properties
    public void generateDailyReports() {
        // Check if scheduler is enabled in configuration
        if (!appProperties.reports().schedule().enabled()) {
            log.info("Report scheduler is disabled in configuration. Skipping scheduled report generation.");
            return;
        }
        
        // Check if any admin user has the FEATURE_ADMIN_REPORTS role-feature enabled
        if (!isReportFeatureEnabledForAdmins()) {
            log.info("FEATURE_ADMIN_REPORTS is not enabled for any admin users. Skipping scheduled report generation.");
            return;
        }
        
        log.info("Starting scheduled report generation with cron: {}", appProperties.reports().schedule().cron());
        
        try {
            // Generate tickets report for all records
            byte[] ticketsReport = reportService.generateReport(
                "tickets", "all", null, null, null, null, null, null, null, null, "system-scheduler"
            );
            
            // Generate users report for all records
            byte[] usersReport = reportService.generateReport(
                "users", "all", null, null, null, null, null, null, null, null, "system-scheduler"
            );
            
            // Get configured email recipients
            List<String> recipients = getConfiguredEmailRecipients();
            
            if (recipients.isEmpty()) {
                log.warn("No configured email recipients found for scheduled reports");
                return;
            }
            
            String recipientEmails = String.join(",", recipients);
            
            String ticketsFilename = reportService.getReportFilename("tickets");
            String usersFilename = reportService.getReportFilename("users");
            String combinedSubject = "Daily Tickets and Users Reports - " + java.time.LocalDate.now();
            String combinedMessage = "Please find the daily tickets and users reports attached. These reports contain all tickets and users in the system.";
            reportService.sendGeneratedReportsInSingleEmail(
                    recipientEmails,
                    combinedSubject,
                    combinedMessage,
                    ticketsReport,
                    ticketsFilename,
                    usersReport,
                    usersFilename
            );
            
            log.info("Successfully generated and sent daily reports to {} recipients. ticketsBytes={}, usersBytes={}",
                    recipients.size(), ticketsReport.length, usersReport.length);
            
        } catch (Exception e) {
            log.error("Error generating scheduled daily reports", e);
        }
    }
    
    private List<String> getConfiguredEmailRecipients() {
        // Get admin users who have email verified and are enabled
        List<AppUser> adminUsers = userRepository.findByRoleName("ROLE_ADMIN", org.springframework.data.domain.PageRequest.of(0, 1000)).getContent().stream()
            .filter(user -> user.isEnabled()
                    && user.isEmailVerified()
                    && user.getEmail() != null
                    && !user.getEmail().isBlank()
                    && userService.hasAuthority(user, "FEATURE_ADMIN_REPORTS"))
            .collect(Collectors.toList());
        
        // Extract email addresses
        List<String> recipients = adminUsers.stream()
            .map(AppUser::getEmail)
            .collect(Collectors.toList());
        
        // Role feature check is now done at the scheduler level in isReportFeatureEnabledForAdmins()
        
        // Add any additional configured recipients from application properties if needed
        // This could be extended to read from a configuration file or database
        
        return recipients;
    }
    
    /**
     * Check if any admin user has the FEATURE_ADMIN_REPORTS role-feature enabled
     */
    private boolean isReportFeatureEnabledForAdmins() {
        try {
            // Get all admin users
            List<AppUser> adminUsers = userRepository.findByRoleName("ROLE_ADMIN", org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();
            
            // Check if any admin user has the FEATURE_ADMIN_REPORTS authority
            return adminUsers.stream()
                .anyMatch(adminUser -> userService.hasAuthority(adminUser, "FEATURE_ADMIN_REPORTS"));
                
        } catch (Exception e) {
            log.error("Error checking if FEATURE_ADMIN_REPORTS is enabled for admin users", e);
            // If there's an error checking the feature, assume it's not enabled to be safe
            return false;
        }
    }
}
