package com.scorpio.powerguard;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableRabbit
@EnableScheduling
@SpringBootApplication
@MapperScan("com.scorpio.powerguard.mapper")
@ConfigurationPropertiesScan
public class PowerGuardApplication {

    public static void main(String[] args) {
        initializeLogDirectoryProperty();
        SpringApplication.run(PowerGuardApplication.class, args);
    }

    private static void initializeLogDirectoryProperty() {
        if (System.getProperty("powerguard.logs.dir") != null) {
            return;
        }
        try {
            Path codeSourcePath = Paths.get(PowerGuardApplication.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
            Path baseDir = Files.isRegularFile(codeSourcePath)
                ? codeSourcePath.getParent()
                : Paths.get(System.getProperty("user.dir"));
            System.setProperty("powerguard.logs.dir", baseDir.resolve("logs").normalize().toString());
        } catch (Exception ex) {
            System.setProperty("powerguard.logs.dir",
                Paths.get(System.getProperty("user.dir"), "logs").normalize().toString());
        }
    }
}
