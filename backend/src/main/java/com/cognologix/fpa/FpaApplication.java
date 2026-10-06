package com.cognologix.fpa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FpaApplication {
    public static void main(String[] args) {
        SpringApplication.run(FpaApplication.class, args);
    }
}
