# Ticket Payment Section Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the existing Price Section on ticket create/edit/view pages with a structured Payment Section containing three subsections (Client, Technician, Vendor), each stored as a row in a new `ticket_payments` table with role-based visibility.

**Architecture:** Single `TicketPayment` entity with a `payment_type` discriminator. Payments are saved separately after ticket persistence. Role guards: admin sees all three; agent sees client+technician; vendor sees vendor only. Flat form fields (15 total) map to per-type upsert logic in TicketService.

**Tech Stack:** Spring Boot, Spring Data JPA, Hibernate, Thymeleaf, Bootstrap 5, Flyway, MySQL/MariaDB

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `entity/TicketPaymentType.java` | Create | Enum: CLIENT, TECHNICIAN, VENDOR |
| `entity/TicketPaymentMode.java` | Create | Enum: CASH, BANK_TRANSFER, UPI |
| `entity/TicketPayment.java` | Create | JPA entity for ticket_payments table |
| `repository/TicketPaymentRepository.java` | Create | JPA repository with findByTicketIdAndPaymentType |
| `db/migration/V13__add_ticket_payments_table.sql` | Create | Flyway migration |
| `entity/Ticket.java` | Modify | Add `@OneToMany payments` relationship |
| `dto/AuthDtos.java` | Modify | Add TicketPaymentInfo record; extend TicketRequest + TicketSummary |
| `service/TicketService.java` | Modify | Inject repo; add payment save logic; update create/update/toSummary |
| `templates/ticket-create.html` | Modify | Replace Price Section with 3-subsection Payment Section |
| `templates/ticket-edit.html` | Modify | Replace Price Section with pre-populated Payment Section |
| `templates/ticket-view.html` | Modify | Replace Price Section with read-only Payment Section + badges |

---

## Task 1: Flyway Migration + Java Enums

**Files:**
- Create: `src/main/resources/db/migration/V13__add_ticket_payments_table.sql`
- Create: `src/main/java/com/example/ticketmanager/entity/TicketPaymentType.java`
- Create: `src/main/java/com/example/ticketmanager/entity/TicketPaymentMode.java`

- [ ] **Step 1: Create the Flyway migration**

```sql
-- src/main/resources/db/migration/V13__add_ticket_payments_table.sql
CREATE TABLE ticket_payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    payment_type VARCHAR(20) NOT NULL,
    expected_price DECIMAL(12,2),
    actual_price DECIMAL(12,2),
    payment_mode VARCHAR(20),
    payment_datetime DATETIME,
    status VARCHAR(50),
    CONSTRAINT fk_tp_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE
);
```

- [ ] **Step 2: Create TicketPaymentType enum**

```java
// src/main/java/com/example/ticketmanager/entity/TicketPaymentType.java
package com.example.ticketmanager.entity;

public enum TicketPaymentType {
    CLIENT, TECHNICIAN, VENDOR
}
```

- [ ] **Step 3: Create TicketPaymentMode enum**

```java
// src/main/java/com/example/ticketmanager/entity/TicketPaymentMode.java
package com.example.ticketmanager.entity;

public enum TicketPaymentMode {
    CASH, BANK_TRANSFER, UPI
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/migration/V13__add_ticket_payments_table.sql \
        src/main/java/com/example/ticketmanager/entity/TicketPaymentType.java \
        src/main/java/com/example/ticketmanager/entity/TicketPaymentMode.java
git commit -m "feat: add ticket_payments migration and payment enums"
```

---

## Task 2: TicketPayment Entity + Repository

**Files:**
- Create: `src/main/java/com/example/ticketmanager/entity/TicketPayment.java`
- Create: `src/main/java/com/example/ticketmanager/repository/TicketPaymentRepository.java`

- [ ] **Step 1: Create TicketPayment entity**

Note: `payment_mode` uses `columnDefinition = "varchar(20)"` to suppress Hibernate's auto-generated CHECK constraint — this prevents deploy failures when enum values are added in future (see project pattern: feedback_hibernate_enum_check_constraint.md).

```java
// src/main/java/com/example/ticketmanager/entity/TicketPayment.java
package com.example.ticketmanager.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ticket_payments")
public class TicketPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20)
    private TicketPaymentType paymentType;

    @Column(name = "expected_price", precision = 12, scale = 2)
    private BigDecimal expectedPrice;

    @Column(name = "actual_price", precision = 12, scale = 2)
    private BigDecimal actualPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", columnDefinition = "varchar(20)")
    private TicketPaymentMode paymentMode;

    @Column(name = "payment_datetime")
    private LocalDateTime paymentDatetime;

    @Column(name = "status", length = 50)
    private String status;
}
```

- [ ] **Step 2: Create TicketPaymentRepository**

```java
// src/main/java/com/example/ticketmanager/repository/TicketPaymentRepository.java
package com.example.ticketmanager.repository;

import com.example.ticketmanager.entity.TicketPayment;
import com.example.ticketmanager.entity.TicketPaymentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TicketPaymentRepository extends JpaRepository<TicketPayment, Long> {
    Optional<TicketPayment> findByTicketIdAndPaymentType(Long ticketId, TicketPaymentType paymentType);
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/ticketmanager/entity/TicketPayment.java \
        src/main/java/com/example/ticketmanager/repository/TicketPaymentRepository.java
git commit -m "feat: add TicketPayment entity and repository"
```

