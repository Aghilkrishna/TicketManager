# Staff Billing — Technician Payment Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Update staff billing to source its billing amount from the TECHNICIAN `TicketPayment` record and surface payment mode, date, and status in the details and invoice views.

**Architecture:** Three targeted changes — extend `StaffBillingTicketLine` DTO with 3 new nullable fields, update `StaffBillingService` to resolve amounts from TECHNICIAN payment with fallback to old cost fields, then update two Thymeleaf templates to render the new columns.

**Tech Stack:** Java 21, Spring Boot, Thymeleaf, Bootstrap 5

## Global Constraints

- Fallback chain for billing amount: TECHNICIAN `actualPrice` → `expectedPrice` → `ticket.actualCost` → `ticket.estimatedCost` → `BigDecimal.ZERO`
- CLIENT and VENDOR payment types are not surfaced — TECHNICIAN only
- All three new DTO fields are nullable — render `—` when null in templates
- Payment status (from `TicketPayment.status`) appears in the details view only, not the invoice
- `TicketBillingStatus` (PAID/UNPAID on Ticket) is unchanged — it remains the admin-controlled flag

---

## File Map

| File | Change |
|------|--------|
| `src/main/java/com/example/ticketmanager/dto/AdminDtos.java` | Extend `StaffBillingTicketLine` with 3 new fields |
| `src/main/java/com/example/ticketmanager/service/StaffBillingService.java` | Add helper, update `resolveBillAmount`, update `getStaffBillingDetails` |
| `src/main/resources/templates/admin-staff-billing-details.html` | Add 3 columns to ticket table, fix colspans |
| `src/main/resources/templates/admin-staff-billing-invoice.html` | Add 2 columns to invoice table, fix colspans |

---

### Task 1: Extend DTO and update service

**Files:**
- Modify: `src/main/java/com/example/ticketmanager/dto/AdminDtos.java:148-156`
- Modify: `src/main/java/com/example/ticketmanager/service/StaffBillingService.java`

**Interfaces:**
- Produces: `AdminDtos.StaffBillingTicketLine` with 9 fields (3 new nullable: `paymentMode`, `paymentDatetime`, `paymentStatus`) — consumed by Tasks 2 and 3 via Thymeleaf

---

- [ ] **Step 1: Extend `StaffBillingTicketLine` in `AdminDtos.java`**

Replace lines 148–156:

```java
    public record StaffBillingTicketLine(
            Long ticketId,
            String title,
            String status,
            BigDecimal amount,
            String billingStatus,
            LocalDateTime updatedAt,
            String paymentMode,
            LocalDateTime paymentDatetime,
            String paymentStatus
    ) {
    }
```

- [ ] **Step 2: Add imports to `StaffBillingService.java`**

After the existing entity imports (after `import com.example.ticketmanager.entity.TicketStatus;`), add:

```java
import com.example.ticketmanager.entity.TicketPayment;
import com.example.ticketmanager.entity.TicketPaymentType;
```

After `import java.util.Set;`, add:

```java
import java.util.Optional;
```

- [ ] **Step 3: Add `getTechnicianPayment` private helper in `StaffBillingService`**

Add this method after the `resolveBillAmount` method (currently at line 175):

```java
    private Optional<TicketPayment> getTechnicianPayment(Ticket ticket) {
        return ticket.getPayments().stream()
                .filter(p -> p.getPaymentType() == TicketPaymentType.TECHNICIAN)
                .findFirst();
    }
```

- [ ] **Step 4: Update `resolveBillAmount` to use TECHNICIAN payment with fallback**

Replace the existing `resolveBillAmount` method (lines 175–183):

```java
    private BigDecimal resolveBillAmount(Ticket ticket) {
        Optional<TicketPayment> techPayment = getTechnicianPayment(ticket);
        if (techPayment.isPresent()) {
            TicketPayment tp = techPayment.get();
            if (tp.getActualPrice() != null) return tp.getActualPrice();
            if (tp.getExpectedPrice() != null) return tp.getExpectedPrice();
        }
        if (ticket.getActualCost() != null) return ticket.getActualCost();
        if (ticket.getEstimatedCost() != null) return ticket.getEstimatedCost();
        return BigDecimal.ZERO;
    }
```

- [ ] **Step 5: Update `getStaffBillingDetails()` to populate the 3 new fields**

In `getStaffBillingDetails()`, replace the `lines.add(...)` call (lines 86–94):

