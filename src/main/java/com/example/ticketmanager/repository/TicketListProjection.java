package com.example.ticketmanager.repository;

import java.time.LocalDateTime;

public interface TicketListProjection {
    Long getId();
    String getTitle();
    String getDescription();
    String getServiceType();
    String getStatus();
    String getPriority();
    LocalDateTime getScheduleDate();
    String getCreatedBy();
    String getAssignedTo();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();
}