---

## Task 3: Ticket Entity — Add Payments Relationship

**Files:**
- Modify: `src/main/java/com/example/ticketmanager/entity/Ticket.java`

- [ ] **Step 1: Read the existing Ticket.java import block and field list** to find the right insertion point (after the `siteVisitHistory` field, before `createdAt`).

- [ ] **Step 2: Add import for TicketPayment** (it's in the same package, no import needed — but confirm there is no explicit import block conflict).

- [ ] **Step 3: Add the payments relationship field**

In `Ticket.java`, after the `siteVisitHistory` field:
```java
@OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
private List<TicketPayment> payments = new ArrayList<>();
```

- [ ] **Step 4: Verify the project compiles**

```bash
cd /Users/aghilkrishna/work/workspaces/TicketManager && ./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/ticketmanager/entity/Ticket.java
git commit -m "feat: add payments OneToMany relationship to Ticket entity"
```

---

## Task 4: DTOs — TicketPaymentInfo, Extend TicketRequest and TicketSummary

**Files:**
- Modify: `src/main/java/com/example/ticketmanager/dto/AuthDtos.java`

All changes are inside the `AuthDtos` class in this file.

- [ ] **Step 1: Add the `TicketPaymentInfo` record**

Add this record after the `TicketAttachmentInfo` record (around line 215):

```java
public record TicketPaymentInfo(
        Long id,
        String paymentType,
        BigDecimal expectedPrice,
        BigDecimal actualPrice,
        String paymentMode,
        LocalDateTime paymentDatetime,
        String status
) {}
```

`LocalDateTime` and `BigDecimal` are already imported in `AuthDtos.java`. Confirm at the top of the file.

- [ ] **Step 2: Extend `TicketRequest` with 15 payment fields**

In the `TicketRequest` record (line 125), add these fields after `Set<Long> serviceUserIds`:

```java
        BigDecimal clientExpectedPrice,
        BigDecimal clientActualPrice,
        String clientPaymentMode,
        LocalDateTime clientPaymentDatetime,
        String clientPaymentStatus,
        BigDecimal technicianExpectedPrice,
        BigDecimal technicianActualPrice,
        String technicianPaymentMode,
        LocalDateTime technicianPaymentDatetime,
        String technicianPaymentStatus,
        BigDecimal vendorExpectedPrice,
        BigDecimal vendorActualPrice,
        String vendorPaymentMode,
        LocalDateTime vendorPaymentDatetime,
        String vendorPaymentStatus
```

The record closing `)` must come after `vendorPaymentStatus`.

- [ ] **Step 3: Extend `TicketSummary` with payments field and helper method**

In the `TicketSummary` record (line 161), add `List<TicketPaymentInfo> payments` as the last field (after `List<TicketAttachmentInfo> attachments`):

```java
        List<TicketPaymentInfo> payments
```

Then add a helper method inside the record body (after the closing `)` of the component list, before the record's `}`):

```java
    public TicketPaymentInfo paymentByType(String type) {
        if (payments == null) return null;
        return payments.stream()
                .filter(p -> type.equals(p.paymentType()))
                .findFirst().orElse(null);
    }
```

- [ ] **Step 4: Verify the project compiles** (TicketService will break — that's expected at this step because `new AuthDtos.TicketSummary(...)` calls now have a missing argument)

```bash
cd /Users/aghilkrishna/work/workspaces/TicketManager && ./mvnw compile -q 2>&1 | grep -E 'ERROR|error:' | head -20
```

Expected: compile errors mentioning `TicketSummary` constructor argument count mismatch — these are fixed in Task 5.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/ticketmanager/dto/AuthDtos.java
git commit -m "feat: add TicketPaymentInfo record, extend TicketRequest and TicketSummary with payment fields"
```

---

## Task 5: TicketService — Payment Save Logic + toSummary Update

**Files:**
- Modify: `src/main/java/com/example/ticketmanager/service/TicketService.java`

- [ ] **Step 1: Inject `TicketPaymentRepository`**

`TicketService` uses Lombok `@RequiredArgsConstructor`. Add the field after the last existing repository field (around line 56):

```java
private final TicketPaymentRepository ticketPaymentRepository;
```

- [ ] **Step 2: Add the necessary imports** at the top of `TicketService.java` (check if already present before adding):

```java
import com.example.ticketmanager.entity.TicketPayment;
import com.example.ticketmanager.entity.TicketPaymentMode;
import com.example.ticketmanager.entity.TicketPaymentType;
import com.example.ticketmanager.repository.TicketPaymentRepository;
```

- [ ] **Step 3: Update `create()` to call payment save after ticket persistence**

In `create()` (line 62), add `saveOrUpdatePayments(saved, request, creator)` after `ticketRepository.save(ticket)`:

```java
public AuthDtos.TicketSummary create(String creatorUsername, AuthDtos.TicketRequest request, MultipartFile[] files) {
    AppUser creator = userService.getByEmail(creatorUsername);
    Ticket ticket = new Ticket();
    applyRequest(ticket, request, creator, creatorUsername, true);
    storeFiles(ticket, files);
    Ticket saved = ticketRepository.save(ticket);
    addInitialComment(saved, creatorUsername, request.initialComment());
    saveOrUpdatePayments(saved, request, creator);
    publishTicketListRefreshEvent("CREATED", saved);
    notifyStakeholders(saved, "Ticket created: " + saved.getTitle(), NotificationType.TICKET_UPDATED, EmailNotificationAction.TICKET_CREATED);
    notifyAdditionalTicketAudiences(saved, "Ticket created: " + saved.getTitle(), NotificationType.TICKET_UPDATED);
    return toSummary(saved);
}
```

- [ ] **Step 4: Update `update()` to extract actor and call payment save**

In `update()` (line 77), extract `actor` and add `saveOrUpdatePayments`:

```java
public AuthDtos.TicketSummary update(Long ticketId, String actorUsername, AuthDtos.TicketRequest request, MultipartFile[] files) {
    Ticket ticket = getTicket(ticketId);
    AppUser actor = userService.getByEmail(actorUsername);
    ensureCanUpdate(ticket, actor);
    applyRequest(ticket, request, ticket.getCreatedBy(), actorUsername, false);
    ticket.setUpdatedAt(LocalDateTime.now());
    storeFiles(ticket, files);
    Ticket saved = ticketRepository.save(ticket);
    saveOrUpdatePayments(saved, request, actor);
    publishTicketListRefreshEvent("UPDATED", saved);
    notifyStakeholders(saved, "Ticket updated: " + saved.getTitle(), NotificationType.TICKET_UPDATED, EmailNotificationAction.TICKET_UPDATED);
    notifyAdditionalTicketAudiences(saved, "Ticket updated: " + saved.getTitle(), NotificationType.TICKET_UPDATED);
    return toSummary(saved);
}
```

- [ ] **Step 5: Add private `saveOrUpdatePayments()` and `upsertPayment()` methods**

Add these two private methods anywhere in the private methods section of `TicketService` (e.g., after the `applyRequest` method around line 631):

```java
private void saveOrUpdatePayments(Ticket ticket, AuthDtos.TicketRequest request, AppUser actor) {
    boolean isVendor = userService.hasRole(actor, "ROLE_VENDOR");
    boolean isAgent = userService.hasRole(actor, "ROLE_AGENT");
    if (!isVendor) {
        upsertPayment(ticket, TicketPaymentType.CLIENT,
                request.clientExpectedPrice(), request.clientActualPrice(),
                request.clientPaymentMode(), request.clientPaymentDatetime(), request.clientPaymentStatus());
        upsertPayment(ticket, TicketPaymentType.TECHNICIAN,
                request.technicianExpectedPrice(), request.technicianActualPrice(),
                request.technicianPaymentMode(), request.technicianPaymentDatetime(), request.technicianPaymentStatus());
    }
    if (!isAgent) {
        upsertPayment(ticket, TicketPaymentType.VENDOR,
                request.vendorExpectedPrice(), request.vendorActualPrice(),
                request.vendorPaymentMode(), request.vendorPaymentDatetime(), request.vendorPaymentStatus());
    }
}

private void upsertPayment(Ticket ticket, TicketPaymentType type,
        BigDecimal expectedPrice, BigDecimal actualPrice,
        String paymentMode, LocalDateTime paymentDatetime, String status) {
    boolean hasData = expectedPrice != null || actualPrice != null
            || (paymentMode != null && !paymentMode.isBlank())
            || paymentDatetime != null
            || (status != null && !status.isBlank());
    if (!hasData) return;
    TicketPayment payment = ticketPaymentRepository
            .findByTicketIdAndPaymentType(ticket.getId(), type)
            .orElse(new TicketPayment());
    payment.setTicket(ticket);
    payment.setPaymentType(type);
    payment.setExpectedPrice(expectedPrice);
    payment.setActualPrice(actualPrice);
    payment.setPaymentMode(paymentMode == null || paymentMode.isBlank()
            ? null : TicketPaymentMode.valueOf(paymentMode));
    payment.setPaymentDatetime(paymentDatetime);
    payment.setStatus(status == null || status.isBlank() ? null : status);
    ticketPaymentRepository.save(payment);
}
```

- [ ] **Step 6: Update `toSummary(Ticket, String)` to include payments as last argument**

In `toSummary(Ticket ticket, String viewerUsername)` (line 845), the `new AuthDtos.TicketSummary(...)` constructor call ends at line 906. Add payments as the final argument after the attachments list:

The last 4 lines of the constructor call currently look like:
```java
                ticket.getAttachments().stream().map(TicketAttachment::getOriginalFileName).toList(),
                !ticket.getAttachments().isEmpty(),
                ticket.getAttachments().stream()
                        .map(this::toAttachmentInfo)
                        .toList()
        );
```

Change to:
```java
                ticket.getAttachments().stream().map(TicketAttachment::getOriginalFileName).toList(),
                !ticket.getAttachments().isEmpty(),
                ticket.getAttachments().stream()
                        .map(this::toAttachmentInfo)
                        .toList(),
                ticket.getPayments().stream()
                        .map(p -> new AuthDtos.TicketPaymentInfo(
                                p.getId(), p.getPaymentType().name(),
                                p.getExpectedPrice(), p.getActualPrice(),
                                p.getPaymentMode() == null ? null : p.getPaymentMode().name(),
                                p.getPaymentDatetime(), p.getStatus()
                        ))
                        .toList()
        );
```

- [ ] **Step 7: Update `buildListSummary()` to pass `List.of()` for payments**

In `buildListSummary()` (line 795), the constructor call ends at line 841 with:
```java
                List.of()
        );
