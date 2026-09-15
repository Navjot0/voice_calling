package com.freeswitch.calling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CallingApplication {

    public static void main(String[] args) {
        SpringApplication.run(CallingApplication.class, args);
    }
}
