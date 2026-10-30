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
        "APP_ENV_FILE=target/no-such-local.env", // tests never read the developer's real .env (it may point at a real database)
        "app.jwt.secret=integration-test-secret-integration-test-secret",
        "app.rate-limit.auth-per-minute=100000"})
@ActiveProfiles("postgres")
@ContextConfiguration(initializers = EmbeddedPostgresInitializer.class)
public @interface UsesPostgres {
}