```

Change to:
```java
                List.of(),
                List.of()
        );
```

(First `List.of()` = attachments, second `List.of()` = payments.)

- [ ] **Step 8: Verify the project compiles cleanly**

```bash
cd /Users/aghilkrishna/work/workspaces/TicketManager && ./mvnw compile -q
```

Expected: BUILD SUCCESS with no errors.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/example/ticketmanager/service/TicketService.java
git commit -m "feat: add payment save logic and update toSummary with payments in TicketService"
```

---

## Task 6: ticket-create.html — Payment Section

**Files:**
- Modify: `src/main/resources/templates/ticket-create.html`

- [ ] **Step 1: Check the `<body>` tag for existing `th:with` variables**

Read the body tag line in `ticket-create.html`. If `agentEditorView` and `vendorOnlyEditor` are missing from `th:with`, add them following the same pattern as `ticket-edit.html` (line 6):

```
agentEditorView=${currentFeatures.contains('FEATURE_SITE_VISIT_EDIT') and !currentFeatures.contains('FEATURE_TICKETS_MANAGE') and !currentFeatures.contains('FEATURE_TICKETS_ALL_VIEW') and !currentFeatures.contains('FEATURE_TICKETS_REVIEW')}
vendorOnlyEditor=${currentFeatures.contains('FEATURE_TICKETS_CREATE_VENDOR') and !currentFeatures.contains('FEATURE_TICKETS_CREATE_STANDARD') and !currentFeatures.contains('FEATURE_TICKETS_MANAGE') and !currentFeatures.contains('FEATURE_TICKETS_ALL_VIEW') and !currentFeatures.contains('FEATURE_TICKETS_REVIEW')}
```

