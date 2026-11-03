package com.dqh.nccapa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NcCapaApplication {
    public static void main(String[] args) {
        SpringApplication.run(NcCapaApplication.class, args);
    }
}
