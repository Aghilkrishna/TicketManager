package com.example.ticketmanager.config;

import com.example.ticketmanager.entity.*;
import com.example.ticketmanager.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TicketRepository ticketRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final EmailNotificationSettingRepository emailNotificationSettingRepository;
    private final NotificationRepository notificationRepository;
    private final UserIdProofRepository userIdProofRepository;

    @Bean
    CommandLineRunner seedData() {
        return args -> {
            log.info("Starting data initialization");
            
            seedRole("ROLE_ADMIN", "Full administrative access", Set.of(
                    AppFeature.DASHBOARD_ACCESS,
                    AppFeature.DASHBOARD_MY_TICKET_STATUS,
                    AppFeature.DASHBOARD_ALL_TICKET_STATUS,
                    AppFeature.DASHBOARD_USER_COUNT,
                    AppFeature.PROFILE_ACCESS,
                    AppFeature.TICKETS_VIEW,
                    AppFeature.TICKETS_MANAGE,
                    AppFeature.TICKETS_CREATE_STANDARD,
                    AppFeature.TICKETS_REVIEW,
                    AppFeature.TICKETS_ALL_VIEW,
                    AppFeature.CHAT_ACCESS,
                    AppFeature.ADMIN_ACCESS,
                    AppFeature.ADMIN_SUPPORT_TICKETS,
                    AppFeature.ADMIN_USER_MANAGEMENT,
                    AppFeature.ADMIN_ROLE_MANAGEMENT,
                    AppFeature.ADMIN_ROLE_FEATURE_ASSIGNMENT,
                    AppFeature.ADMIN_EMAIL_NOTIFICATION_MANAGEMENT,
                    AppFeature.ADMIN_STAFF_BILLING,
                    AppFeature.ADMIN_REPORT_ACCESS
            ));
            seedRole("ROLE_MANAGER", "Manage ticket operations", Set.of(
                    AppFeature.DASHBOARD_ACCESS,
                    AppFeature.DASHBOARD_MY_TICKET_STATUS,
                    AppFeature.DASHBOARD_ALL_TICKET_STATUS,
                    AppFeature.PROFILE_ACCESS,
                    AppFeature.TICKETS_VIEW,
                    AppFeature.TICKETS_MANAGE,
                    AppFeature.TICKETS_CREATE_STANDARD,
                    AppFeature.TICKETS_REVIEW,
                    AppFeature.TICKETS_ALL_VIEW,
                    AppFeature.CHAT_ACCESS
            ));
            seedRole("ROLE_AGENT", "Work assigned tickets and chat", Set.of(
                    AppFeature.DASHBOARD_ACCESS,
                    AppFeature.DASHBOARD_MY_TICKET_STATUS,
                    AppFeature.PROFILE_ACCESS,
                    AppFeature.TICKETS_VIEW,
                    AppFeature.SITE_VISIT_EDIT,
                    AppFeature.CHAT_ACCESS
            ));
            seedRole("ROLE_VENDOR", "Create and manage vendor-owned tickets", Set.of(
                    AppFeature.DASHBOARD_ACCESS,
                    AppFeature.DASHBOARD_MY_TICKET_STATUS,
                    AppFeature.PROFILE_ACCESS,
                    AppFeature.TICKETS_VIEW,
                    AppFeature.TICKETS_MANAGE,
                    AppFeature.TICKETS_CREATE_VENDOR,
                    AppFeature.TICKETS_CREATED_VIEW
            ));
            seedRole("ROLE_USER", "Standard end user access", Set.of(
                    AppFeature.DASHBOARD_ACCESS,
                    AppFeature.PROFILE_ACCESS,
                    AppFeature.TICKETS_VIEW,
                    AppFeature.CHAT_ACCESS
            ));
            if (userRepository.count() == 0) {
                log.info("Creating default users");
                createUser("admin", "admin@example.com", "9999999999", "Admin@123", "ROLE_ADMIN", "ROLE_MANAGER");
                createUser("manager", "manager@example.com", "8888888888", "Manager@123", "ROLE_MANAGER");
                createUser("agent", "agent@example.com", "7777777777", "Agent@123", "ROLE_AGENT");
                createUser("vendor", "vendor@example.com", "5555555555", "Vendor@123", "ROLE_VENDOR");
                createUser("user", "user@example.com", "6666666666", "User@123", "ROLE_USER");
                log.info("Default users created successfully");
            } else {
                log.info("Users already exist, skipping user creation");
            }
            
            // Seed email notification settings
            seedEmailNotificationSettings();
            
            // Create sample tickets and related data
            if (ticketRepository.count() == 0) {
                log.info("Creating sample tickets and related data");
                createSampleTickets();
                log.info("Sample tickets created successfully");
            } else {
                log.info("Tickets already exist, skipping ticket creation");
            }
            
            // Create sample notifications
            if (notificationRepository.count() == 0) {
                log.info("Creating sample notifications");
                createSampleNotifications();
                log.info("Sample notifications created successfully");
            } else {
                log.info("Notifications already exist, skipping notification creation");
            }
            
            // Create sample user ID proofs
            if (userIdProofRepository.count() == 0) {
                log.info("Creating sample user ID proofs");
                createSampleUserIdProofs();
                log.info("Sample user ID proofs created successfully");
            } else {
                log.info("User ID proofs already exist, skipping ID proof creation");
            }
            
            log.info("Data initialization completed successfully");
        };
    }

    private void seedRole(String name, String description, Set<AppFeature> features) {
        roleRepository.findByName(name).ifPresentOrElse(existing -> {
            log.debug("Role {} already exists, checking for updates", name);
            boolean changed = false;
            if (existing.getDescription() == null || existing.getDescription().isBlank()) {
                existing.setDescription(description);
                changed = true;
            }
            if (existing.getFeatures() == null || existing.getFeatures().isEmpty()) {
                existing.setFeatures(new HashSet<>(features));
                existing.setActive(true);
                changed = true;
            } else {
                // Always ensure any newly-seeded features are present on the role
                for (AppFeature f : features) {
                    if (!existing.getFeatures().contains(f)) {
                        existing.getFeatures().add(f);
                        changed = true;
                    }
                }
            }
            if (changed) {
                roleRepository.save(existing);
                log.info("Updated role {} with description and features", name);
            }
        }, () -> {
            log.debug("Creating new role: {}", name);
            Role role = new Role(name, description);
            role.setActive(true);
            role.setFeatures(new HashSet<>(features));
            roleRepository.save(role);
            log.info("Created new role: {} with {} features", name, features.size());
        });
    }

    private void createUser(String username, String email, String phone, String rawPassword, String... roles) {
        log.debug("Creating user: {} with roles: {}", email, Arrays.toString(roles));
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setEmailVerified(true);
        user.setPhoneVerified(true);
        user.setEnabled(true);
        
        // Set name fields based on username
        String[] nameParts = username.split("(?=[A-Z])");
        if (nameParts.length > 1) {
            user.setFirstName(nameParts[0]);
            user.setLastName(String.join("", java.util.Arrays.copyOfRange(nameParts, 1, nameParts.length)));
        } else {
            user.setFirstName(username);
            user.setLastName("User");
        }
        
        // Set company information
        user.setCompanyName("Default Company");
        user.setContactPerson(user.getFirstName() + " " + user.getLastName());
        user.setGstNumber("GST" + username.toUpperCase() + "123456");
        
        // Set address information
        user.setFlat("A-" + (int)(Math.random() * 999 + 1));
        user.setBuilding("Tech Building");
        user.setArea("Business District");
        user.setCity("Mumbai");
        user.setState("Maharashtra");
        user.setCountry("India");
        user.setPincode("400001");
        
        Arrays.stream(roles)
                .map(role -> roleRepository.findByName(role).orElseThrow())
                .forEach(user.getRoles()::add);
        userRepository.save(user);
        log.info("Created user: {} with {} roles", email, roles.length);
    }
    
    private void createSampleTickets() {
        AppUser admin = userRepository.findByUsername("admin").orElseThrow();
        AppUser manager = userRepository.findByUsername("manager").orElseThrow();
        AppUser agent = userRepository.findByUsername("agent").orElseThrow();
        AppUser vendor = userRepository.findByUsername("vendor").orElseThrow();
        AppUser user = userRepository.findByUsername("user").orElseThrow();
        
        // Create sample customer addresses
        CustomerAddress address1 = createCustomerAddress("John Doe", "john@example.com", "9876543210", 
            "A-101", "123 Main Street", "Mumbai", "Maharashtra", "400001", "https://maps.example.com/addr1");
        CustomerAddress address2 = createCustomerAddress("Jane Smith", "jane@example.com", "9876543211", 
            "B-205", "456 Park Avenue", "Delhi", "Delhi", "110001", "https://maps.example.com/addr2");
        CustomerAddress address3 = createCustomerAddress("Bob Wilson", "bob@example.com", "9876543212", 
            "C-303", "789 Commercial Road", "Bangalore", "Karnataka", "560001", "https://maps.example.com/addr3");
        
        // Create sample tickets
        createTicket("Internet Connection Issue", "Customer reports slow internet speed and frequent disconnections. Need to check router configuration and line quality.", 
            TicketServiceType.SERVICE, TicketPriority.HIGH, user, agent, address1, new BigDecimal("500.00"));
            
        createTicket("New CCTV Installation", "Install 4 CCTV cameras at commercial premises. Includes wiring, camera mounting, and DVR setup.", 
            TicketServiceType.INSTALLATION, TicketPriority.MEDIUM, manager, vendor, address2, new BigDecimal("15000.00"));
            
        createTicket("Annual Maintenance Contract", "Schedule annual maintenance for existing network infrastructure at corporate office.", 
            TicketServiceType.AMC, TicketPriority.LOW, admin, manager, address3, new BigDecimal("25000.00"));
            
        createTicket("Router Replacement", "Replace faulty router with new model. Configure network settings and ensure connectivity.", 
            TicketServiceType.REPAIR, TicketPriority.CRITICAL, user, agent, address1, new BigDecimal("2000.00"));
            
        createTicket("Site Survey for New Office", "Conduct site survey for new office setup. Assess cabling requirements and network infrastructure needs.", 
            TicketServiceType.SITE_VISIT, TicketPriority.MEDIUM, manager, agent, address2, new BigDecimal("3000.00"));
            
        createTicket("Network Cable Installation", "Install network cables for new office floor. Approximately 50 data points required.", 
            TicketServiceType.INSTALLATION, TicketPriority.HIGH, admin, vendor, address3, new BigDecimal("8000.00"));
            
        createTicket("WiFi Access Point Setup", "Setup WiFi access points for better coverage in warehouse area.", 
            TicketServiceType.SERVICE, TicketPriority.MEDIUM, user, agent, address1, new BigDecimal("3500.00"));
            
        createTicket("Server Maintenance", "Perform routine maintenance on servers. Update OS and check hardware components.", 
            TicketServiceType.MAINTENANCE, TicketPriority.HIGH, manager, agent, address2, new BigDecimal("12000.00"));
            
        // Add comments to some tickets
        addCommentsToTickets();
    }
    
    private CustomerAddress createCustomerAddress(String name, String email, String phone, String flat, String street, 
                                                  String city, String state, String pincode, String locationLink) {
        CustomerAddress address = new CustomerAddress();
        address.setCustomerName(name);
        address.setCustomerEmail(email);
        address.setCustomerPhone(phone);
        address.setFlat(flat);
        address.setStreet(street);
        address.setCity(city);
        address.setState(state);
        address.setPincode(pincode);
        address.setLocationLink(locationLink);
        address.setCreatedBy(userRepository.findByUsername("admin").orElseThrow());
        return customerAddressRepository.save(address);
    }
    
    private void createTicket(String title, String description, TicketServiceType serviceType, TicketPriority priority, 
                             AppUser createdBy, AppUser assignedTo, CustomerAddress address, BigDecimal estimatedCost) {
        Ticket ticket = new Ticket();
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setServiceType(serviceType);
        ticket.setPriority(priority);
        ticket.setCreatedBy(createdBy);
        ticket.setAssignedTo(assignedTo);
        ticket.setPricingModel(TicketPricingModel.FIXED_PRICE);
        ticket.setEstimatedCost(estimatedCost);
        ticket.setBillingStatus(TicketBillingStatus.UNPAID);
        ticket.setScheduleDate(LocalDate.now().plusDays((int)(Math.random() * 10) + 1));
        
        // Set customer details from address
        ticket.setCustomerName(address.getCustomerName());
        ticket.setCustomerEmail(address.getCustomerEmail());
        ticket.setCustomerPhone(address.getCustomerPhone());
        ticket.setCustomerFlat(address.getFlat());
        ticket.setCustomerStreet(address.getStreet());
        ticket.setCustomerCity(address.getCity());
        ticket.setCustomerState(address.getState());
        ticket.setCustomerPincode(address.getPincode());
        ticket.setCustomerLocationLink(address.getLocationLink());
        ticket.setCustomerAddressReferenceId(address.getId());
        ticket.setAddress(address.getFlat() + ", " + address.getStreet() + ", " + 
                          address.getCity() + ", " + address.getState() + " - " + address.getPincode());
        
        // Set random status for variety
        TicketStatus[] statuses = {TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.SITE_VISITED, TicketStatus.ON_HOLD};
        ticket.setStatus(statuses[(int)(Math.random() * statuses.length)]);
        
        ticketRepository.save(ticket);
    }
    
    private void addCommentsToTickets() {
        AppUser admin = userRepository.findByUsername("admin").orElseThrow();
        AppUser agent = userRepository.findByUsername("agent").orElseThrow();
        
        // Get some tickets to add comments to
        java.util.List<Ticket> tickets = ticketRepository.findAll();
        if (tickets.size() >= 3) {
            // Add comment to first ticket
            TicketComment comment1 = new TicketComment();
            comment1.setTicket(tickets.get(0));
            comment1.setContent("Initial assessment completed. Router needs replacement. Parts ordered.");
            comment1.setAuthor(agent);
            comment1.setCreatedAt(LocalDateTime.now().minusHours(2));
            ticketCommentRepository.save(comment1);
            
            // Add comment to second ticket
            TicketComment comment2 = new TicketComment();
            comment2.setTicket(tickets.get(1));
            comment2.setContent("Site visit scheduled for tomorrow. Customer confirmed availability.");
            comment2.setAuthor(admin);
            comment2.setCreatedAt(LocalDateTime.now().minusHours(4));
            ticketCommentRepository.save(comment2);
            
            // Add comment to third ticket
            TicketComment comment3 = new TicketComment();
            comment3.setTicket(tickets.get(2));
            comment3.setContent("AMC contract reviewed and approved. Schedule maintenance for next week.");
            comment3.setAuthor(agent);
            comment3.setCreatedAt(LocalDateTime.now().minusHours(6));
            ticketCommentRepository.save(comment3);
        }
    }
    
    private void seedEmailNotificationSettings() {
        log.info("Seeding email notification settings");
        
        for (EmailNotificationAction action : EmailNotificationAction.values()) {
            emailNotificationSettingRepository.findById(action).ifPresentOrElse(existing -> {
                log.debug("Email notification setting for {} already exists", action.label());
            }, () -> {
                EmailNotificationSetting setting = new EmailNotificationSetting();
                setting.setAction(action);
                
                // Configure default settings based on action type
                switch (action) {
                    case ACCOUNT_VERIFICATION:
                    case PASSWORD_RESET:
                        setting.setEmailEnabled(true);
                        setting.setSmsEnabled(false);
                        break;
                    case TICKET_CREATED:
                    case TICKET_UPDATED:
                    case COMMENT_ADDED:
                    case SITE_VISIT_ADDED:
                        setting.setEmailEnabled(true);
                        setting.setSmsEnabled(true);
                        break;
                    case ADMIN_TICKET_ACTIVITY:
                    case VENDOR_CREATED_TICKET_ACTIVITY:
                        setting.setEmailEnabled(true);
                        setting.setSmsEnabled(true);
                        break;
                    case CHAT_MESSAGE:
                        setting.setEmailEnabled(false);
                        setting.setSmsEnabled(true);
                        break;
                }
                
                emailNotificationSettingRepository.save(setting);
                log.info("Created email notification setting for {}", action.label());
            });
        }
    }
    
    private void createSampleNotifications() {
        AppUser admin = userRepository.findByUsername("admin").orElseThrow();
        AppUser agent = userRepository.findByUsername("agent").orElseThrow();
        AppUser user = userRepository.findByUsername("user").orElseThrow();
        
        // Create sample notifications for admin
        createNotification(admin, NotificationType.TICKET_UPDATED, 
            "New ticket #1001 has been created by user", "TICKET", 1001L);
        createNotification(admin, NotificationType.COMMENT_ADDED, 
            "New comment added to ticket #1002", "TICKET", 1002L);
        createNotification(admin, NotificationType.ACCOUNT_EVENT, 
            "New user registration: vendor@example.com", "USER", null);
        
        // Create sample notifications for agent
        createNotification(agent, NotificationType.TICKET_UPDATED, 
            "You have been assigned to ticket #1003", "TICKET", 1003L);
        createNotification(agent, NotificationType.CHAT_MESSAGE, 
            "New message from customer in chat #1", "CHAT", 1L);
        
        // Create sample notifications for regular user
        createNotification(user, NotificationType.TICKET_UPDATED, 
            "Your ticket #1004 status has been updated to IN_PROGRESS", "TICKET", 1004L);
        createNotification(user, NotificationType.COMMENT_ADDED, 
            "New comment added to your ticket #1004", "TICKET", 1004L);
    }
    
    private void createNotification(AppUser user, NotificationType type, String message, String referenceType, Long referenceId) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setMessage(message);
        notification.setReferenceType(referenceType);
        notification.setReferenceId(referenceId);
        notification.setReadFlag(Math.random() > 0.7); // 30% chance of being read
        notificationRepository.save(notification);
    }
    
    private void createSampleUserIdProofs() {
        AppUser admin = userRepository.findByUsername("admin").orElseThrow();
        AppUser vendor = userRepository.findByUsername("vendor").orElseThrow();
        AppUser user = userRepository.findByUsername("user").orElseThrow();
        
        // Create ID proofs for admin (verified)
        createUserIdProof(admin, "AADHAR_CARD", true, "Admin ID proof verified");
        
        // Create ID proofs for vendor (verified)
        createUserIdProof(vendor, "PAN_CARD", true, "Vendor PAN card verified");
        createUserIdProof(vendor, "GST_CERTIFICATE", true, "Vendor GST certificate verified");
        
        // Create ID proofs for regular user (pending verification)
        createUserIdProof(user, "AADHAR_CARD", false, "Pending verification");
        createUserIdProof(user, "VOTER_ID", false, "Pending verification");
    }
    
    private void createUserIdProof(AppUser user, String idProofType, boolean verified, String verificationNotes) {
        UserIdProof idProof = UserIdProof.builder()
            .user(user)
            .idProofType(idProofType)
            .idProofFileName(idProofType.toLowerCase() + "_" + user.getUsername() + ".pdf")
            .idProofContentType("application/pdf")
            .fileSize(1024L * 100) // 100KB dummy size
            .uploadStatus("UPLOADED")
            .verified(verified)
            .verificationNotes(verificationNotes)
            .build();
        
        userIdProofRepository.save(idProof);
    }
}
