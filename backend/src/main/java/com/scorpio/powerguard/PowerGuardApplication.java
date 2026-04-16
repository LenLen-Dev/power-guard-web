package com.scorpio.powerguard;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@MapperScan("com.scorpio.powerguard.mapper")
@ConfigurationPropertiesScan
public class PowerGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(PowerGuardApplication.class, args);
    }
}
