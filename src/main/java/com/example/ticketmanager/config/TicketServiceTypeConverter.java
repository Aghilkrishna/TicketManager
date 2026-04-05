package com.example.ticketmanager.config;

import com.example.ticketmanager.entity.TicketServiceType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter to handle TicketServiceType enum conversion with backward compatibility
 */
@Converter(autoApply = true)
public class TicketServiceTypeConverter implements AttributeConverter<TicketServiceType, String> {

    @Override
    public String convertToDatabaseColumn(TicketServiceType attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public TicketServiceType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        
        // Handle legacy values and unknown types
        try {
            return TicketServiceType.valueOf(dbData.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // Handle legacy values or unknown types
            switch (dbData.trim().toLowerCase()) {
                case "installation":
                    return TicketServiceType.INSTALLATION;
                case "service":
                    return TicketServiceType.SERVICE;
                case "amc":
                    return TicketServiceType.AMC;
                case "site_visit":
                case "site visit":
                    return TicketServiceType.SITE_VISIT;
                case "repair":
                    return TicketServiceType.REPAIR;
                case "maintenance":
                    return TicketServiceType.MAINTENANCE;
                default:
                    return null; // or throw new IllegalArgumentException("Unknown ticket service type: " + dbData);
            }
        }
    }
}
