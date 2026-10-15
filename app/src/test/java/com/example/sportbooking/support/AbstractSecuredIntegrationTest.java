package com.example.sportbooking.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.example.sportbooking.AbstractIntegrationTest;

/**
 * Base para los tests de integracion que necesitan autenticarse. Hace login
 * REAL contra {@code /auth/login} (ejercita el {@code JwtFilter} y el
 * {@code CustomUserDetails} de verdad, D5) y devuelve el JWT.
 */
public abstract class AbstractSecuredIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String login(String email, String password) throws Exception {
        String responseBody = mockMvc
                .perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(responseBody).get("token").asText();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

}
