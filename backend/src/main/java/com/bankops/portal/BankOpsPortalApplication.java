package com.bankops.portal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class BankOpsPortalApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(BankOpsPortalApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("===========================================================");
        log.info("API READY at http://localhost:8080/api");
        log.info("Health:      http://localhost:8080/api/health");
        log.info("WhoAmI:      http://localhost:8080/api/whoami");
        log.info("Swagger:     http://localhost:8080/api/swagger-ui/index.html");
        log.info("===========================================================");
    }
}
