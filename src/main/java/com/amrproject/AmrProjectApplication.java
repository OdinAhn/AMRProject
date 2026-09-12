package com.amrproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AmrProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(AmrProjectApplication.class, args);
    }

}
