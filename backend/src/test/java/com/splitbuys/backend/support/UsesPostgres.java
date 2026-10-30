package com.splitbuys.backend.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** Runs an inherited API test class against a real PostgreSQL (migrated by Flyway, schema validated by Hibernate). */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "app.jwt.secret=integration-test-secret-integration-test-secret",
        "app.rate-limit.auth-per-minute=100000"})
@ActiveProfiles("postgres")
@ContextConfiguration(initializers = EmbeddedPostgresInitializer.class)
public @interface UsesPostgres {
}