- [ ] **Step 2: Replace the old Price Section with the Payment Section**

Find and replace the block (lines 256–282):
```html
                <div class="col-xl-5" th:if="${adminTicketScope}">
                    <section class="form-card glass-card h-100">
                        <div class="section-title mb-3">Price Section</div>
                        ...
                    </section>
                </div>
```

Replace with:
```html
                <div class="col-xl-5" th:if="${adminTicketScope or agentEditorView or vendorOnlyEditor}">
                    <section class="form-card glass-card h-100">
                        <div class="section-title mb-3">Payment</div>

                        <!-- Client Payment -->
                        <div th:if="${adminTicketScope or agentEditorView}" class="mb-4">
                            <div class="mb-2"><span class="badge bg-primary-subtle text-primary">Client Payment</span></div>
                            <div class="row g-2">
                                <div class="col-6">
                                    <label class="form-label small mb-1">Expected Price</label>
                                    <input class="form-control form-control-sm" name="clientExpectedPrice" type="number" step="0.01" min="0" placeholder="0.00">
                                </div>
                                <div class="col-6">
                                    <label class="form-label small mb-1">Actual Price</label>
                                    <input class="form-control form-control-sm" name="clientActualPrice" type="number" step="0.01" min="0" placeholder="0.00">
                                </div>
                                <div class="col-6">
                                    <label class="form-label small mb-1">Mode</label>
                                    <select class="form-select form-select-sm" name="clientPaymentMode">
                                        <option value="">Mode</option>
                                        <option value="CASH">Cash</option>
                                        <option value="BANK_TRANSFER">Bank Transfer</option>
                                        <option value="UPI">UPI</option>
                                    </select>
                                </div>
                                <div class="col-6">
                                    <label class="form-label small mb-1">Status</label>
                                    <select class="form-select form-select-sm" name="clientPaymentStatus">
                                        <option value="">Status</option>
                                        <option value="PENDING">Pending</option>
                                        <option value="PAID_TO_YUBIX">Paid to Yubix</option>
                                        <option value="PAID_TO_TECHNICIAN">Paid to Technician</option>
                                        <option value="PAID_TO_VENDOR">Paid to Vendor</option>
                                    </select>
                                </div>
                                <div class="col-12">
                                    <label class="form-label small mb-1">Payment Date &amp; Time</label>
                                    <input class="form-control form-control-sm" name="clientPaymentDatetime" type="datetime-local">
                                </div>
                            </div>
                        </div>

                        <!-- Technician Payment -->
                        <div th:if="${adminTicketScope or agentEditorView}" class="mb-4">
                            <div class="mb-2"><span class="badge bg-success-subtle text-success">Technician Payment</span></div>
                            <div class="row g-2">
                                <div class="col-6">
                                    <label class="form-label small mb-1">Expected Price</label>
                                    <input class="form-control form-control-sm" name="technicianExpectedPrice" type="number" step="0.01" min="0" placeholder="0.00">
                                </div>
                                <div class="col-6">
                                    <label class="form-label small mb-1">Actual Price</label>
                                    <input class="form-control form-control-sm" name="technicianActualPrice" type="number" step="0.01" min="0" placeholder="0.00">
                                </div>
                                <div class="col-6">
                                    <label class="form-label small mb-1">Mode</label>
                                    <select class="form-select form-select-sm" name="technicianPaymentMode">
                                        <option value="">Mode</option>
                                        <option value="CASH">Cash</option>
                                        <option value="BANK_TRANSFER">Bank Transfer</option>
                                        <option value="UPI">UPI</option>
                                    </select>
                                </div>
                                <div class="col-6">
                                    <label class="form-label small mb-1">Status</label>
                                    <select class="form-select form-select-sm" name="technicianPaymentStatus">
                                        <option value="">Status</option>
                                        <option value="PENDING">Pending</option>
                                        <option value="PAID">Paid</option>
                                    </select>
                                </div>
                                <div class="col-12">
                                    <label class="form-label small mb-1">Payment Date &amp; Time</label>
                                    <input class="form-control form-control-sm" name="technicianPaymentDatetime" type="datetime-local">
                                </div>
                            </div>
                        </div>

                        <!-- Vendor Payment -->
                        <div th:if="${adminTicketScope or vendorOnlyEditor}">
                            <div class="mb-2"><span class="badge bg-warning-subtle text-warning-emphasis">Vendor Payment</span></div>
                            <div class="row g-2">
                                <div class="col-6">
                                    <label class="form-label small mb-1">Expected Price</label>
                                    <input class="form-control form-control-sm" name="vendorExpectedPrice" type="number" step="0.01" min="0" placeholder="0.00">
                                </div>
                                <div class="col-6">
                                    <label class="form-label small mb-1">Actual Price</label>
                                    <input class="form-control form-control-sm" name="vendorActualPrice" type="number" step="0.01" min="0" placeholder="0.00">
                                </div>
                                <div class="col-6">
                                    <label class="form-label small mb-1">Mode</label>
                                    <select class="form-select form-select-sm" name="vendorPaymentMode">
                                        <option value="">Mode</option>
                                        <option value="CASH">Cash</option>
                                        <option value="BANK_TRANSFER">Bank Transfer</option>
                                        <option value="UPI">UPI</option>
                                    </select>
                                </div>
                                <div class="col-6">
                                    <label class="form-label small mb-1">Status</label>
                                    <select class="form-select form-select-sm" name="vendorPaymentStatus">
                                        <option value="">Status</option>
                                        <option value="PENDING">Pending</option>
                                        <option value="PAID">Paid</option>
                                    </select>
                                </div>
                                <div class="col-12">
                                    <label class="form-label small mb-1">Payment Date &amp; Time</label>
                                    <input class="form-control form-control-sm" name="vendorPaymentDatetime" type="datetime-local">
                                </div>
                            </div>
                        </div>

                    </section>
                </div>
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/ticket-create.html
git commit -m "feat: replace Price Section with Payment Section on ticket-create page"
```

