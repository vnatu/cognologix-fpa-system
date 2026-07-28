package com.cognologix.fpa.security;

import com.cognologix.fpa.general.AppUser;
import com.cognologix.fpa.general.UserRole;
import com.cognologix.fpa.general.UserService;
import com.cognologix.fpa.general.repository.AppUserRepository;
import com.cognologix.fpa.general.repository.LoginAttemptRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthAndUserManagementIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;
    @Autowired AppUserRepository appUserRepository;
    @Autowired LoginAttemptRepository loginAttemptRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void cleanAttempts() {
        loginAttemptRepository.deleteAll();
    }

    @Test
    void login_withValidCredentials_returnsJwtWithRoleClaim() throws Exception {
        ensureUser("login-admin@cognologix.com", "Login Admin", UserRole.ADMIN, "Secret123!", false);

        var body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"login-admin@cognologix.com","password":"Secret123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = objectMapper.readTree(body).get("token").asText();
        assertThat(jwtTokenProvider.extractRole(token)).isEqualTo("ADMIN");
        assertThat(jwtPayload(token).get("role").asText()).isEqualTo("ADMIN");
    }

    @Test
    void login_failure_incrementsAttemptCount() throws Exception {
        ensureUser("fail@cognologix.com", "Fail User", UserRole.VIEWER, "Secret123!", false);
        long before = userService.countRecentLoginAttempts("fail@cognologix.com");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"fail@cognologix.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized());

        assertThat(userService.countRecentLoginAttempts("fail@cognologix.com")).isEqualTo(before + 1);
    }

    @Test
    void login_fiveFailedAttempts_returns429() throws Exception {
        ensureUser("locked@cognologix.com", "Locked User", UserRole.VIEWER, "Secret123!", false);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"locked@cognologix.com","password":"wrong"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"locked@cognologix.com","password":"Secret123!"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error")
                        .value("Too many login attempts. Please try again in 15 minutes."));
    }

    @Test
    void adminEndpoint_returns403_forViewerJwt() throws Exception {
        ensureUser("viewer@cognologix.com", "Viewer User", UserRole.VIEWER, "Secret123!", false);
        String token = loginToken("viewer@cognologix.com", "Secret123!");

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUser_setsMustChangePassword() throws Exception {
        ensureUser("creator@cognologix.com", "Creator", UserRole.ADMIN, "Secret123!", false);
        String token = loginToken("creator@cognologix.com", "Secret123!");

        var body = mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"invited@cognologix.com",
                                  "fullName":"Invited User",
                                  "role":"VIEWER",
                                  "initialPassword":"TempPass99!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.role").value("VIEWER"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        AppUser stored = appUserRepository.findById(id).orElseThrow();
        assertThat(stored.isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches("TempPass99!", stored.getPasswordHash())).isTrue();
    }

    @Test
    void changePassword_updatesHash_andClearsMustChangeFlag() throws Exception {
        ensureUser("changer@cognologix.com", "Changer", UserRole.ADMIN, "OldPass123!", true);
        String token = loginToken("changer@cognologix.com", "OldPass123!");

        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"OldPass123!","newPassword":"NewPass456!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        AppUser stored = appUserRepository.findByEmailIgnoreCase("changer@cognologix.com").orElseThrow();
        assertThat(passwordEncoder.matches("NewPass456!", stored.getPasswordHash())).isTrue();
        assertThat(stored.isMustChangePassword()).isFalse();
    }

    private void ensureUser(String email, String name, UserRole role, String password, boolean mustChange) {
        appUserRepository.findByEmailIgnoreCase(email).ifPresentOrElse(existing -> {
            existing.setFullName(name);
            existing.setRole(role);
            existing.setPasswordHash(passwordEncoder.encode(password));
            existing.setMustChangePassword(mustChange);
            existing.setActive(true);
            appUserRepository.save(existing);
        }, () -> {
            var user = new AppUser();
            user.setEmail(email);
            user.setFullName(name);
            user.setRole(role);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setMustChangePassword(mustChange);
            user.setActive(true);
            appUserRepository.save(user);
        });
    }

    private String loginToken(String email, String password) throws Exception {
        var body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private static JsonNode jwtPayload(String token) throws Exception {
        String payload = token.split("\\.")[1];
        byte[] decoded = Base64.getUrlDecoder().decode(payload);
        return new ObjectMapper().readTree(new String(decoded, StandardCharsets.UTF_8));
    }
}
