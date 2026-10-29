package com.splitbuys.backend;

import java.io.File;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
public class BackendApplication {

	public static void main(String[] args) {
		// SQLite does not create missing folders; this is where the default database file lives
		new File("data").mkdirs();
		SpringApplication.run(BackendApplication.class, args);
	}

}
