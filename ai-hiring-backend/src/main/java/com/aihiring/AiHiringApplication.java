package com.aihiring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AiHiringApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiHiringApplication.class, args);
    }
}
