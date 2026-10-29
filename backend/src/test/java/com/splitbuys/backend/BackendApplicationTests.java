package com.splitbuys.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:sqlite:target/test.db", "app.jwt.secret=test-secret-test-secret-test-secret-123"})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
