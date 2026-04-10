package com.windwatch.scada;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ScadaApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScadaApplication.class, args);
    }
}
