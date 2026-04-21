package com.carlesso.pilatesapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PilatesApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PilatesApiApplication.class, args);
    }

}
