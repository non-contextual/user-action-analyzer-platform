package com.useranalyzer.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class UserActionBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserActionBackendApplication.class, args);
    }
}
