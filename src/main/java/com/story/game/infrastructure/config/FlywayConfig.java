package com.story.game.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway configuration for handling failed migrations
 */
@Slf4j
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            try {
                // Try to repair failed migrations first
                log.info("Attempting to repair Flyway schema history...");
                flyway.repair();
                log.info("Flyway repair completed successfully");
            } catch (Exception e) {
                log.warn("Flyway repair failed: {}", e.getMessage());
            }

            // Then run migrations
            flyway.migrate();
        };
    }
}
