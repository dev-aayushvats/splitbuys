package com.splitbuys.backend.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Starts one real PostgreSQL server for the whole test run (no Docker needed) and points the {@code postgres} profile
 * at it using the same DATABASE_* settings an operator would set for Neon.
 */
public class EmbeddedPostgresInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final EmbeddedPostgres POSTGRES = start();

    private static EmbeddedPostgres start() {
        try {
            EmbeddedPostgres pg = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    pg.close();
                } catch (IOException ignored) {
                    // shutting down anyway
                }
            }));
            return pg;
        } catch (IOException e) {
            throw new IllegalStateException("Could not start embedded PostgreSQL", e);
        }
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        TestPropertyValues.of(
                "DATABASE_URL=jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/postgres",
                "DATABASE_USERNAME=postgres",
                "DATABASE_PASSWORD=postgres").applyTo(context);
    }
}
