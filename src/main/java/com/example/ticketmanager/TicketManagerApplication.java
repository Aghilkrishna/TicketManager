package com.example.ticketmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TicketManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketManagerApplication.class, args);
    }

}
