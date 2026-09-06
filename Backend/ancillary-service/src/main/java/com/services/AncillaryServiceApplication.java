package com.services;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class AncillaryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AncillaryServiceApplication.class, args);
    }
}
