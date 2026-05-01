package com.example.ticketmanager.service;

import com.example.ticketmanager.dto.AdminDtos;
import com.example.ticketmanager.entity.EmailNotificationAction;
import com.example.ticketmanager.entity.EmailNotificationSetting;
import com.example.ticketmanager.exception.AppException;
import com.example.ticketmanager.repository.EmailNotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmailNotificationSettingsService {
    private final EmailNotificationSettingRepository settingRepository;

    @Transactional(readOnly = true)
    public boolean isEnabled(EmailNotificationAction action) {
        return isEmailEnabled(action);
    }

    @Transactional(readOnly = true)
    public boolean isEmailEnabled(EmailNotificationAction action) {
        return settingRepository.findById(action)
                .map(EmailNotificationSetting::isEmailEnabled)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean isSmsEnabled(EmailNotificationAction action) {
        return settingRepository.findById(action)
                .map(EmailNotificationSetting::isSmsEnabled)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.EmailNotificationSettingResponse> listSettings() {
        Map<EmailNotificationAction, EmailNotificationSetting> settings = settingRepository.findAll().stream()
                .collect(Collectors.toMap(EmailNotificationSetting::getAction, Function.identity()));
        return Arrays.stream(EmailNotificationAction.values())
                .map(action -> new AdminDtos.EmailNotificationSettingResponse(
                        action.name(),
                        action.label(),
                        action.description(),
                        settings.get(action) == null || settings.get(action).isEmailEnabled(),
                        settings.get(action) != null && settings.get(action).isSmsEnabled()
                ))
                .toList();
    }

    @Transactional
    public List<AdminDtos.EmailNotificationSettingResponse> updateSettings(List<AdminDtos.EmailNotificationSettingUpdateItem> items) {
        if (items == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Notification settings are required");
        }
        for (AdminDtos.EmailNotificationSettingUpdateItem item : items) {
            EmailNotificationAction action;
            try {
                action = EmailNotificationAction.valueOf(item.action());
            } catch (IllegalArgumentException ex) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Unknown notification action: " + item.action());
            }
            EmailNotificationSetting setting = settingRepository.findById(action).orElseGet(() -> {
                EmailNotificationSetting created = new EmailNotificationSetting();
                created.setAction(action);
                return created;
            });
            setting.setEmailEnabled(item.emailEnabled());
            setting.setSmsEnabled(item.smsEnabled());
            settingRepository.save(setting);
        }
        return listSettings();
    }
}
