package com.example.ticketmanager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@Slf4j
@SpringBootApplication
@EnableAsync
public class TicketManagerApplication {

    public static void main(String[] args) {
        log.info("Starting Ticket Manager Application");
        try {
            SpringApplication.run(TicketManagerApplication.class, args);
            log.info("Ticket Manager Application started successfully");
        } catch (Exception e) {
            log.error("Failed to start Ticket Manager Application", e);
            System.exit(1);
        }
    }

}