---

## Task 7: ticket-edit.html — Payment Section with Pre-populated Values

**Files:**
- Modify: `src/main/resources/templates/ticket-edit.html`

`agentEditorView` and `vendorOnlyEditor` are already defined in the body `th:with` on line 6 of `ticket-edit.html`.

- [ ] **Step 1: Replace the old Price Section**

Find and replace the block starting at line 230 (`<div class="col-xl-5" th:if="${adminTicketScope}">`), ending at line 256 (closing `</div>`).

Replace with:

```html
                <div class="col-xl-5" th:if="${adminTicketScope or agentEditorView or vendorOnlyEditor}"
                     th:with="clientPmt=${ticket.paymentByType('CLIENT')},techPmt=${ticket.paymentByType('TECHNICIAN')},vendorPmt=${ticket.paymentByType('VENDOR')}">
                    <section class="form-card glass-card h-100">
                        <div class="section-title mb-3">Payment</div>

                        <!-- Client Payment -->
                        <div th:if="${adminTicketScope or agentEditorView}" class="mb-4">
                            <div class="mb-2"><span class="badge bg-primary-subtle text-primary">Client Payment</span></div>
                            <div class="row g-2">
                                <div class="col-6">
                                    <label class="form-label small mb-1">Expected Price</label>
                                    <input class="form-control form-control-sm" name="clientExpectedPrice" type="number" step="0.01" min="0" placeholder="0.00"
                                           th:value="${clientPmt != null ? clientPmt.expectedPrice() : null}">
                                </div>
                                <div class="col-6">
                                    <label class="form-label small mb-1">Actual Price</label>
                                    <input class="form-control form-control-sm" name="clientActualPrice" type="number" step="0.01" min="0" placeholder="0.00"
                                           th:value="${clientPmt != null ? clientPmt.actualPrice() : null}">
                                </div>
                                <div class="col-6">
                                    <label class="form-label small mb-1">Mode</label>
                                    <select class="form-select form-select-sm" name="clientPaymentMode">
                                        <option value="">Mode</option>
                                        <option value="CASH" th:selected="${clientPmt != null and 'CASH' == clientPmt.paymentMode()}">Cash</option>
                                        <option value="BANK_TRANSFER" th:selected="${clientPmt != null and 'BANK_TRANSFER' == clientPmt.paymentMode()}">Bank Transfer</option>
                                        <option value="UPI" th:selected="${clientPmt != null and 'UPI' == clientPmt.paymentMode()}">UPI</option>
                                    </select>
                                </div>
                                <div class="col-6">
                                    <label class="form-label small mb-1">Status</label>
                                    <select class="form-select form-select-sm" name="clientPaymentStatus">
                                        <option value="">Status</option>
                                        <option value="PENDING" th:selected="${clientPmt != null and 'PENDING' == clientPmt.status()}">Pending</option>
                                        <option value="PAID_TO_YUBIX" th:selected="${clientPmt != null and 'PAID_TO_YUBIX' == clientPmt.status()}">Paid to Yubix</option>
                                        <option value="PAID_TO_TECHNICIAN" th:selected="${clientPmt != null and 'PAID_TO_TECHNICIAN' == clientPmt.status()}">Paid to Technician</option>
                                        <option value="PAID_TO_VENDOR" th:selected="${clientPmt != null and 'PAID_TO_VENDOR' == clientPmt.status()}">Paid to Vendor</option>
                                    </select>
                                </div>
                                <div class="col-12">
                                    <label class="form-label small mb-1">Payment Date &amp; Time</label>
                                    <input class="form-control form-control-sm" name="clientPaymentDatetime" type="datetime-local"
                                           th:value="${clientPmt != null and clientPmt.paymentDatetime() != null ? #temporals.format(clientPmt.paymentDatetime(), 'yyyy-MM-dd''T''HH:mm') : ''}">
                                </div>
                            </div>
                        </div>

                        <!-- Technician Payment -->
                        <div th:if="${adminTicketScope or agentEditorView}" class="mb-4">
                            <div class="mb-2"><span class="badge bg-success-subtle text-success">Technician Payment</span></div>
                            <div class="row g-2">
                                <div class="col-6">
                                    <label class="form-label small mb-1">Expected Price</label>
                                    <input class="form-control form-control-sm" name="technicianExpectedPrice" type="number" step="0.01" min="0" placeholder="0.00"
                                           th:value="${techPmt != null ? techPmt.expectedPrice() : null}">
                                </div>
                                <div class="col-6">
                                    <label class="form-label small mb-1">Actual Price</label>
                                    <input class="form-control form-control-sm" name="technicianActualPrice" type="number" step="0.01" min="0" placeholder="0.00"
                                           th:value="${techPmt != null ? techPmt.actualPrice() : null}">
                                </div>
                                <div class="col-6">
                                    <label class="form-label small mb-1">Mode</label>
                                    <select class="form-select form-select-sm" name="technicianPaymentMode">
                                        <option value="">Mode</option>
                                        <option value="CASH" th:selected="${techPmt != null and 'CASH' == techPmt.paymentMode()}">Cash</option>
                                        <option value="BANK_TRANSFER" th:selected="${techPmt != null and 'BANK_TRANSFER' == techPmt.paymentMode()}">Bank Transfer</option>
                                        <option value="UPI" th:selected="${techPmt != null and 'UPI' == techPmt.paymentMode()}">UPI</option>
                                    </select>
                                </div>
                                <div class="col-6">
                                    <label class="form-label small mb-1">Status</label>
                                    <select class="form-select form-select-sm" name="technicianPaymentStatus">
                                        <option value="">Status</option>
                                        <option value="PENDING" th:selected="${techPmt != null and 'PENDING' == techPmt.status()}">Pending</option>
                                        <option value="PAID" th:selected="${techPmt != null and 'PAID' == techPmt.status()}">Paid</option>
                                    </select>
                                </div>
                                <div class="col-12">
                                    <label class="form-label small mb-1">Payment Date &amp; Time</label>
                                    <input class="form-control form-control-sm" name="technicianPaymentDatetime" type="datetime-local"
                                           th:value="${techPmt != null and techPmt.paymentDatetime() != null ? #temporals.format(techPmt.paymentDatetime(), 'yyyy-MM-dd''T''HH:mm') : ''}">
                                </div>
                            </div>
                        </div>

                        <!-- Vendor Payment -->
                        <div th:if="${adminTicketScope or vendorOnlyEditor}">
                            <div class="mb-2"><span class="badge bg-warning-subtle text-warning-emphasis">Vendor Payment</span></div>
                            <div class="row g-2">
                                <div class="col-6">
                                    <label class="form-label small mb-1">Expected Price</label>
                                    <input class="form-control form-control-sm" name="vendorExpectedPrice" type="number" step="0.01" min="0" placeholder="0.00"
                                           th:value="${vendorPmt != null ? vendorPmt.expectedPrice() : null}">
                                </div>
                                <div class="col-6">
                                    <label class="form-label small mb-1">Actual Price</label>
                                    <input class="form-control form-control-sm" name="vendorActualPrice" type="number" step="0.01" min="0" placeholder="0.00"
                                           th:value="${vendorPmt != null ? vendorPmt.actualPrice() : null}">
                                </div>
                                <div class="col-6">
                                    <label class="form-label small mb-1">Mode</label>
                                    <select class="form-select form-select-sm" name="vendorPaymentMode">
                                        <option value="">Mode</option>
                                        <option value="CASH" th:selected="${vendorPmt != null and 'CASH' == vendorPmt.paymentMode()}">Cash</option>
                                        <option value="BANK_TRANSFER" th:selected="${vendorPmt != null and 'BANK_TRANSFER' == vendorPmt.paymentMode()}">Bank Transfer</option>
                                        <option value="UPI" th:selected="${vendorPmt != null and 'UPI' == vendorPmt.paymentMode()}">UPI</option>
                                    </select>
                                </div>
                                <div class="col-6">
                                    <label class="form-label small mb-1">Status</label>
                                    <select class="form-select form-select-sm" name="vendorPaymentStatus">
                                        <option value="">Status</option>
                                        <option value="PENDING" th:selected="${vendorPmt != null and 'PENDING' == vendorPmt.status()}">Pending</option>
                                        <option value="PAID" th:selected="${vendorPmt != null and 'PAID' == vendorPmt.status()}">Paid</option>
                                    </select>
                                </div>
                                <div class="col-12">
                                    <label class="form-label small mb-1">Payment Date &amp; Time</label>
                                    <input class="form-control form-control-sm" name="vendorPaymentDatetime" type="datetime-local"
                                           th:value="${vendorPmt != null and vendorPmt.paymentDatetime() != null ? #temporals.format(vendorPmt.paymentDatetime(), 'yyyy-MM-dd''T''HH:mm') : ''}">
                                </div>
                            </div>
                        </div>

                    </section>
                </div>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/ticket-edit.html
git commit -m "feat: replace Price Section with pre-populated Payment Section on ticket-edit page"
```