```java
            if (isResolved || isUnpaidClosed) {
                Optional<TicketPayment> techPmt = getTechnicianPayment(ticket);
                String paymentMode = techPmt
                        .map(p -> p.getPaymentMode() == null ? null : p.getPaymentMode().name())
                        .orElse(null);
                LocalDateTime paymentDatetime = techPmt
                        .map(TicketPayment::getPaymentDatetime)
                        .orElse(null);
                String paymentStatus = techPmt
                        .map(TicketPayment::getStatus)
                        .orElse(null);
                lines.add(new AdminDtos.StaffBillingTicketLine(
                        ticket.getId(),
                        ticket.getTitle(),
                        ticket.getStatus().name(),
                        resolveBillAmount(ticket),
                        toBillingStatusLabel(ticket.getBillingStatus()),
                        ticket.getUpdatedAt(),
                        paymentMode,
                        paymentDatetime,
                        paymentStatus
                ));
            }
```

- [ ] **Step 6: Verify the project compiles**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS with no errors.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/ticketmanager/dto/AdminDtos.java \
        src/main/java/com/example/ticketmanager/service/StaffBillingService.java
git commit -m "feat: source staff billing amount from TECHNICIAN payment with old-field fallback"
```

---

### Task 2: Update billing details template

**Files:**
- Modify: `src/main/resources/templates/admin-staff-billing-details.html`

**Interfaces:**
- Consumes: `AdminDtos.StaffBillingTicketLine` with `paymentMode`, `paymentDatetime`, `paymentStatus` (from Task 1)

---

- [ ] **Step 1: Add 3 new column headers to the ticket table**

Replace lines 183–190 (the `<thead><tr>` block):

```html
                                <thead>
                                <tr>
                                    <th>Ticket</th>
                                    <th>Status</th>
                                    <th>Price</th>
                                    <th>Payment Mode</th>
                                    <th>Payment Date</th>
                                    <th>Payment Status</th>
                                    <th>Billing</th>
                                    <th>Last Updated</th>
                                    <th class="text-end">Actions</th>
                                </tr>
                                </thead>
```

- [ ] **Step 2: Add 3 new cells to each ticket row**

Replace line 208 (`<td th:text="${ticket.amount}">0</td>`) with:

```html
                                    <td th:text="${ticket.amount}">0</td>
                                    <td th:text="${ticket.paymentMode != null ? (ticket.paymentMode == 'CASH' ? 'Cash' : (ticket.paymentMode == 'BANK_TRANSFER' ? 'Bank Transfer' : 'UPI')) : '—'}">—</td>
                                    <td th:text="${ticket.paymentDatetime != null ? #temporals.format(ticket.paymentDatetime, 'dd MMM yyyy, hh:mm a') : '—'}">—</td>
                                    <td>
                                        <span th:if="${ticket.paymentStatus != null}"
                                              class="badge text-bg-secondary"
                                              th:text="${ticket.paymentStatus}">—</span>
                                        <span th:if="${ticket.paymentStatus == null}">—</span>
                                    </td>
```

- [ ] **Step 3: Fix the empty-state colspan (line 194)**

Replace:
```html
                                    <td colspan="6" class="text-center text-muted py-5">
```
With:
```html
                                    <td colspan="9" class="text-center text-muted py-5">
```

- [ ] **Step 4: Fix tfoot colspans (lines 242–251)**

Replace:
```html
                                <tfoot th:if="${!#lists.isEmpty(billingDetails.tickets)}">
                                <tr class="table-success">
                                    <th colspan="4" class="text-end text-success">Already Paid (closed tickets)</th>
                                    <th colspan="2" class="text-end text-success" th:text="${billingDetails.totalPaidAmount}">0</th>
                                </tr>
                                <tr class="table-active fw-bold">
                                    <th colspan="4" class="text-end">Total Pending Amount</th>
                                    <th colspan="2" class="text-end" th:text="${billingDetails.totalUnpaidAmount}">0</th>
                                </tr>
                                </tfoot>
```
With:
```html
                                <tfoot th:if="${!#lists.isEmpty(billingDetails.tickets)}">
                                <tr class="table-success">
                                    <th colspan="7" class="text-end text-success">Already Paid (closed tickets)</th>
                                    <th colspan="2" class="text-end text-success" th:text="${billingDetails.totalPaidAmount}">0</th>
                                </tr>
                                <tr class="table-active fw-bold">
                                    <th colspan="7" class="text-end">Total Pending Amount</th>
                                    <th colspan="2" class="text-end" th:text="${billingDetails.totalUnpaidAmount}">0</th>
                                </tr>
                                </tfoot>
