package com.example.ticketmanager.service;

import com.example.ticketmanager.entity.*;
import com.example.ticketmanager.repository.TicketRepository;
import com.example.ticketmanager.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public byte[] generateReport(String reportType, String dateRange, String startDate, String endDate,
                                String ticketStatus, String ticketPriority, String serviceType,
                                String userStatus, String emailVerified, String userRole, String generatedBy) {
        try {
            if ("tickets".equalsIgnoreCase(reportType)) {
                return generateTicketsReport(dateRange, startDate, endDate, ticketStatus, ticketPriority, serviceType);
            } else if ("users".equalsIgnoreCase(reportType)) {
                return generateUsersReport(dateRange, startDate, endDate, userStatus, emailVerified, userRole);
            } else {
                throw new IllegalArgumentException("Invalid report type: " + reportType);
            }
        } catch (Exception e) {
            log.error("Error generating report", e);
            throw new RuntimeException("Failed to generate report: " + e.getMessage(), e);
        }
    }

    public String getReportFilename(String reportType) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return reportType.toLowerCase() + "_report_" + timestamp + ".xlsx";
    }

    private byte[] generateTicketsReport(String dateRange, String startDate, String endDate,
                                       String ticketStatus, String ticketPriority, String serviceType) throws IOException {
        List<Ticket> tickets = getFilteredTickets(dateRange, startDate, endDate, ticketStatus, ticketPriority, serviceType);
        
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("Tickets Report");
            
            // Create header style
            CellStyle headerStyle = createHeaderStyle(workbook);
            
            // Create headers
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "ID", "Title", "Description", "Service Type", "Status", "Priority",
                "Customer Name", "Customer Email", "Customer Phone", "Address",
                "Customer Flat", "Customer Street", "Customer City", "Customer State", "Customer Pincode",
                "Estimated Cost", "Actual Cost", "Billing Status", "Schedule Date",
                "Created By", "Assigned To", "Created At", "Updated At", "Site Visits"
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Create data rows
            int rowNum = 1;
            for (Ticket ticket : tickets) {
                Row row = sheet.createRow(rowNum++);
                
                row.createCell(0).setCellValue(ticket.getId());
                row.createCell(1).setCellValue(ticket.getTitle() != null ? ticket.getTitle() : "");
                row.createCell(2).setCellValue(ticket.getDescription() != null ? ticket.getDescription() : "");
                row.createCell(3).setCellValue(ticket.getServiceType() != null ? ticket.getServiceType().toString() : "");
                row.createCell(4).setCellValue(ticket.getStatus() != null ? ticket.getStatus().toString() : "");
                row.createCell(5).setCellValue(ticket.getPriority() != null ? ticket.getPriority().toString() : "");
                row.createCell(6).setCellValue(ticket.getCustomerName() != null ? ticket.getCustomerName() : "");
                row.createCell(7).setCellValue(ticket.getCustomerEmail() != null ? ticket.getCustomerEmail() : "");
                row.createCell(8).setCellValue(ticket.getCustomerPhone() != null ? ticket.getCustomerPhone() : "");
                row.createCell(9).setCellValue(ticket.getAddress() != null ? ticket.getAddress() : "");
                row.createCell(10).setCellValue(ticket.getCustomerFlat() != null ? ticket.getCustomerFlat() : "");
                row.createCell(11).setCellValue(ticket.getCustomerStreet() != null ? ticket.getCustomerStreet() : "");
                row.createCell(12).setCellValue(ticket.getCustomerCity() != null ? ticket.getCustomerCity() : "");
                row.createCell(13).setCellValue(ticket.getCustomerState() != null ? ticket.getCustomerState() : "");
                row.createCell(14).setCellValue(ticket.getCustomerPincode() != null ? ticket.getCustomerPincode() : "");
                row.createCell(15).setCellValue(ticket.getEstimatedCost() != null ? ticket.getEstimatedCost().doubleValue() : 0.0);
                row.createCell(16).setCellValue(ticket.getActualCost() != null ? ticket.getActualCost().doubleValue() : 0.0);
                row.createCell(17).setCellValue(ticket.getBillingStatus() != null ? ticket.getBillingStatus().toString() : "");
                row.createCell(18).setCellValue(ticket.getScheduleDate() != null ? ticket.getScheduleDate().toString() : "");
                row.createCell(19).setCellValue(ticket.getCreatedBy() != null ? ticket.getCreatedBy().getUsername() : "");
                row.createCell(20).setCellValue(ticket.getAssignedTo() != null ? ticket.getAssignedTo().getUsername() : "");
                row.createCell(21).setCellValue(ticket.getCreatedAt() != null ? ticket.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
                row.createCell(22).setCellValue(ticket.getUpdatedAt() != null ? ticket.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
                row.createCell(23).setCellValue(ticket.getSiteVisits() != null ? ticket.getSiteVisits() : 0);
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] generateUsersReport(String dateRange, String startDate, String endDate,
                                     String userStatus, String emailVerified, String userRole) throws IOException {
        List<AppUser> users = getFilteredUsers(dateRange, startDate, endDate, userStatus, emailVerified, userRole);
        
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("Users Report");
            
            // Create header style
            CellStyle headerStyle = createHeaderStyle(workbook);
            
            // Create headers
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "ID", "Username", "Email", "Phone", "First Name", "Last Name", "Company Name",
                "Contact Person", "GST Number", "Flat", "Building", "Area", "City", "State",
                "Country", "Pincode", "Enabled", "Email Verified", "Phone Verified", "Created At"
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Create data rows
            int rowNum = 1;
            for (AppUser user : users) {
                Row row = sheet.createRow(rowNum++);
                
                row.createCell(0).setCellValue(user.getId());
                row.createCell(1).setCellValue(user.getUsername() != null ? user.getUsername() : "");
                row.createCell(2).setCellValue(user.getEmail() != null ? user.getEmail() : "");
                row.createCell(3).setCellValue(user.getPhone() != null ? user.getPhone() : "");
                row.createCell(4).setCellValue(user.getFirstName() != null ? user.getFirstName() : "");
                row.createCell(5).setCellValue(user.getLastName() != null ? user.getLastName() : "");
                row.createCell(6).setCellValue(user.getCompanyName() != null ? user.getCompanyName() : "");
                row.createCell(7).setCellValue(user.getContactPerson() != null ? user.getContactPerson() : "");
                row.createCell(8).setCellValue(user.getGstNumber() != null ? user.getGstNumber() : "");
                row.createCell(9).setCellValue(user.getFlat() != null ? user.getFlat() : "");
                row.createCell(10).setCellValue(user.getBuilding() != null ? user.getBuilding() : "");
                row.createCell(11).setCellValue(user.getArea() != null ? user.getArea() : "");
                row.createCell(12).setCellValue(user.getCity() != null ? user.getCity() : "");
                row.createCell(13).setCellValue(user.getState() != null ? user.getState() : "");
                row.createCell(14).setCellValue(user.getCountry() != null ? user.getCountry() : "");
                row.createCell(15).setCellValue(user.getPincode() != null ? user.getPincode() : "");
                row.createCell(16).setCellValue(user.isEnabled());
                row.createCell(17).setCellValue(user.isEmailVerified());
                row.createCell(18).setCellValue(user.isPhoneVerified());
                row.createCell(19).setCellValue(user.getCreatedAt() != null ? user.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        
        return style;
    }

    private List<Ticket> getFilteredTickets(String dateRange, String startDate, String endDate,
                                           String ticketStatus, String ticketPriority, String serviceType) {
        Specification<Ticket> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Date range filter
            if (dateRange != null && !dateRange.equals("all")) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime startDateTime;
                LocalDateTime endDateTime;
                
                switch (dateRange) {
                    case "today":
                        startDateTime = now.toLocalDate().atStartOfDay();
                        endDateTime = now.toLocalDate().atTime(23, 59, 59);
                        break;
                    case "yesterday":
                        LocalDate yesterday = now.toLocalDate().minusDays(1);
                        startDateTime = yesterday.atStartOfDay();
                        endDateTime = yesterday.atTime(23, 59, 59);
                        break;
                    case "last7days":
                        startDateTime = now.minusDays(7).toLocalDate().atStartOfDay();
                        endDateTime = now.toLocalDate().atTime(23, 59, 59);
                        break;
                    case "last30days":
                        startDateTime = now.minusDays(30).toLocalDate().atStartOfDay();
                        endDateTime = now.toLocalDate().atTime(23, 59, 59);
                        break;
                    case "thismonth":
                        startDateTime = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
                        endDateTime = now.toLocalDate().atTime(23, 59, 59);
                        break;
                    case "lastmonth":
                        LocalDate lastMonth = now.toLocalDate().minusMonths(1);
                        startDateTime = lastMonth.withDayOfMonth(1).atStartOfDay();
                        endDateTime = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()).atTime(23, 59, 59);
                        break;
                    case "custom":
                        if (startDate != null && endDate != null) {
                            startDateTime = LocalDate.parse(startDate).atStartOfDay();
                            endDateTime = LocalDate.parse(endDate).atTime(23, 59, 59);
                        } else {
                            return criteriaBuilder.conjunction();
                        }
                        break;
                    default:
                        return criteriaBuilder.conjunction();
                }
                
                predicates.add(criteriaBuilder.between(root.get("createdAt"), startDateTime, endDateTime));
            }
            
            // Status filter
            if (ticketStatus != null && !ticketStatus.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("status"), TicketStatus.valueOf(ticketStatus)));
            }
            
            // Priority filter
            if (ticketPriority != null && !ticketPriority.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("priority"), TicketPriority.valueOf(ticketPriority)));
            }
            
            // Service type filter
            if (serviceType != null && !serviceType.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("serviceType"), TicketServiceType.valueOf(serviceType)));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        
        return ticketRepository.findAll(spec);
    }

    private List<AppUser> getFilteredUsers(String dateRange, String startDate, String endDate,
                                         String userStatus, String emailVerified, String userRole) {
        // Get all users and filter in memory for now
        // In a production system, you might want to add custom queries to UserRepository
        List<AppUser> allUsers = userRepository.findAll();
        List<AppUser> filteredUsers = new ArrayList<>();
        
        for (AppUser user : allUsers) {
            boolean includeUser = true;
            
            // Date range filter
            if (dateRange != null && !dateRange.equals("all")) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime startDateTime = null;
                LocalDateTime endDateTime = null;
                
                switch (dateRange) {
                    case "today":
                        startDateTime = now.toLocalDate().atStartOfDay();
                        endDateTime = now.toLocalDate().atTime(23, 59, 59);
                        break;
                    case "yesterday":
                        LocalDate yesterday = now.toLocalDate().minusDays(1);
                        startDateTime = yesterday.atStartOfDay();
                        endDateTime = yesterday.atTime(23, 59, 59);
                        break;
                    case "last7days":
                        startDateTime = now.minusDays(7).toLocalDate().atStartOfDay();
                        endDateTime = now.toLocalDate().atTime(23, 59, 59);
                        break;
                    case "last30days":
                        startDateTime = now.minusDays(30).toLocalDate().atStartOfDay();
                        endDateTime = now.toLocalDate().atTime(23, 59, 59);
                        break;
                    case "thismonth":
                        startDateTime = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
                        endDateTime = now.toLocalDate().atTime(23, 59, 59);
                        break;
                    case "lastmonth":
                        LocalDate lastMonth = now.toLocalDate().minusMonths(1);
                        startDateTime = lastMonth.withDayOfMonth(1).atStartOfDay();
                        endDateTime = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()).atTime(23, 59, 59);
                        break;
                    case "custom":
                        if (startDate != null && endDate != null) {
                            startDateTime = LocalDate.parse(startDate).atStartOfDay();
                            endDateTime = LocalDate.parse(endDate).atTime(23, 59, 59);
                        } else {
                            includeUser = false;
                        }
                        break;
                    default:
                        includeUser = false;
                }
                
                if (includeUser && startDateTime != null && endDateTime != null && user.getCreatedAt() != null) {
                    includeUser = !user.getCreatedAt().isBefore(startDateTime) && !user.getCreatedAt().isAfter(endDateTime);
                }
            }
            
            // User status filter
            if (includeUser && userStatus != null && !userStatus.isEmpty()) {
                if ("enabled".equals(userStatus)) {
                    includeUser = user.isEnabled();
                } else if ("disabled".equals(userStatus)) {
                    includeUser = !user.isEnabled();
                }
            }
            
            // Email verification filter
            if (includeUser && emailVerified != null && !emailVerified.isEmpty()) {
                if ("verified".equals(emailVerified)) {
                    includeUser = user.isEmailVerified();
                } else if ("unverified".equals(emailVerified)) {
                    includeUser = !user.isEmailVerified();
                }
            }
            
            // Role filter
            if (includeUser && userRole != null && !userRole.isEmpty()) {
                includeUser = user.getRoles().stream()
                    .anyMatch(role -> userRole.equals(role.getName()));
            }
            
            if (includeUser) {
                filteredUsers.add(user);
            }
        }
        
        return filteredUsers;
    }

    public void emailReport(String reportType, String dateRange, String startDate, String endDate,
                           String ticketStatus, String ticketPriority, String serviceType,
                           String userStatus, String emailVerified, String userRole,
                           String recipientEmail, String emailSubject, String emailMessage, String generatedBy) {
        try {
            byte[] reportData = generateReport(
                reportType, dateRange, startDate, endDate,
                ticketStatus, ticketPriority, serviceType,
                userStatus, emailVerified, userRole, generatedBy
            );
            
            String filename = getReportFilename(reportType);
            String subject = emailSubject != null ? emailSubject : reportType + " Report";
            String message = emailMessage != null ? emailMessage : "Please find the attached report.";
            
            emailService.sendReportEmail(recipientEmail, subject, message, reportData, filename);
            
        } catch (Exception e) {
            log.error("Error sending report email", e);
            throw new RuntimeException("Failed to send report email: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> getRecentReports() {
        // This is a placeholder implementation
        // In a real application, you would store report metadata in a database
        return new ArrayList<>();
    }

    public byte[] getReportById(Long reportId) {
        // This is a placeholder implementation
        // In a real application, you would retrieve the stored report from the database
        throw new UnsupportedOperationException("Report storage not implemented yet");
    }

    public String getReportFilenameById(Long reportId) {
        // This is a placeholder implementation
        // In a real application, you would retrieve the stored report filename from the database
        throw new UnsupportedOperationException("Report storage not implemented yet");
    }
}
