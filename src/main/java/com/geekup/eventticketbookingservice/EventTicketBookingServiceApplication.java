package com.geekup.eventticketbookingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EventTicketBookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventTicketBookingServiceApplication.class, args);
    }

}