```

- [ ] **Step 5: Start the app and verify in browser**

Navigate to `/admin/staff-billing` → click a staff member → confirm the details table shows 9 columns with Payment Mode, Payment Date, Payment Status populated from TECHNICIAN payment (or `—` where none exists).

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/admin-staff-billing-details.html
git commit -m "feat: add payment mode, date, status columns to staff billing details table"
```

---

### Task 3: Update invoice template

**Files:**
- Modify: `src/main/resources/templates/admin-staff-billing-invoice.html`

**Interfaces:**
- Consumes: `AdminDtos.StaffBillingTicketLine` with `paymentMode`, `paymentDatetime` (from Task 1)

---

- [ ] **Step 1: Add 2 new column headers to the invoice table**

Replace lines 89–95 (the `<thead><tr>` block):

```html
                <thead>
                <tr>
                    <th>Ticket ID</th>
                    <th>Title</th>
                    <th>Last Updated</th>
                    <th>Payment Mode</th>
                    <th>Payment Date</th>
                    <th>Billing Status</th>
                    <th class="text-end">Amount</th>
                </tr>
                </thead>
```

- [ ] **Step 2: Add 2 new cells to each invoice ticket row**

Replace lines 101–110 (`<tr th:each="ticket : ${invoiceTickets}">` block):

```html
                <tr th:each="ticket : ${invoiceTickets}">
                    <td>#<span th:text="${ticket.ticketId}">1</span></td>
                    <td th:text="${ticket.title}">Ticket title</td>
                    <td th:text="${#temporals.format(ticket.updatedAt, 'dd MMM yyyy, hh:mm a')}">-</td>
                    <td th:text="${ticket.paymentMode != null ? (ticket.paymentMode == 'CASH' ? 'Cash' : (ticket.paymentMode == 'BANK_TRANSFER' ? 'Bank Transfer' : 'UPI')) : '—'}">—</td>
                    <td th:text="${ticket.paymentDatetime != null ? #temporals.format(ticket.paymentDatetime, 'dd MMM yyyy, hh:mm a') : '—'}">—</td>
                    <td>
                        <span th:class="${ticket.billingStatus == 'Paid'} ? 'badge-paid' : 'badge-unpaid'"
                              th:text="${ticket.billingStatus}">Unpaid</span>
                    </td>
                    <td class="text-end fw-semibold" th:text="${ticket.amount}">0</td>
                </tr>
```

- [ ] **Step 3: Fix the empty-state colspan (line 99)**

Replace:
```html
                    <td colspan="5" class="text-center text-muted py-4">No closed tickets are currently available for invoice generation.</td>
```
With:
```html
                    <td colspan="7" class="text-center text-muted py-4">No closed tickets are currently available for invoice generation.</td>
```

- [ ] **Step 4: Fix tfoot colspans (lines 112–125)**

Replace:
```html
                <tfoot>
                <tr>
                    <th colspan="4" class="text-end text-success">Total Paid</th>
                    <th class="text-end text-success" th:text="${billingDetails.totalPaidAmount}">0</th>
                </tr>
                <tr>
                    <th colspan="4" class="text-end text-danger">Total Unpaid</th>
                    <th class="text-end text-danger" th:text="${billingDetails.totalUnpaidAmount}">0</th>
                </tr>
                <tr class="table-active">
                    <th colspan="4" class="text-end">Total Closed Ticket Amount</th>
                    <th class="text-end" th:text="${billingDetails.totalClosedAmount}">0</th>
                </tr>
                </tfoot>
```
With:
```html
                <tfoot>
                <tr>
                    <th colspan="6" class="text-end text-success">Total Paid</th>
                    <th class="text-end text-success" th:text="${billingDetails.totalPaidAmount}">0</th>
                </tr>
                <tr>
                    <th colspan="6" class="text-end text-danger">Total Unpaid</th>
                    <th class="text-end text-danger" th:text="${billingDetails.totalUnpaidAmount}">0</th>
                </tr>
                <tr class="table-active">
                    <th colspan="6" class="text-end">Total Closed Ticket Amount</th>
                    <th class="text-end" th:text="${billingDetails.totalClosedAmount}">0</th>
                </tr>
                </tfoot>
```

- [ ] **Step 5: Verify in browser**

Navigate to `/admin/staff-billing` → open any staff member → click "Generate Invoice" → confirm the invoice table shows 7 columns with Payment Mode and Payment Date populated (or `—`).

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/admin-staff-billing-invoice.html
git commit -m "feat: add payment mode and date columns to staff billing invoice"
```
