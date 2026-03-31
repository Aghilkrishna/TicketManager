package com.example.ticketmanager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "email_notification_settings")
public class EmailNotificationSetting {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "action_name", nullable = false, length = 60)
    private EmailNotificationAction action;

    @Column(nullable = false)
    private boolean enabled = true;
}
