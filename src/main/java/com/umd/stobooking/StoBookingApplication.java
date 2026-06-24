package com.umd.stobooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StoBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoBookingApplication.class, args);
    }
}
