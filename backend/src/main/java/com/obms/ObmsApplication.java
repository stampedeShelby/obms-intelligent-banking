package com.obms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the OBMS backend.
 *
 * <p>JPA auditing is enabled here so that created/updated timestamps on every
 * entity are populated by the persistence layer rather than by service code.
 * Async and scheduling support back the notification dispatcher and the
 * monthly financial-score job.</p>
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
public class ObmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ObmsApplication.class, args);
    }
}
