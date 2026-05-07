package com.yegkim.task_reloader_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class TaskReloaderApiApplicationTests {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.flyway.placeholders.auth_admin_email", () -> "admin@task-reloader.local");
		registry.add("spring.flyway.placeholders.auth_admin_password_hash",
				() -> "$2a$12$yA0NQILk2h0m9Pk5IXf4Y.j6pESf9bnC8sY8VAsxN1uQf9P4j2Q0m");
		registry.add("auth.jwt.secret", () -> "test-secret-key-for-jwt-signing-at-least-32-bytes");
		registry.add("auth.jwt.access-token-ttl-seconds", () -> "900");
		registry.add("auth.jwt.refresh-token-ttl-seconds", () -> "1209600");
	}

	@Test
	void contextLoads() {
	}

}
