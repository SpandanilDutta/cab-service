package com.cabservice.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // the outbox poller runs on a fixed-delay @Scheduled method
public class CabServiceWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CabServiceWorkerApplication.class, args);
    }
}
