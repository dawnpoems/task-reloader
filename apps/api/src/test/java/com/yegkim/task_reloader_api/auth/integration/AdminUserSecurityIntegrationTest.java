package com.yegkim.task_reloader_api.auth.integration;

import com.yegkim.task_reloader_api.auth.entity.User;
import com.yegkim.task_reloader_api.auth.entity.UserRole;
import com.yegkim.task_reloader_api.auth.entity.UserStatus;
import com.yegkim.task_reloader_api.auth.jwt.JwtTokenProvider;
import com.yegkim.task_reloader_api.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("관리자 API JWT 보안 통합테스트")
class AdminUserSecurityIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@task-reloader.local";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.placeholders.auth_admin_email", () -> ADMIN_EMAIL);
        registry.add("spring.flyway.placeholders.auth_admin_password_hash",
                () -> "$2a$12$yA0NQILk2h0m9Pk5IXf4Y.j6pESf9bnC8sY8VAsxN1uQf9P4j2Q0m");
        registry.add("auth.jwt.secret", () -> "test-secret-key-for-jwt-signing-at-least-32-bytes");
        registry.add("auth.jwt.access-token-ttl-seconds", () -> "900");
        registry.add("auth.jwt.refresh-token-ttl-seconds", () -> "1209600");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("ADMIN 토큰으로 pending 사용자 목록 조회 성공")
    void pendingUsers_withAdminToken_success() throws Exception {
        User admin = userRepository.findByEmail(ADMIN_EMAIL)
                .orElseThrow(() -> new IllegalStateException("admin user seed is missing"));

        String email = "pending-integration-1@example.com";
        userRepository.save(User.builder()
                .email(email)
                .passwordHash("hash")
                .role(UserRole.USER)
                .status(UserStatus.PENDING)
                .build());

        String adminAccessToken = jwtTokenProvider.generateAccessToken(admin.getId(), admin.getRole());

        mockMvc.perform(get("/api/admin/users/pending")
                        .header(AUTHORIZATION, bearer(adminAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data[*].email", hasItem(email)));
    }

    @Test
    @DisplayName("USER 토큰으로 pending 사용자 목록 조회 시 403")
    void pendingUsers_withUserToken_forbidden() throws Exception {
        User user = userRepository.save(User.builder()
                .email("approved-user-integration-1@example.com")
                .passwordHash("hash")
                .role(UserRole.USER)
                .status(UserStatus.APPROVED)
                .build());

        String userAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole());

        mockMvc.perform(get("/api/admin/users/pending")
                        .header(AUTHORIZATION, bearer(userAccessToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
