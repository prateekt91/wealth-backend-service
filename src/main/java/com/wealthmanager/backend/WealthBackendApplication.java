package com.wealthmanager.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WealthBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(WealthBackendApplication.class, args);
    }
}
