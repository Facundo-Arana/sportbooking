package com.example.sportbooking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.example.sportbooking.support.AbstractSecuredIntegrationTest;
import com.example.sportbooking.support.IntegrationTestFixture;

/**
 * Integracion de identidad y directorio: escenarios de las specs
 * {@code user-identity} y {@code user-directory}, con login real y JWT.
 */
class AuthAndUserDirectoryIntegrationTest extends AbstractSecuredIntegrationTest {

    @Test
    void loginReturnsTokenAndFullIdentity() throws Exception {
        fixture.seed();

        MvcResult result = mockMvc
                .perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", IntegrationTestFixture.ADMIN_EMAIL,
                                "password", IntegrationTestFixture.ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(IntegrationTestFixture.ADMIN_EMAIL))
                .andExpect(jsonPath("$.user.id").isNumber())
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_ADMIN"))
                .andReturn();

        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText()).isNotBlank();
    }

    @Test
    void loginWithInvalidCredentialsReturns401() throws Exception {
        fixture.seed();

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", IntegrationTestFixture.ADMIN_EMAIL, "password", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsIdentityForValidToken() throws Exception {
        fixture.seed();
        String token = login(IntegrationTestFixture.CLIENT_EMAIL, IntegrationTestFixture.CLIENT_PASSWORD);

        mockMvc.perform(get("/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(IntegrationTestFixture.CLIENT_EMAIL))
                .andExpect(jsonPath("$.user.id").isNumber())
                .andExpect(jsonPath("$.authorities").isArray());
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanListUsersWithoutExposingPasswordHash() throws Exception {
        fixture.seed();
        String token = login(IntegrationTestFixture.ADMIN_EMAIL, IntegrationTestFixture.ADMIN_PASSWORD);

        mockMvc.perform(get("/users").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].password_hash").doesNotExist())
                .andExpect(content().string(containsString(IntegrationTestFixture.ADMIN_EMAIL)));
    }

    @Test
    void userListFilterMatchesUsernameOrEmail() throws Exception {
        fixture.seed();
        String token = login(IntegrationTestFixture.ADMIN_EMAIL, IntegrationTestFixture.ADMIN_PASSWORD);

        mockMvc.perform(get("/users").param("q", "client").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("client"));
    }

    @Test
    void nonAdminCannotListUsers() throws Exception {
        fixture.seed();
        String token = login(IntegrationTestFixture.CLIENT_EMAIL, IntegrationTestFixture.CLIENT_PASSWORD);

        mockMvc.perform(get("/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUserCanListRoles() throws Exception {
        fixture.seed();
        String token = login(IntegrationTestFixture.CLIENT_EMAIL, IntegrationTestFixture.CLIENT_PASSWORD);

        mockMvc.perform(get("/roles").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0:2].name").exists());
    }

    @Test
    void rolesWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/roles")).andExpect(status().isUnauthorized());
    }

}
