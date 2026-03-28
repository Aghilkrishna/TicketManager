package com.example.ticketmanager.repository;

import com.example.ticketmanager.entity.EmailNotificationAction;
import com.example.ticketmanager.entity.EmailNotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailNotificationSettingRepository extends JpaRepository<EmailNotificationSetting, EmailNotificationAction> {
}
