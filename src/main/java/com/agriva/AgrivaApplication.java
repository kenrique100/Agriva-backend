package com.agriva;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.modulith.Modulithic;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@Modulithic
public class AgrivaApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgrivaApplication.class, args);
    }
}