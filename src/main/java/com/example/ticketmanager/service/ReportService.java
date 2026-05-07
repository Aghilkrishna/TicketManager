package com.example.ticketmanager.service;

import com.example.ticketmanager.entity.*;
import com.example.ticketmanager.repository.TicketRepository;
import com.example.ticketmanager.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
            sheet.setDisplayGridlines(false);
            sheet.setDefaultRowHeightInPoints(20f);
            CellStyle metaStyle = createMetaStyle(workbook);
            CellStyle bodyEvenStyle = createBodyStyle(workbook, IndexedColors.WHITE);
            CellStyle bodyOddStyle = createBodyStyle(workbook, IndexedColors.GREY_25_PERCENT);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle moneyStyle = createMoneyStyle(workbook);
            
            // Create header style
            CellStyle headerStyle = createHeaderStyle(workbook);
            Map<String, CellStyle> ticketBadgeStyles = createTicketBadgeStyles(workbook);
            addReportTitle(sheet, "Tickets Report", "Scope: All filtered ticket records • Generated at: " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")), metaStyle);
            
            // Create headers
            Row headerRow = sheet.createRow(2);
            String[] headers = {
                "Ticket ID", "Parent Ticket ID", "Vendor User ID", "Created By ID", "Assigned To ID",
                "Customer Address Ref ID", "Title", "Description", "Service Type", "Status", "Priority",
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
            int rowNum = 3;
            for (Ticket ticket : tickets) {
                Row row = sheet.createRow(rowNum++);
                
                row.createCell(0).setCellValue(ticket.getId());
                row.createCell(1).setCellValue(ticket.getParentTicket() != null ? ticket.getParentTicket().getId() : 0L);
                row.createCell(2).setCellValue(ticket.getVendorUser() != null ? ticket.getVendorUser().getId() : 0L);
                row.createCell(3).setCellValue(ticket.getCreatedBy() != null ? ticket.getCreatedBy().getId() : 0L);
                row.createCell(4).setCellValue(ticket.getAssignedTo() != null ? ticket.getAssignedTo().getId() : 0L);
                row.createCell(5).setCellValue(ticket.getCustomerAddressReferenceId() != null ? ticket.getCustomerAddressReferenceId() : 0L);
                row.createCell(6).setCellValue(ticket.getTitle() != null ? ticket.getTitle() : "");
                row.createCell(7).setCellValue(ticket.getDescription() != null ? ticket.getDescription() : "");
                setBadgeCell(row.createCell(8), ticket.getServiceType() != null ? ticket.getServiceType().toString() : "", ticketBadgeStyles);
                setBadgeCell(row.createCell(9), ticket.getStatus() != null ? ticket.getStatus().toString() : "", ticketBadgeStyles);
                setBadgeCell(row.createCell(10), ticket.getPriority() != null ? ticket.getPriority().toString() : "", ticketBadgeStyles);
                row.createCell(11).setCellValue(ticket.getCustomerName() != null ? ticket.getCustomerName() : "");
                row.createCell(12).setCellValue(ticket.getCustomerEmail() != null ? ticket.getCustomerEmail() : "");
                row.createCell(13).setCellValue(ticket.getCustomerPhone() != null ? ticket.getCustomerPhone() : "");
                row.createCell(14).setCellValue(ticket.getAddress() != null ? ticket.getAddress() : "");
                row.createCell(15).setCellValue(ticket.getCustomerFlat() != null ? ticket.getCustomerFlat() : "");
                row.createCell(16).setCellValue(ticket.getCustomerStreet() != null ? ticket.getCustomerStreet() : "");
                row.createCell(17).setCellValue(ticket.getCustomerCity() != null ? ticket.getCustomerCity() : "");
                row.createCell(18).setCellValue(ticket.getCustomerState() != null ? ticket.getCustomerState() : "");
                row.createCell(19).setCellValue(ticket.getCustomerPincode() != null ? ticket.getCustomerPincode() : "");
                Cell estimatedCell = row.createCell(20);
                estimatedCell.setCellValue(ticket.getEstimatedCost() != null ? ticket.getEstimatedCost().doubleValue() : 0.0);
                estimatedCell.setCellStyle(moneyStyle);
                Cell actualCell = row.createCell(21);
                actualCell.setCellValue(ticket.getActualCost() != null ? ticket.getActualCost().doubleValue() : 0.0);
                actualCell.setCellStyle(moneyStyle);
                row.createCell(22).setCellValue(ticket.getBillingStatus() != null ? ticket.getBillingStatus().toString() : "");
                row.createCell(23).setCellValue(ticket.getScheduleDate() != null ? ticket.getScheduleDate().toString() : "");
                row.createCell(24).setCellValue(ticket.getCreatedBy() != null ? ticket.getCreatedBy().getUsername() : "");
                row.createCell(25).setCellValue(ticket.getAssignedTo() != null ? ticket.getAssignedTo().getUsername() : "");
                Cell createdAtCell = row.createCell(26);
                createdAtCell.setCellValue(ticket.getCreatedAt() != null ? ticket.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
                createdAtCell.setCellStyle(dateStyle);
                Cell updatedAtCell = row.createCell(27);
                updatedAtCell.setCellValue(ticket.getUpdatedAt() != null ? ticket.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
                updatedAtCell.setCellStyle(dateStyle);
                row.createCell(28).setCellValue(ticket.getSiteVisits() != null ? ticket.getSiteVisits() : 0);
                applyBodyStyle(row, bodyEvenStyle, bodyOddStyle, Set.of(8, 9, 10, 20, 21, 26, 27));
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.createFreezePane(0, 3);
            sheet.setAutoFilter(new CellRangeAddress(2, Math.max(2, rowNum - 1), 0, headers.length - 1));

            addTicketMetricsSheet(workbook, tickets);
            
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
            sheet.setDisplayGridlines(false);
            sheet.setDefaultRowHeightInPoints(20f);
            CellStyle metaStyle = createMetaStyle(workbook);
            CellStyle bodyEvenStyle = createBodyStyle(workbook, IndexedColors.WHITE);
            CellStyle bodyOddStyle = createBodyStyle(workbook, IndexedColors.GREY_25_PERCENT);
            CellStyle dateStyle = createDateStyle(workbook);
            
            // Create header style
            CellStyle headerStyle = createHeaderStyle(workbook);
            Map<String, CellStyle> userBadgeStyles = createUserBadgeStyles(workbook);
            addReportTitle(sheet, "Users Report", "Scope: All filtered user records • Generated at: " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")), metaStyle);
            
            // Create headers
            Row headerRow = sheet.createRow(2);
            String[] headers = {
                "User ID", "Username", "Email", "Phone", "First Name", "Last Name", "Company Name",
                "Contact Person", "GST Number", "Flat", "Building", "Area", "City", "State",
                "Country", "Pincode", "Status", "Email Verified", "Phone Verified", "Roles", "Role IDs", "Created At"
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Create data rows
            int rowNum = 3;
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
                setBadgeCell(row.createCell(16), user.isEnabled() ? "ENABLED" : "DISABLED", userBadgeStyles);
                setBadgeCell(row.createCell(17), user.isEmailVerified() ? "VERIFIED" : "UNVERIFIED", userBadgeStyles);
                setBadgeCell(row.createCell(18), user.isPhoneVerified() ? "VERIFIED" : "UNVERIFIED", userBadgeStyles);
                Set<Role> roles = user.getRoles();
                String primaryRole = (roles == null || roles.isEmpty()) ? "NO_ROLE" : roles.stream().map(Role::getName).sorted().findFirst().orElse("NO_ROLE");
                setBadgeCell(row.createCell(19), primaryRole, userBadgeStyles);
                row.createCell(20).setCellValue(roles == null ? "" : roles.stream().map(role -> String.valueOf(role.getId())).collect(Collectors.joining(", ")));
                Cell createdAtCell = row.createCell(21);
                createdAtCell.setCellValue(user.getCreatedAt() != null ? user.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
                createdAtCell.setCellStyle(dateStyle);
                applyBodyStyle(row, bodyEvenStyle, bodyOddStyle, Set.of(16, 17, 18, 19, 21));
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.createFreezePane(0, 3);
            sheet.setAutoFilter(new CellRangeAddress(2, Math.max(2, rowNum - 1), 0, headers.length - 1));

            addUserMetricsSheet(workbook, users);
            
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        
        return style;
    }

    private CellStyle createMetaStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 9);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        return style;
    }

    private CellStyle createBodyStyle(XSSFWorkbook workbook, IndexedColors fillColor) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(fillColor.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.GREY_80_PERCENT.getIndex());
        style.setFont(font);
        return style;
    }

    private CellStyle createDateStyle(XSSFWorkbook workbook) {
        CellStyle style = createBodyStyle(workbook, IndexedColors.WHITE);
        style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
        return style;
    }

    private CellStyle createMoneyStyle(XSSFWorkbook workbook) {
        CellStyle style = createBodyStyle(workbook, IndexedColors.WHITE);
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private void addReportTitle(Sheet sheet, String title, String subtitle, CellStyle metaStyle) {
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(28f);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        CellStyle titleStyle = createReportTitleStyle((XSSFWorkbook) sheet.getWorkbook());
        titleCell.setCellStyle(titleStyle);
        Row subtitleRow = sheet.createRow(1);
        subtitleRow.setHeightInPoints(20f);
        Cell subtitleCell = subtitleRow.createCell(0);
        subtitleCell.setCellValue(subtitle);
        subtitleCell.setCellStyle(metaStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 11));
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 11));
    }

    private CellStyle createReportTitleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        return style;
    }

    private void applyBodyStyle(Row row, CellStyle evenStyle, CellStyle oddStyle, Set<Integer> skipColumns) {
        CellStyle selectedStyle = (row.getRowNum() % 2 == 0) ? evenStyle : oddStyle;
        for (Cell cell : row) {
            if (skipColumns.contains(cell.getColumnIndex())) {
                continue;
            }
            cell.setCellStyle(selectedStyle);
        }
    }

    private Map<String, CellStyle> createTicketBadgeStyles(XSSFWorkbook workbook) {
        Map<String, CellStyle> styles = new LinkedHashMap<>();
        styles.put("LEADS", badgeStyle(workbook, IndexedColors.LAVENDER, IndexedColors.VIOLET));
        styles.put("INSTALLATION", badgeStyle(workbook, IndexedColors.LIGHT_TURQUOISE, IndexedColors.DARK_TEAL));
        styles.put("SERVICE", badgeStyle(workbook, IndexedColors.LIGHT_ORANGE, IndexedColors.DARK_YELLOW));
        styles.put("AMC", badgeStyle(workbook, IndexedColors.PALE_BLUE, IndexedColors.BLUE));
        styles.put("SITE_VISIT", badgeStyle(workbook, IndexedColors.LIGHT_GREEN, IndexedColors.GREEN));
        styles.put("OPEN", badgeStyle(workbook, IndexedColors.PALE_BLUE, IndexedColors.BLUE));
        styles.put("SITE_VISITED", badgeStyle(workbook, IndexedColors.LIGHT_GREEN, IndexedColors.DARK_GREEN));
        styles.put("IN_PROGRESS", badgeStyle(workbook, IndexedColors.LIGHT_TURQUOISE, IndexedColors.DARK_TEAL));
        styles.put("ON_HOLD", badgeStyle(workbook, IndexedColors.LEMON_CHIFFON, IndexedColors.BROWN));
        styles.put("QUOTED", badgeStyle(workbook, IndexedColors.LIGHT_CORNFLOWER_BLUE, IndexedColors.DARK_BLUE));
        styles.put("RESOLVED", badgeStyle(workbook, IndexedColors.LIGHT_GREEN, IndexedColors.GREEN));
        styles.put("CLOSED", badgeStyle(workbook, IndexedColors.GREY_25_PERCENT, IndexedColors.GREY_80_PERCENT));
        styles.put("CANCELLED", badgeStyle(workbook, IndexedColors.ROSE, IndexedColors.DARK_RED));
        styles.put("LOW", badgeStyle(workbook, IndexedColors.LIGHT_GREEN, IndexedColors.GREEN));
        styles.put("MEDIUM", badgeStyle(workbook, IndexedColors.LEMON_CHIFFON, IndexedColors.BROWN));
        styles.put("HIGH", badgeStyle(workbook, IndexedColors.LIGHT_ORANGE, IndexedColors.DARK_YELLOW));
        styles.put("CRITICAL", badgeStyle(workbook, IndexedColors.ROSE, IndexedColors.DARK_RED));
        return styles;
    }

    private Map<String, CellStyle> createUserBadgeStyles(XSSFWorkbook workbook) {
        Map<String, CellStyle> styles = new LinkedHashMap<>();
        styles.put("ENABLED", badgeStyle(workbook, IndexedColors.LIGHT_GREEN, IndexedColors.GREEN));
        styles.put("DISABLED", badgeStyle(workbook, IndexedColors.ROSE, IndexedColors.DARK_RED));
        styles.put("VERIFIED", badgeStyle(workbook, IndexedColors.LIGHT_GREEN, IndexedColors.GREEN));
        styles.put("UNVERIFIED", badgeStyle(workbook, IndexedColors.LEMON_CHIFFON, IndexedColors.BROWN));
        styles.put("ROLE_ADMIN", badgeStyle(workbook, IndexedColors.LIGHT_BLUE, IndexedColors.DARK_BLUE));
        styles.put("ROLE_AGENT", badgeStyle(workbook, IndexedColors.LIGHT_TURQUOISE, IndexedColors.DARK_TEAL));
        styles.put("ROLE_VENDOR", badgeStyle(workbook, IndexedColors.LIGHT_ORANGE, IndexedColors.DARK_YELLOW));
        styles.put("NO_ROLE", badgeStyle(workbook, IndexedColors.GREY_25_PERCENT, IndexedColors.GREY_80_PERCENT));
        return styles;
    }

    private CellStyle badgeStyle(XSSFWorkbook workbook, IndexedColors bg, IndexedColors fg) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(bg.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.NONE);
        style.setBorderBottom(BorderStyle.NONE);
        style.setBorderLeft(BorderStyle.NONE);
        style.setBorderRight(BorderStyle.NONE);
        style.setWrapText(false);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(fg.getIndex());
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private void setBadgeCell(Cell cell, String value, Map<String, CellStyle> styles) {
        cell.setCellValue(value == null ? "" : value);
        if (value != null && styles.containsKey(value)) {
            cell.setCellStyle(styles.get(value));
        }
    }

    private void addTicketMetricsSheet(XSSFWorkbook workbook, List<Ticket> tickets) {
        Sheet metrics = workbook.createSheet("Ticket Metrics");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle metricHeaderStyle = createMetricHeaderStyle(workbook);
        CellStyle metaStyle = createMetaStyle(workbook);

        addReportTitle(metrics, "Ticket Metrics Dashboard", "Visual breakdown by service type, status and priority", metaStyle);
        metrics.setColumnWidth(0, 26 * 256);
        metrics.setColumnWidth(1, 12 * 256);
        metrics.setColumnWidth(2, 2 * 256);
        for (int col = 3; col <= 11; col++) {
            metrics.setColumnWidth(col, 12 * 256);
        }

        int rowIdx = 3;
        LinkedHashMap<String, Integer> serviceTypeCounts = countBy(tickets, ticket -> ticket.getServiceType() == null ? "UNKNOWN" : ticket.getServiceType().name());
        SectionBounds serviceSection = writeMetricSection(metrics, rowIdx, "Service Type Counts", serviceTypeCounts, headerStyle, metricHeaderStyle, "Service Type", "Count");
        addBarChart(metrics, serviceSection.dataStartRow, serviceSection.dataEndRow, 3, serviceSection.titleRow, "Service Type Distribution");

        rowIdx = serviceSection.nextRow + 2;
        LinkedHashMap<String, Integer> statusCounts = countBy(tickets, ticket -> ticket.getStatus() == null ? "UNKNOWN" : ticket.getStatus().name());
        SectionBounds statusSection = writeMetricSection(metrics, rowIdx, "Status Counts", statusCounts, headerStyle, metricHeaderStyle, "Status", "Count");
        addBarChart(metrics, statusSection.dataStartRow, statusSection.dataEndRow, 3, statusSection.titleRow, "Status Distribution");

        rowIdx = statusSection.nextRow + 2;
        LinkedHashMap<String, Integer> priorityCounts = countBy(tickets, ticket -> ticket.getPriority() == null ? "UNKNOWN" : ticket.getPriority().name());
        SectionBounds prioritySection = writeMetricSection(metrics, rowIdx, "Priority Counts", priorityCounts, headerStyle, metricHeaderStyle, "Priority", "Count");
        addBarChart(metrics, prioritySection.dataStartRow, prioritySection.dataEndRow, 3, prioritySection.titleRow, "Priority Distribution");

        rowIdx = prioritySection.nextRow + 2;
        Row totalHeader = metrics.createRow(rowIdx++);
        Cell totalCell = totalHeader.createCell(0);
        totalCell.setCellValue("Total Tickets");
        totalCell.setCellStyle(headerStyle);
        Row totalRow = metrics.createRow(rowIdx++);
        totalRow.createCell(0).setCellValue(tickets.size());

        for (int i = 0; i < 12; i++) {
            metrics.autoSizeColumn(i);
        }
        metrics.createFreezePane(0, 3);
    }

    private void addUserMetricsSheet(XSSFWorkbook workbook, List<AppUser> users) {
        Sheet metrics = workbook.createSheet("User Metrics");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle metricHeaderStyle = createMetricHeaderStyle(workbook);
        CellStyle metaStyle = createMetaStyle(workbook);

        addReportTitle(metrics, "User Metrics Dashboard", "Visual breakdown by user status, verification and role", metaStyle);
        metrics.setColumnWidth(0, 26 * 256);
        metrics.setColumnWidth(1, 12 * 256);
        metrics.setColumnWidth(2, 2 * 256);
        for (int col = 3; col <= 11; col++) {
            metrics.setColumnWidth(col, 12 * 256);
        }

        int rowIdx = 3;
        LinkedHashMap<String, Integer> userStatusCounts = countBy(users, user -> user.isEnabled() ? "ENABLED" : "DISABLED");
        SectionBounds userStatusSection = writeMetricSection(metrics, rowIdx, "User Status Counts", userStatusCounts, headerStyle, metricHeaderStyle, "User Status", "Count");
        addBarChart(metrics, userStatusSection.dataStartRow, userStatusSection.dataEndRow, 3, userStatusSection.titleRow, "User Status Distribution");

        rowIdx = userStatusSection.nextRow + 2;
        LinkedHashMap<String, Integer> emailStatusCounts = countBy(users, user -> user.isEmailVerified() ? "VERIFIED" : "UNVERIFIED");
        SectionBounds emailSection = writeMetricSection(metrics, rowIdx, "Email Verification Counts", emailStatusCounts, headerStyle, metricHeaderStyle, "Email Verification", "Count");
        addBarChart(metrics, emailSection.dataStartRow, emailSection.dataEndRow, 3, emailSection.titleRow, "Email Verification Distribution");

        rowIdx = emailSection.nextRow + 2;
        LinkedHashMap<String, Integer> roleCounts = countRoles(users);
        SectionBounds roleSection = writeMetricSection(metrics, rowIdx, "Role Counts", roleCounts, headerStyle, metricHeaderStyle, "Role", "Count");
        addBarChart(metrics, roleSection.dataStartRow, roleSection.dataEndRow, 3, roleSection.titleRow, "Role Distribution");

        rowIdx = roleSection.nextRow + 2;
        Row totalHeader = metrics.createRow(rowIdx++);
        Cell totalCell = totalHeader.createCell(0);
        totalCell.setCellValue("Total Users");
        totalCell.setCellStyle(headerStyle);
        Row totalRow = metrics.createRow(rowIdx++);
        totalRow.createCell(0).setCellValue(users.size());

        for (int i = 0; i < 12; i++) {
            metrics.autoSizeColumn(i);
        }
        metrics.createFreezePane(0, 3);
    }

    private CellStyle createMetricHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private <T> LinkedHashMap<String, Integer> countBy(List<T> values, java.util.function.Function<T, String> classifier) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (T value : values) {
            String key = classifier.apply(value);
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        return counts;
    }

    private LinkedHashMap<String, Integer> countRoles(List<AppUser> users) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (AppUser user : users) {
            if (user.getRoles() == null || user.getRoles().isEmpty()) {
                counts.put("NO_ROLE", counts.getOrDefault("NO_ROLE", 0) + 1);
                continue;
            }
            for (Role role : user.getRoles()) {
                String roleName = role == null || role.getName() == null ? "UNKNOWN_ROLE" : role.getName();
                counts.put(roleName, counts.getOrDefault(roleName, 0) + 1);
            }
        }
        return counts;
    }

    private SectionBounds writeMetricSection(Sheet sheet, int startRow, String title, LinkedHashMap<String, Integer> data,
                                             CellStyle headerStyle, CellStyle metricHeaderStyle,
                                             String labelHeader, String valueHeader) {
        int titleRowIdx = startRow;
        Row titleRow = sheet.createRow(startRow++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(metricHeaderStyle);

        Row headerRow = sheet.createRow(startRow++);
        Cell labelCell = headerRow.createCell(0);
        labelCell.setCellValue(labelHeader);
        labelCell.setCellStyle(headerStyle);
        Cell valueCell = headerRow.createCell(1);
        valueCell.setCellValue(valueHeader);
        valueCell.setCellStyle(headerStyle);

        int dataStartRow = startRow;
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            Row row = sheet.createRow(startRow++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
        }
        return new SectionBounds(titleRowIdx, dataStartRow, startRow - 1, startRow);
    }

    private int addBarChart(Sheet sheet, int dataStartRow, int dataEndRow, int chartLeftCol, int chartTopRow, String title) {
        if (dataEndRow < dataStartRow) {
            return chartTopRow;
        }
        XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();
        int height = 12;
        int width = 8;
        var anchor = drawing.createAnchor(0, 0, 0, 0, chartLeftCol, chartTopRow, chartLeftCol + width, chartTopRow + height);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);
        chart.getOrAddLegend().setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setCrosses(org.apache.poi.xddf.usermodel.chart.AxisCrosses.AUTO_ZERO);

        XDDFDataSource<String> categories = org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory.fromStringCellRange(
                (org.apache.poi.xssf.usermodel.XSSFSheet) sheet,
                new CellRangeAddress(dataStartRow, dataEndRow, 0, 0));
        XDDFNumericalDataSource<Double> values = org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory.fromNumericCellRange(
                (org.apache.poi.xssf.usermodel.XSSFSheet) sheet,
                new CellRangeAddress(dataStartRow, dataEndRow, 1, 1));

        XDDFChartData data = chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        XDDFBarChartData bar = (XDDFBarChartData) data;
        bar.setBarDirection(BarDirection.BAR);
        bar.setVaryColors(true);
        XDDFChartData.Series series = bar.addSeries(categories, values);
        series.setTitle("Count", null);
        chart.plot(data);
        return chartTopRow + height;
    }

    private record SectionBounds(int titleRow, int dataStartRow, int dataEndRow, int nextRow) {}

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

    @Transactional(readOnly = true)
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

    public void sendGeneratedReport(String recipientEmail, String emailSubject, String emailMessage, byte[] reportData, String filename) {
        try {
            String subject = emailSubject != null ? emailSubject : "Report";
            String message = emailMessage != null ? emailMessage : "Please find the attached report.";
            emailService.sendReportEmail(recipientEmail, subject, message, reportData, filename);
        } catch (Exception e) {
            log.error("Error sending generated report email", e);
            throw new RuntimeException("Failed to send generated report email: " + e.getMessage(), e);
        }
    }

    public void sendGeneratedReportsInSingleEmail(String recipientEmail, String emailSubject, String emailMessage,
                                                  byte[] ticketsReport, String ticketsFilename,
                                                  byte[] usersReport, String usersFilename) {
        try {
            String subject = emailSubject != null ? emailSubject : "Daily Reports";
            String message = emailMessage != null ? emailMessage : "Please find the attached reports.";
            Map<String, byte[]> attachments = new LinkedHashMap<>();
            attachments.put(ticketsFilename, ticketsReport);
            attachments.put(usersFilename, usersReport);
            emailService.sendReportEmailWithAttachments(recipientEmail, subject, message, attachments);
        } catch (Exception e) {
            log.error("Error sending combined reports email", e);
            throw new RuntimeException("Failed to send combined reports email: " + e.getMessage(), e);
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
