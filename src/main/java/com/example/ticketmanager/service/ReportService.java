package com.example.ticketmanager.service;

import com.example.ticketmanager.entity.*;
import com.example.ticketmanager.repository.TicketRepository;
import com.example.ticketmanager.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    // ── Power BI / modern SaaS color palette ─────────────────────────────────
    private static final byte[] RGB_NAVY      = {(byte)0x1F, (byte)0x38, (byte)0x64};
    private static final byte[] RGB_BLUE      = {(byte)0x2E, (byte)0x75, (byte)0xB6};
    private static final byte[] RGB_MED_BLUE  = {(byte)0x44, (byte)0x72, (byte)0xC4};
    private static final byte[] RGB_LITE_BLUE = {(byte)0xEE, (byte)0xF4, (byte)0xFB};
    private static final byte[] RGB_WHITE     = {(byte)0xFF, (byte)0xFF, (byte)0xFF};
    private static final byte[] RGB_ROW_ALT   = {(byte)0xF5, (byte)0xF8, (byte)0xFC};
    private static final byte[] RGB_BORDER    = {(byte)0xD6, (byte)0xDC, (byte)0xE4};
    private static final byte[] RGB_TEXT_DARK = {(byte)0x26, (byte)0x26, (byte)0x26};

    // Badge palette
    private static final byte[] RGB_GREEN_BG  = {(byte)0xE2, (byte)0xEF, (byte)0xDA};
    private static final byte[] RGB_GREEN_FG  = {(byte)0x37, (byte)0x5C, (byte)0x23};
    private static final byte[] RGB_RED_BG    = {(byte)0xFF, (byte)0xCC, (byte)0xCC};
    private static final byte[] RGB_RED_FG    = {(byte)0xC0, (byte)0x00, (byte)0x00};
    private static final byte[] RGB_AMBER_BG  = {(byte)0xFF, (byte)0xF2, (byte)0xCC};
    private static final byte[] RGB_AMBER_FG  = {(byte)0x80, (byte)0x60, (byte)0x00};
    private static final byte[] RGB_ORANGE_BG = {(byte)0xFC, (byte)0xE4, (byte)0xD6};
    private static final byte[] RGB_ORANGE_FG = {(byte)0x84, (byte)0x32, (byte)0x00};
    private static final byte[] RGB_PURPLE_BG = {(byte)0xE8, (byte)0xD5, (byte)0xF5};
    private static final byte[] RGB_PURPLE_FG = {(byte)0x4B, (byte)0x0B, (byte)0x6B};
    private static final byte[] RGB_TEAL_BG   = {(byte)0xCC, (byte)0xF2, (byte)0xF5};
    private static final byte[] RGB_TEAL_FG   = {(byte)0x00, (byte)0x5E, (byte)0x6B};
    private static final byte[] RGB_GRAY_BG   = {(byte)0xF2, (byte)0xF2, (byte)0xF2};
    private static final byte[] RGB_GRAY_FG   = {(byte)0x59, (byte)0x59, (byte)0x59};
    private static final byte[] RGB_INDIGO_BG = {(byte)0xDB, (byte)0xE5, (byte)0xF1};
    private static final byte[] RGB_INDIGO_FG = {(byte)0x17, (byte)0x37, (byte)0x5E};

    // Chart dimensions — each chart is CHART_H rows tall; sections are padded
    // so the next section always starts below the previous chart.
    private static final int CHART_H   = 15;
    private static final int CHART_W   = 9;
    private static final int CHART_COL = 3;

    private XSSFColor rgb(byte[] b) {
        return new XSSFColor(b, null);
    }

    // ── Public API ────────────────────────────────────────────────────────────

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

    // ── Report generation ─────────────────────────────────────────────────────

    private byte[] generateTicketsReport(String dateRange, String startDate, String endDate,
                                         String ticketStatus, String ticketPriority, String serviceType) throws IOException {
        List<Ticket> tickets = getFilteredTickets(dateRange, startDate, endDate, ticketStatus, ticketPriority, serviceType);

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("Tickets Report");
            sheet.setDisplayGridlines(false);
            sheet.setDefaultRowHeightInPoints(22f);

            XSSFCellStyle metaStyle      = createMetaStyle(wb);
            XSSFCellStyle bodyEvenStyle  = createBodyStyle(wb, false);
            XSSFCellStyle bodyOddStyle   = createBodyStyle(wb, true);
            XSSFCellStyle dateStyle      = createDateStyle(wb);
            XSSFCellStyle moneyStyle     = createMoneyStyle(wb);
            XSSFCellStyle headerStyle    = createHeaderStyle(wb);
            Map<String, XSSFCellStyle> badgeStyles = createTicketBadgeStyles(wb);

            String[] headers = {
                "Ticket ID", "Parent Ticket ID", "Vendor User ID", "Created By ID", "Assigned To ID",
                "Customer Address Ref ID", "Title", "Description", "Service Type", "Status", "Priority",
                "Customer Name", "Customer Email", "Customer Phone", "Address",
                "Customer Flat", "Customer Street", "Customer City", "Customer State", "Customer Pincode",
                "Estimated Cost", "Actual Cost", "Billing Status", "Schedule Date",
                "Created By", "Assigned To", "Created At", "Updated At", "Site Visits"
            };

            addReportTitle(sheet, "Tickets Report",
                "Scope: All filtered ticket records  •  Generated: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")),
                metaStyle, headers.length - 1);

            Row headerRow = sheet.createRow(2);
            headerRow.setHeightInPoints(26f);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 3;
            for (Ticket ticket : tickets) {
                Row row = sheet.createRow(rowNum++);
                row.setHeightInPoints(22f);

                row.createCell(0).setCellValue(ticket.getId());
                row.createCell(1).setCellValue(ticket.getParentTicket() != null ? ticket.getParentTicket().getId() : 0L);
                row.createCell(2).setCellValue(ticket.getVendorUser() != null ? ticket.getVendorUser().getId() : 0L);
                row.createCell(3).setCellValue(ticket.getCreatedBy() != null ? ticket.getCreatedBy().getId() : 0L);
                row.createCell(4).setCellValue(ticket.getAssignedTo() != null ? ticket.getAssignedTo().getId() : 0L);
                row.createCell(5).setCellValue(ticket.getCustomerAddressReferenceId() != null ? ticket.getCustomerAddressReferenceId() : 0L);
                row.createCell(6).setCellValue(ticket.getTitle() != null ? ticket.getTitle() : "");
                row.createCell(7).setCellValue(ticket.getDescription() != null ? ticket.getDescription() : "");
                setBadgeCell(row.createCell(8),  ticket.getServiceType() != null ? ticket.getServiceType().toString() : "", badgeStyles);
                setBadgeCell(row.createCell(9),  ticket.getStatus()      != null ? ticket.getStatus().toString()      : "", badgeStyles);
                setBadgeCell(row.createCell(10), ticket.getPriority()    != null ? ticket.getPriority().toString()    : "", badgeStyles);
                row.createCell(11).setCellValue(ticket.getCustomerName()    != null ? ticket.getCustomerName()    : "");
                row.createCell(12).setCellValue(ticket.getCustomerEmail()   != null ? ticket.getCustomerEmail()   : "");
                row.createCell(13).setCellValue(ticket.getCustomerPhone()   != null ? ticket.getCustomerPhone()   : "");
                row.createCell(14).setCellValue(ticket.getAddress()         != null ? ticket.getAddress()         : "");
                row.createCell(15).setCellValue(ticket.getCustomerFlat()    != null ? ticket.getCustomerFlat()    : "");
                row.createCell(16).setCellValue(ticket.getCustomerStreet()  != null ? ticket.getCustomerStreet()  : "");
                row.createCell(17).setCellValue(ticket.getCustomerCity()    != null ? ticket.getCustomerCity()    : "");
                row.createCell(18).setCellValue(ticket.getCustomerState()   != null ? ticket.getCustomerState()   : "");
                row.createCell(19).setCellValue(ticket.getCustomerPincode() != null ? ticket.getCustomerPincode() : "");

                Cell estCell = row.createCell(20);
                estCell.setCellValue(ticket.getEstimatedCost() != null ? ticket.getEstimatedCost().doubleValue() : 0.0);
                estCell.setCellStyle(moneyStyle);

                Cell actCell = row.createCell(21);
                actCell.setCellValue(ticket.getActualCost() != null ? ticket.getActualCost().doubleValue() : 0.0);
                actCell.setCellStyle(moneyStyle);

                row.createCell(22).setCellValue(ticket.getBillingStatus() != null ? ticket.getBillingStatus().toString() : "");
                row.createCell(23).setCellValue(ticket.getScheduleDate()  != null ? ticket.getScheduleDate().toString()  : "");
                row.createCell(24).setCellValue(ticket.getCreatedBy()     != null ? ticket.getCreatedBy().getUsername()  : "");
                row.createCell(25).setCellValue(ticket.getAssignedTo()    != null ? ticket.getAssignedTo().getUsername() : "");

                Cell caCell = row.createCell(26);
                caCell.setCellValue(ticket.getCreatedAt() != null ? ticket.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
                caCell.setCellStyle(dateStyle);

                Cell uaCell = row.createCell(27);
                uaCell.setCellValue(ticket.getUpdatedAt() != null ? ticket.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
                uaCell.setCellStyle(dateStyle);

                row.createCell(28).setCellValue(ticket.getSiteVisits() != null ? ticket.getSiteVisits() : 0);

                applyBodyStyle(row, bodyEvenStyle, bodyOddStyle, Set.of(8, 9, 10, 20, 21, 26, 27));
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            sheet.createFreezePane(0, 3);
            sheet.setAutoFilter(new CellRangeAddress(2, Math.max(2, rowNum - 1), 0, headers.length - 1));

            addTicketMetricsSheet(wb, tickets);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] generateUsersReport(String dateRange, String startDate, String endDate,
                                        String userStatus, String emailVerified, String userRole) throws IOException {
        List<AppUser> users = getFilteredUsers(dateRange, startDate, endDate, userStatus, emailVerified, userRole);

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("Users Report");
            sheet.setDisplayGridlines(false);
            sheet.setDefaultRowHeightInPoints(22f);

            XSSFCellStyle metaStyle     = createMetaStyle(wb);
            XSSFCellStyle bodyEvenStyle = createBodyStyle(wb, false);
            XSSFCellStyle bodyOddStyle  = createBodyStyle(wb, true);
            XSSFCellStyle dateStyle     = createDateStyle(wb);
            XSSFCellStyle headerStyle   = createHeaderStyle(wb);
            Map<String, XSSFCellStyle> badgeStyles = createUserBadgeStyles(wb);

            String[] headers = {
                "User ID", "Username", "Email", "Phone", "First Name", "Last Name", "Company Name",
                "Contact Person", "GST Number", "Flat", "Building", "Area", "City", "State",
                "Country", "Pincode", "Status", "Email Verified", "Phone Verified", "Roles", "Role IDs", "Created At"
            };

            addReportTitle(sheet, "Users Report",
                "Scope: All filtered user records  •  Generated: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")),
                metaStyle, headers.length - 1);

            Row headerRow = sheet.createRow(2);
            headerRow.setHeightInPoints(26f);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 3;
            for (AppUser user : users) {
                Row row = sheet.createRow(rowNum++);
                row.setHeightInPoints(22f);

                row.createCell(0).setCellValue(user.getId());
                row.createCell(1).setCellValue(user.getUsername()     != null ? user.getUsername()     : "");
                row.createCell(2).setCellValue(user.getEmail()        != null ? user.getEmail()        : "");
                row.createCell(3).setCellValue(user.getPhone()        != null ? user.getPhone()        : "");
                row.createCell(4).setCellValue(user.getFirstName()    != null ? user.getFirstName()    : "");
                row.createCell(5).setCellValue(user.getLastName()     != null ? user.getLastName()     : "");
                row.createCell(6).setCellValue(user.getCompanyName()  != null ? user.getCompanyName()  : "");
                row.createCell(7).setCellValue(user.getContactPerson()!= null ? user.getContactPerson(): "");
                row.createCell(8).setCellValue(user.getGstNumber()    != null ? user.getGstNumber()    : "");
                row.createCell(9).setCellValue(user.getFlat()         != null ? user.getFlat()         : "");
                row.createCell(10).setCellValue(user.getBuilding()    != null ? user.getBuilding()     : "");
                row.createCell(11).setCellValue(user.getArea()        != null ? user.getArea()         : "");
                row.createCell(12).setCellValue(user.getCity()        != null ? user.getCity()         : "");
                row.createCell(13).setCellValue(user.getState()       != null ? user.getState()        : "");
                row.createCell(14).setCellValue(user.getCountry()     != null ? user.getCountry()      : "");
                row.createCell(15).setCellValue(user.getPincode()     != null ? user.getPincode()      : "");

                setBadgeCell(row.createCell(16), user.isEnabled()       ? "ENABLED"    : "DISABLED",   badgeStyles);
                setBadgeCell(row.createCell(17), user.isEmailVerified() ? "VERIFIED"   : "UNVERIFIED", badgeStyles);
                setBadgeCell(row.createCell(18), user.isPhoneVerified() ? "VERIFIED"   : "UNVERIFIED", badgeStyles);

                Set<Role> roles = user.getRoles();
                String primaryRole = (roles == null || roles.isEmpty())
                    ? "NO_ROLE"
                    : roles.stream().map(Role::getName).sorted().findFirst().orElse("NO_ROLE");
                setBadgeCell(row.createCell(19), primaryRole, badgeStyles);
                row.createCell(20).setCellValue(roles == null ? "" :
                    roles.stream().map(r -> String.valueOf(r.getId())).collect(Collectors.joining(", ")));

                Cell caCell = row.createCell(21);
                caCell.setCellValue(user.getCreatedAt() != null ? user.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
                caCell.setCellStyle(dateStyle);

                applyBodyStyle(row, bodyEvenStyle, bodyOddStyle, Set.of(16, 17, 18, 19, 21));
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            sheet.createFreezePane(0, 3);
            sheet.setAutoFilter(new CellRangeAddress(2, Math.max(2, rowNum - 1), 0, headers.length - 1));

            addUserMetricsSheet(wb, users);

            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── Metrics sheets ────────────────────────────────────────────────────────

    private void addTicketMetricsSheet(XSSFWorkbook wb, List<Ticket> tickets) {
        XSSFSheet metrics = wb.createSheet("Ticket Metrics");
        metrics.setDisplayGridlines(false);

        XSSFCellStyle headerStyle   = createHeaderStyle(wb);
        XSSFCellStyle sectionStyle  = createSectionTitleStyle(wb);
        XSSFCellStyle metaStyle     = createMetaStyle(wb);
        XSSFCellStyle kpiLabelStyle = createKpiLabelStyle(wb);
        XSSFCellStyle kpiValueStyle = createKpiValueStyle(wb);
        XSSFCellStyle dataEven      = createBodyStyle(wb, false);
        XSSFCellStyle dataOdd       = createBodyStyle(wb, true);

        addReportTitle(metrics, "Ticket Metrics Dashboard",
            "Visual breakdown by service type, status and priority", metaStyle, 12);

        metrics.setColumnWidth(0, 28 * 256);
        metrics.setColumnWidth(1, 14 * 256);
        metrics.setColumnWidth(2,  3 * 256);
        for (int c = CHART_COL; c <= CHART_COL + CHART_W + 1; c++) metrics.setColumnWidth(c, 11 * 256);

        // KPI summary band
        Row kpiRow = metrics.createRow(2);
        kpiRow.setHeightInPoints(32f);
        Cell kpiLabel = kpiRow.createCell(0);
        kpiLabel.setCellValue("Total Tickets");
        kpiLabel.setCellStyle(kpiLabelStyle);
        Cell kpiValue = kpiRow.createCell(1);
        kpiValue.setCellValue(tickets.size());
        kpiValue.setCellStyle(kpiValueStyle);

        int rowIdx = 4; // leave one spacer row after KPI

        // Section 1 — Service Type
        SectionBounds s1 = writeMetricSection(metrics, rowIdx, "By Service Type",
            countBy(tickets, t -> t.getServiceType() == null ? "UNKNOWN" : t.getServiceType().name()),
            sectionStyle, headerStyle, dataEven, dataOdd, "Service Type", "Count");
        addBarChart(metrics, s1.dataStartRow(), s1.dataEndRow(),
            CHART_COL, s1.titleRow(), CHART_COL + CHART_W, s1.titleRow() + CHART_H,
            "Service Type Distribution");
        rowIdx = Math.max(s1.nextRow(), s1.titleRow() + CHART_H) + 3;

        // Section 2 — Status
        SectionBounds s2 = writeMetricSection(metrics, rowIdx, "By Status",
            countBy(tickets, t -> t.getStatus() == null ? "UNKNOWN" : t.getStatus().name()),
            sectionStyle, headerStyle, dataEven, dataOdd, "Status", "Count");
        addBarChart(metrics, s2.dataStartRow(), s2.dataEndRow(),
            CHART_COL, s2.titleRow(), CHART_COL + CHART_W, s2.titleRow() + CHART_H,
            "Status Distribution");
        rowIdx = Math.max(s2.nextRow(), s2.titleRow() + CHART_H) + 3;

        // Section 3 — Priority
        SectionBounds s3 = writeMetricSection(metrics, rowIdx, "By Priority",
            countBy(tickets, t -> t.getPriority() == null ? "UNKNOWN" : t.getPriority().name()),
            sectionStyle, headerStyle, dataEven, dataOdd, "Priority", "Count");
        addBarChart(metrics, s3.dataStartRow(), s3.dataEndRow(),
            CHART_COL, s3.titleRow(), CHART_COL + CHART_W, s3.titleRow() + CHART_H,
            "Priority Distribution");

        metrics.createFreezePane(0, 2);
    }

    private void addUserMetricsSheet(XSSFWorkbook wb, List<AppUser> users) {
        XSSFSheet metrics = wb.createSheet("User Metrics");
        metrics.setDisplayGridlines(false);

        XSSFCellStyle headerStyle   = createHeaderStyle(wb);
        XSSFCellStyle sectionStyle  = createSectionTitleStyle(wb);
        XSSFCellStyle metaStyle     = createMetaStyle(wb);
        XSSFCellStyle kpiLabelStyle = createKpiLabelStyle(wb);
        XSSFCellStyle kpiValueStyle = createKpiValueStyle(wb);
        XSSFCellStyle dataEven      = createBodyStyle(wb, false);
        XSSFCellStyle dataOdd       = createBodyStyle(wb, true);

        addReportTitle(metrics, "User Metrics Dashboard",
            "Visual breakdown by user status, verification and role", metaStyle, 12);

        metrics.setColumnWidth(0, 28 * 256);
        metrics.setColumnWidth(1, 14 * 256);
        metrics.setColumnWidth(2,  3 * 256);
        for (int c = CHART_COL; c <= CHART_COL + CHART_W + 1; c++) metrics.setColumnWidth(c, 11 * 256);

        Row kpiRow = metrics.createRow(2);
        kpiRow.setHeightInPoints(32f);
        Cell kpiLabel = kpiRow.createCell(0);
        kpiLabel.setCellValue("Total Users");
        kpiLabel.setCellStyle(kpiLabelStyle);
        Cell kpiValue = kpiRow.createCell(1);
        kpiValue.setCellValue(users.size());
        kpiValue.setCellStyle(kpiValueStyle);

        int rowIdx = 4;

        SectionBounds s1 = writeMetricSection(metrics, rowIdx, "By User Status",
            countBy(users, u -> u.isEnabled() ? "ENABLED" : "DISABLED"),
            sectionStyle, headerStyle, dataEven, dataOdd, "User Status", "Count");
        addBarChart(metrics, s1.dataStartRow(), s1.dataEndRow(),
            CHART_COL, s1.titleRow(), CHART_COL + CHART_W, s1.titleRow() + CHART_H,
            "User Status Distribution");
        rowIdx = Math.max(s1.nextRow(), s1.titleRow() + CHART_H) + 3;

        SectionBounds s2 = writeMetricSection(metrics, rowIdx, "By Email Verification",
            countBy(users, u -> u.isEmailVerified() ? "VERIFIED" : "UNVERIFIED"),
            sectionStyle, headerStyle, dataEven, dataOdd, "Email Status", "Count");
        addBarChart(metrics, s2.dataStartRow(), s2.dataEndRow(),
            CHART_COL, s2.titleRow(), CHART_COL + CHART_W, s2.titleRow() + CHART_H,
            "Email Verification Distribution");
        rowIdx = Math.max(s2.nextRow(), s2.titleRow() + CHART_H) + 3;

        SectionBounds s3 = writeMetricSection(metrics, rowIdx, "By Role",
            countRoles(users),
            sectionStyle, headerStyle, dataEven, dataOdd, "Role", "Count");
        addBarChart(metrics, s3.dataStartRow(), s3.dataEndRow(),
            CHART_COL, s3.titleRow(), CHART_COL + CHART_W, s3.titleRow() + CHART_H,
            "Role Distribution");

        metrics.createFreezePane(0, 2);
    }

    // ── Section writer ────────────────────────────────────────────────────────

    private SectionBounds writeMetricSection(XSSFSheet sheet, int startRow, String title,
                                              LinkedHashMap<String, Integer> data,
                                              XSSFCellStyle sectionStyle, XSSFCellStyle headerStyle,
                                              XSSFCellStyle dataEven, XSSFCellStyle dataOdd,
                                              String labelHeader, String valueHeader) {
        int titleRowIdx = startRow;

        Row titleRow = sheet.createRow(startRow++);
        titleRow.setHeightInPoints(22f);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(sectionStyle);

        Row hdrRow = sheet.createRow(startRow++);
        hdrRow.setHeightInPoints(20f);
        Cell lhCell = hdrRow.createCell(0);
        lhCell.setCellValue(labelHeader);
        lhCell.setCellStyle(headerStyle);
        Cell vhCell = hdrRow.createCell(1);
        vhCell.setCellValue(valueHeader);
        vhCell.setCellStyle(headerStyle);

        int dataStartRow = startRow;
        int i = 0;
        for (Map.Entry<String, Integer> entry : data.entrySet()) {
            Row row = sheet.createRow(startRow++);
            row.setHeightInPoints(20f);
            XSSFCellStyle style = (i % 2 == 0) ? dataEven : dataOdd;
            Cell lc = row.createCell(0);
            lc.setCellValue(entry.getKey());
            lc.setCellStyle(style);
            Cell vc = row.createCell(1);
            vc.setCellValue(entry.getValue());
            vc.setCellStyle(style);
            i++;
        }
        return new SectionBounds(titleRowIdx, dataStartRow, startRow - 1, startRow);
    }

    // ── Chart builder ─────────────────────────────────────────────────────────

    private void addBarChart(XSSFSheet sheet, int dataStartRow, int dataEndRow,
                              int col1, int row1, int col2, int row2, String title) {
        if (dataEndRow < dataStartRow) return;
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, col1, row1, col2, row2);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);
        chart.getOrAddLegend().setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis catAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis    valAxis = chart.createValueAxis(AxisPosition.LEFT);
        valAxis.setCrosses(AxisCrosses.AUTO_ZERO);

        XDDFDataSource<String>       categories = XDDFDataSourcesFactory.fromStringCellRange(sheet,
            new CellRangeAddress(dataStartRow, dataEndRow, 0, 0));
        XDDFNumericalDataSource<Double> values  = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
            new CellRangeAddress(dataStartRow, dataEndRow, 1, 1));

        XDDFBarChartData bar = (XDDFBarChartData) chart.createData(ChartTypes.BAR, catAxis, valAxis);
        bar.setBarDirection(BarDirection.COL);
        bar.setVaryColors(true);
        XDDFChartData.Series series = bar.addSeries(categories, values);
        series.setTitle("Count", null);
        chart.plot(bar);
    }

    // ── Cell style builders ───────────────────────────────────────────────────

    private XSSFCellStyle createHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(rgb(RGB_BLUE));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        s.setTopBorderColor(rgb(RGB_MED_BLUE));
        s.setBottomBorderColor(rgb(RGB_MED_BLUE));
        s.setLeftBorderColor(rgb(RGB_MED_BLUE));
        s.setRightBorderColor(rgb(RGB_MED_BLUE));
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(true);
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(rgb(RGB_WHITE));
        f.setFontName("Calibri");
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        return s;
    }

    private XSSFCellStyle createMetaStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(rgb(RGB_LITE_BLUE));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont();
        f.setFontName("Calibri");
        f.setFontHeightInPoints((short) 9);
        f.setColor(rgb(RGB_MED_BLUE));
        s.setFont(f);
        return s;
    }

    private XSSFCellStyle createBodyStyle(XSSFWorkbook wb, boolean alt) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(rgb(alt ? RGB_ROW_ALT : RGB_WHITE));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        s.setBottomBorderColor(rgb(RGB_BORDER));
        s.setLeftBorderColor(rgb(RGB_BORDER));
        s.setRightBorderColor(rgb(RGB_BORDER));
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont();
        f.setFontName("Calibri");
        f.setFontHeightInPoints((short) 10);
        f.setColor(rgb(RGB_TEXT_DARK));
        s.setFont(f);
        return s;
    }

    private XSSFCellStyle createDateStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = createBodyStyle(wb, false);
        s.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
        return s;
    }

    private XSSFCellStyle createMoneyStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = createBodyStyle(wb, false);
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        s.setAlignment(HorizontalAlignment.RIGHT);
        return s;
    }

    private XSSFCellStyle createReportTitleStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(rgb(RGB_NAVY));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setFontName("Calibri");
        f.setFontHeightInPoints((short) 15);
        f.setColor(rgb(RGB_WHITE));
        s.setFont(f);
        return s;
    }

    private XSSFCellStyle createSectionTitleStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(rgb(RGB_MED_BLUE));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        s.setTopBorderColor(rgb(RGB_BLUE));
        s.setBottomBorderColor(rgb(RGB_BLUE));
        s.setLeftBorderColor(rgb(RGB_BLUE));
        s.setRightBorderColor(rgb(RGB_BLUE));
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(rgb(RGB_WHITE));
        f.setFontName("Calibri");
        f.setFontHeightInPoints((short) 11);
        s.setFont(f);
        return s;
    }

    private XSSFCellStyle createKpiLabelStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(rgb(RGB_LITE_BLUE));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(rgb(RGB_BLUE));
        f.setFontName("Calibri");
        f.setFontHeightInPoints((short) 11);
        s.setFont(f);
        return s;
    }

    private XSSFCellStyle createKpiValueStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(rgb(RGB_BLUE));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(rgb(RGB_WHITE));
        f.setFontName("Calibri");
        f.setFontHeightInPoints((short) 14);
        s.setFont(f);
        return s;
    }

    private void addReportTitle(XSSFSheet sheet, String title, String subtitle, XSSFCellStyle metaStyle, int lastCol) {
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(38f);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(createReportTitleStyle((XSSFWorkbook) sheet.getWorkbook()));

        Row subtitleRow = sheet.createRow(1);
        subtitleRow.setHeightInPoints(18f);
        Cell subtitleCell = subtitleRow.createCell(0);
        subtitleCell.setCellValue(subtitle);
        subtitleCell.setCellStyle(metaStyle);

        int mergeEnd = Math.max(11, lastCol);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, mergeEnd));
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, mergeEnd));
    }

    // ── Badge styles ──────────────────────────────────────────────────────────

    private Map<String, XSSFCellStyle> createTicketBadgeStyles(XSSFWorkbook wb) {
        Map<String, XSSFCellStyle> m = new LinkedHashMap<>();
        // Service Types
        m.put("LEADS",        badgeStyle(wb, RGB_PURPLE_BG, RGB_PURPLE_FG));
        m.put("INSTALLATION", badgeStyle(wb, RGB_INDIGO_BG, RGB_INDIGO_FG));
        m.put("SERVICE",      badgeStyle(wb, RGB_TEAL_BG,   RGB_TEAL_FG));
        m.put("AMC",          badgeStyle(wb, RGB_AMBER_BG,  RGB_AMBER_FG));
        m.put("SITE_VISIT",   badgeStyle(wb, RGB_GREEN_BG,  RGB_GREEN_FG));
        // Statuses
        m.put("OPEN",         badgeStyle(wb, RGB_INDIGO_BG, RGB_INDIGO_FG));
        m.put("SITE_VISITED", badgeStyle(wb, RGB_TEAL_BG,   RGB_TEAL_FG));
        m.put("IN_PROGRESS",  badgeStyle(wb, RGB_LITE_BLUE, RGB_MED_BLUE));
        m.put("ON_HOLD",      badgeStyle(wb, RGB_AMBER_BG,  RGB_AMBER_FG));
        m.put("QUOTED",       badgeStyle(wb, RGB_PURPLE_BG, RGB_PURPLE_FG));
        m.put("RESOLVED",     badgeStyle(wb, RGB_GREEN_BG,  RGB_GREEN_FG));
        m.put("CLOSED",       badgeStyle(wb, RGB_GRAY_BG,   RGB_GRAY_FG));
        m.put("CANCELLED",    badgeStyle(wb, RGB_RED_BG,    RGB_RED_FG));
        // Priorities
        m.put("LOW",          badgeStyle(wb, RGB_GREEN_BG,  RGB_GREEN_FG));
        m.put("MEDIUM",       badgeStyle(wb, RGB_AMBER_BG,  RGB_AMBER_FG));
        m.put("HIGH",         badgeStyle(wb, RGB_ORANGE_BG, RGB_ORANGE_FG));
        m.put("CRITICAL",     badgeStyle(wb, RGB_RED_BG,    RGB_RED_FG));
        return m;
    }

    private Map<String, XSSFCellStyle> createUserBadgeStyles(XSSFWorkbook wb) {
        Map<String, XSSFCellStyle> m = new LinkedHashMap<>();
        m.put("ENABLED",     badgeStyle(wb, RGB_GREEN_BG,  RGB_GREEN_FG));
        m.put("DISABLED",    badgeStyle(wb, RGB_RED_BG,    RGB_RED_FG));
        m.put("VERIFIED",    badgeStyle(wb, RGB_GREEN_BG,  RGB_GREEN_FG));
        m.put("UNVERIFIED",  badgeStyle(wb, RGB_AMBER_BG,  RGB_AMBER_FG));
        m.put("ROLE_ADMIN",  badgeStyle(wb, RGB_INDIGO_BG, RGB_INDIGO_FG));
        m.put("ROLE_AGENT",  badgeStyle(wb, RGB_TEAL_BG,   RGB_TEAL_FG));
        m.put("ROLE_VENDOR", badgeStyle(wb, RGB_ORANGE_BG, RGB_ORANGE_FG));
        m.put("NO_ROLE",     badgeStyle(wb, RGB_GRAY_BG,   RGB_GRAY_FG));
        return m;
    }

    private XSSFCellStyle badgeStyle(XSSFWorkbook wb, byte[] bg, byte[] fg) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(rgb(bg));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(rgb(fg));
        f.setFontName("Calibri");
        f.setFontHeightInPoints((short) 9);
        s.setFont(f);
        return s;
    }

    private void setBadgeCell(Cell cell, String value, Map<String, XSSFCellStyle> styles) {
        cell.setCellValue(value == null ? "" : value);
        if (value != null && styles.containsKey(value)) {
            cell.setCellStyle(styles.get(value));
        }
    }

    private void applyBodyStyle(Row row, XSSFCellStyle even, XSSFCellStyle odd, Set<Integer> skip) {
        XSSFCellStyle selected = (row.getRowNum() % 2 == 0) ? even : odd;
        for (Cell cell : row) {
            if (!skip.contains(cell.getColumnIndex())) {
                cell.setCellStyle(selected);
            }
        }
    }

    // ── Aggregation helpers ───────────────────────────────────────────────────

    private <T> LinkedHashMap<String, Integer> countBy(List<T> values, java.util.function.Function<T, String> fn) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (T v : values) {
            String key = fn.apply(v);
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
                String name = (role == null || role.getName() == null) ? "UNKNOWN_ROLE" : role.getName();
                counts.put(name, counts.getOrDefault(name, 0) + 1);
            }
        }
        return counts;
    }

    private record SectionBounds(int titleRow, int dataStartRow, int dataEndRow, int nextRow) {}

    // ── Data filtering ────────────────────────────────────────────────────────

    private List<Ticket> getFilteredTickets(String dateRange, String startDate, String endDate,
                                            String ticketStatus, String ticketPriority, String serviceType) {
        Specification<Ticket> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (dateRange != null && !dateRange.equals("all")) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime startDT;
                LocalDateTime endDT;

                switch (dateRange) {
                    case "today" -> {
                        startDT = now.toLocalDate().atStartOfDay();
                        endDT   = now.toLocalDate().atTime(23, 59, 59);
                    }
                    case "yesterday" -> {
                        LocalDate yd = now.toLocalDate().minusDays(1);
                        startDT = yd.atStartOfDay();
                        endDT   = yd.atTime(23, 59, 59);
                    }
                    case "last7days" -> {
                        startDT = now.minusDays(7).toLocalDate().atStartOfDay();
                        endDT   = now.toLocalDate().atTime(23, 59, 59);
                    }
                    case "last30days" -> {
                        startDT = now.minusDays(30).toLocalDate().atStartOfDay();
                        endDT   = now.toLocalDate().atTime(23, 59, 59);
                    }
                    case "thismonth" -> {
                        startDT = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
                        endDT   = now.toLocalDate().atTime(23, 59, 59);
                    }
                    case "lastmonth" -> {
                        LocalDate lm = now.toLocalDate().minusMonths(1);
                        startDT = lm.withDayOfMonth(1).atStartOfDay();
                        endDT   = lm.withDayOfMonth(lm.lengthOfMonth()).atTime(23, 59, 59);
                    }
                    case "custom" -> {
                        if (startDate != null && endDate != null) {
                            startDT = LocalDate.parse(startDate).atStartOfDay();
                            endDT   = LocalDate.parse(endDate).atTime(23, 59, 59);
                        } else {
                            return cb.conjunction();
                        }
                    }
                    default -> { return cb.conjunction(); }
                }
                predicates.add(cb.between(root.get("createdAt"), startDT, endDT));
            }

            if (ticketStatus != null && !ticketStatus.isEmpty())
                predicates.add(cb.equal(root.get("status"),      TicketStatus.valueOf(ticketStatus)));
            if (ticketPriority != null && !ticketPriority.isEmpty())
                predicates.add(cb.equal(root.get("priority"),    TicketPriority.valueOf(ticketPriority)));
            if (serviceType != null && !serviceType.isEmpty())
                predicates.add(cb.equal(root.get("serviceType"), TicketServiceType.valueOf(serviceType)));

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return ticketRepository.findAll(spec);
    }

    private List<AppUser> getFilteredUsers(String dateRange, String startDate, String endDate,
                                           String userStatus, String emailVerified, String userRole) {
        List<AppUser> all = userRepository.findAll();
        List<AppUser> result = new ArrayList<>();

        for (AppUser user : all) {
            boolean include = true;

            if (dateRange != null && !dateRange.equals("all")) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime startDT = null;
                LocalDateTime endDT   = null;

                switch (dateRange) {
                    case "today" -> {
                        startDT = now.toLocalDate().atStartOfDay();
                        endDT   = now.toLocalDate().atTime(23, 59, 59);
                    }
                    case "yesterday" -> {
                        LocalDate yd = now.toLocalDate().minusDays(1);
                        startDT = yd.atStartOfDay();
                        endDT   = yd.atTime(23, 59, 59);
                    }
                    case "last7days" -> {
                        startDT = now.minusDays(7).toLocalDate().atStartOfDay();
                        endDT   = now.toLocalDate().atTime(23, 59, 59);
                    }
                    case "last30days" -> {
                        startDT = now.minusDays(30).toLocalDate().atStartOfDay();
                        endDT   = now.toLocalDate().atTime(23, 59, 59);
                    }
                    case "thismonth" -> {
                        startDT = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
                        endDT   = now.toLocalDate().atTime(23, 59, 59);
                    }
                    case "lastmonth" -> {
                        LocalDate lm = now.toLocalDate().minusMonths(1);
                        startDT = lm.withDayOfMonth(1).atStartOfDay();
                        endDT   = lm.withDayOfMonth(lm.lengthOfMonth()).atTime(23, 59, 59);
                    }
                    case "custom" -> {
                        if (startDate != null && endDate != null) {
                            startDT = LocalDate.parse(startDate).atStartOfDay();
                            endDT   = LocalDate.parse(endDate).atTime(23, 59, 59);
                        } else {
                            include = false;
                        }
                    }
                    default -> include = false;
                }

                if (include && startDT != null && user.getCreatedAt() != null)
                    include = !user.getCreatedAt().isBefore(startDT) && !user.getCreatedAt().isAfter(endDT);
            }

            if (include && userStatus != null && !userStatus.isEmpty()) {
                if ("enabled".equals(userStatus))        include = user.isEnabled();
                else if ("disabled".equals(userStatus))  include = !user.isEnabled();
            }

            if (include && emailVerified != null && !emailVerified.isEmpty()) {
                if ("verified".equals(emailVerified))      include = user.isEmailVerified();
                else if ("unverified".equals(emailVerified)) include = !user.isEmailVerified();
            }

            if (include && userRole != null && !userRole.isEmpty())
                include = user.getRoles().stream().anyMatch(r -> userRole.equals(r.getName()));

            if (include) result.add(user);
        }

        return result;
    }

    // ── Email helpers ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public void emailReport(String reportType, String dateRange, String startDate, String endDate,
                            String ticketStatus, String ticketPriority, String serviceType,
                            String userStatus, String emailVerified, String userRole,
                            String recipientEmail, String emailSubject, String emailMessage, String generatedBy) {
        try {
            byte[] data     = generateReport(reportType, dateRange, startDate, endDate,
                ticketStatus, ticketPriority, serviceType, userStatus, emailVerified, userRole, generatedBy);
            String filename = getReportFilename(reportType);
            String subject  = emailSubject  != null ? emailSubject  : reportType + " Report";
            String message  = emailMessage  != null ? emailMessage  : "Please find the attached report.";
            emailService.sendReportEmail(recipientEmail, subject, message, data, filename);
        } catch (Exception e) {
            log.error("Error sending report email", e);
            throw new RuntimeException("Failed to send report email: " + e.getMessage(), e);
        }
    }

    public void sendGeneratedReport(String recipientEmail, String emailSubject, String emailMessage,
                                    byte[] reportData, String filename) {
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
                                                   byte[] usersReport,   String usersFilename) {
        try {
            String subject = emailSubject != null ? emailSubject : "Daily Reports";
            String message = emailMessage != null ? emailMessage : "Please find the attached reports.";
            Map<String, byte[]> attachments = new LinkedHashMap<>();
            attachments.put(ticketsFilename, ticketsReport);
            attachments.put(usersFilename,   usersReport);
            emailService.sendReportEmailWithAttachments(recipientEmail, subject, message, attachments);
        } catch (Exception e) {
            log.error("Error sending combined reports email", e);
            throw new RuntimeException("Failed to send combined reports email: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> getRecentReports() {
        return new ArrayList<>();
    }

    public byte[] getReportById(Long reportId) {
        throw new UnsupportedOperationException("Report storage not implemented yet");
    }

    public String getReportFilenameById(Long reportId) {
        throw new UnsupportedOperationException("Report storage not implemented yet");
    }
}