---

## Task 8: ticket-view.html — Read-only Payment Section with Status Badges

**Files:**
- Modify: `src/main/resources/templates/ticket-view.html`

- [ ] **Step 1: Replace the old Price Section**

Find and replace the block at lines 164–184:
```html
                    <section class="form-card glass-card mb-4" th:if="${adminTicketScope}">
                        <div class="section-title mb-3">Price Section</div>
                        ...
                    </section>
```

Replace with:

```html
                    <section class="form-card glass-card mb-4"
                             th:if="${adminTicketScope or agentViewer or vendorOnlyViewer}"
                             th:with="clientPmt=${ticket.paymentByType('CLIENT')},techPmt=${ticket.paymentByType('TECHNICIAN')},vendorPmt=${ticket.paymentByType('VENDOR')}">
                        <div class="section-title mb-3">Payment</div>

                        <!-- Client Payment -->
                        <div th:if="${adminTicketScope or agentViewer}" class="mb-4">
                            <div class="d-flex align-items-center gap-2 mb-2">
                                <span class="badge bg-primary-subtle text-primary">Client Payment</span>
                                <span th:if="${clientPmt != null and clientPmt.status() != null}"
                                      th:class="${'badge ' + (clientPmt.status() == 'PENDING' ? 'bg-warning text-dark' : 'bg-primary')}"
                                      th:text="${clientPmt.status() == 'PENDING' ? 'Pending' : (clientPmt.status() == 'PAID_TO_YUBIX' ? 'Paid to Yubix' : (clientPmt.status() == 'PAID_TO_TECHNICIAN' ? 'Paid to Technician' : 'Paid to Vendor'))}">
                                </span>
                            </div>
                            <div class="row g-2">
                                <div class="col-6">
                                    <div class="small text-muted text-uppercase mb-1">Expected Price</div>
                                    <div class="fw-semibold" th:text="${clientPmt != null and clientPmt.expectedPrice() != null ? clientPmt.expectedPrice() : '-'}">-</div>
                                </div>
                                <div class="col-6">
                                    <div class="small text-muted text-uppercase mb-1">Actual Price</div>
                                    <div class="fw-semibold" th:text="${clientPmt != null and clientPmt.actualPrice() != null ? clientPmt.actualPrice() : '-'}">-</div>
                                </div>
                                <div class="col-6">
                                    <div class="small text-muted text-uppercase mb-1">Mode</div>
                                    <div class="fw-semibold" th:text="${clientPmt != null and clientPmt.paymentMode() != null ? (clientPmt.paymentMode() == 'CASH' ? 'Cash' : (clientPmt.paymentMode() == 'BANK_TRANSFER' ? 'Bank Transfer' : 'UPI')) : '-'}">-</div>
                                </div>
                                <div class="col-6">
                                    <div class="small text-muted text-uppercase mb-1">Paid On</div>
                                    <div class="fw-semibold" th:text="${clientPmt != null and clientPmt.paymentDatetime() != null ? #temporals.format(clientPmt.paymentDatetime(), 'dd MMM yyyy, HH:mm') : '-'}">-</div>
                                </div>
                            </div>
                        </div>

                        <!-- Technician Payment -->
                        <div th:if="${adminTicketScope or agentViewer}" class="mb-4">
                            <div class="d-flex align-items-center gap-2 mb-2">
                                <span class="badge bg-success-subtle text-success">Technician Payment</span>
                                <span th:if="${techPmt != null and techPmt.status() != null}"
                                      th:class="${'badge ' + (techPmt.status() == 'PENDING' ? 'bg-warning text-dark' : 'bg-success')}"
                                      th:text="${techPmt.status() == 'PENDING' ? 'Pending' : 'Paid'}">
                                </span>
                            </div>
                            <div class="row g-2">
                                <div class="col-6">
                                    <div class="small text-muted text-uppercase mb-1">Expected Price</div>
                                    <div class="fw-semibold" th:text="${techPmt != null and techPmt.expectedPrice() != null ? techPmt.expectedPrice() : '-'}">-</div>
                                </div>
                                <div class="col-6">
                                    <div class="small text-muted text-uppercase mb-1">Actual Price</div>
                                    <div class="fw-semibold" th:text="${techPmt != null and techPmt.actualPrice() != null ? techPmt.actualPrice() : '-'}">-</div>
                                </div>
                                <div class="col-6">
                                    <div class="small text-muted text-uppercase mb-1">Mode</div>
                                    <div class="fw-semibold" th:text="${techPmt != null and techPmt.paymentMode() != null ? (techPmt.paymentMode() == 'CASH' ? 'Cash' : (techPmt.paymentMode() == 'BANK_TRANSFER' ? 'Bank Transfer' : 'UPI')) : '-'}">-</div>
                                </div>
                                <div class="col-6">
                                    <div class="small text-muted text-uppercase mb-1">Paid On</div>
                                    <div class="fw-semibold" th:text="${techPmt != null and techPmt.paymentDatetime() != null ? #temporals.format(techPmt.paymentDatetime(), 'dd MMM yyyy, HH:mm') : '-'}">-</div>
                                </div>
                            </div>
                        </div>

                        <!-- Vendor Payment -->
                        <div th:if="${adminTicketScope or vendorOnlyViewer}">
                            <div class="d-flex align-items-center gap-2 mb-2">
                                <span class="badge bg-warning-subtle text-warning-emphasis">Vendor Payment</span>
                                <span th:if="${vendorPmt != null and vendorPmt.status() != null}"
                                      th:class="${'badge ' + (vendorPmt.status() == 'PENDING' ? 'bg-warning text-dark' : 'bg-success')}"
                                      th:text="${vendorPmt.status() == 'PENDING' ? 'Pending' : 'Paid'}">
                                </span>
                            </div>
                            <div class="row g-2">
                                <div class="col-6">
                                    <div class="small text-muted text-uppercase mb-1">Expected Price</div>
                                    <div class="fw-semibold" th:text="${vendorPmt != null and vendorPmt.expectedPrice() != null ? vendorPmt.expectedPrice() : '-'}">-</div>
                                </div>
                                <div class="col-6">
                                    <div class="small text-muted text-uppercase mb-1">Actual Price</div>
                                    <div class="fw-semibold" th:text="${vendorPmt != null and vendorPmt.actualPrice() != null ? vendorPmt.actualPrice() : '-'}">-</div>
                                </div>
                                <div class="col-6">
                                    <div class="small text-muted text-uppercase mb-1">Mode</div>
                                    <div class="fw-semibold" th:text="${vendorPmt != null and vendorPmt.paymentMode() != null ? (vendorPmt.paymentMode() == 'CASH' ? 'Cash' : (vendorPmt.paymentMode() == 'BANK_TRANSFER' ? 'Bank Transfer' : 'UPI')) : '-'}">-</div>
                                </div>
                                <div class="col-6">
                                    <div class="small text-muted text-uppercase mb-1">Paid On</div>
                                    <div class="fw-semibold" th:text="${vendorPmt != null and vendorPmt.paymentDatetime() != null ? #temporals.format(vendorPmt.paymentDatetime(), 'dd MMM yyyy, HH:mm') : '-'}">-</div>
                                </div>
                            </div>
                        </div>

                    </section>
```

- [ ] **Step 2: Compile and verify no Thymeleaf template errors**

```bash
cd /Users/aghilkrishna/work/workspaces/TicketManager && ./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/ticket-view.html
git commit -m "feat: replace Price Section with read-only Payment Section on ticket-view page"
```

---

## Spec Coverage Check

| Spec requirement | Covered by |
|---|---|
| Client/Technician/Vendor subsections | Tasks 6, 7, 8 |
| Expected Price, Actual Price, Mode, Datetime, Status per type | Tasks 6, 7, 8 |
| Client statuses: Pending, Paid to Yubix, Paid to Technician, Paid to Vendor | Tasks 6, 7, 8 |
| Technician/Vendor statuses: Pending, Paid | Tasks 6, 7, 8 |
| Separate rows in ticket_payments table | Tasks 1, 2 |
| Admin sees all 3 | Tasks 6, 7, 8 (adminTicketScope condition) |
| Agent sees Client + Technician | Tasks 6, 7, 8 (agentEditorView/agentViewer condition) |
| Vendor sees Vendor only | Tasks 6, 7, 8 (vendorOnlyEditor/vendorOnlyViewer condition) |
| Old price fields kept in DB, hidden from UI | Tasks 3–5 (fields untouched), no old UI rendered |
| Flyway migration V13 | Task 1 |
| Hibernate enum constraint pitfall avoided on paymentMode | Task 2 (columnDefinition) |
