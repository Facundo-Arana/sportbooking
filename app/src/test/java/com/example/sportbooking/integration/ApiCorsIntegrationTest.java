package com.example.sportbooking.integration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.example.sportbooking.support.AbstractSecuredIntegrationTest;

/**
 * Integracion de CORS: escenarios de la spec {@code api-cors}. El preflight
 * {@code OPTIONS} viaja SIN Authorization, asi que tiene que resolverse antes
 * de la autorizacion y autorizar {@code PATCH}.
 */
class ApiCorsIntegrationTest extends AbstractSecuredIntegrationTest {

    @Test
    void preflightForPatchCancelIsAuthorized() throws Exception {
        mockMvc.perform(options("/reservations/1/cancel")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PATCH")));
    }

    @Test
    void preflightFromUnknownOriginIsNotAuthorized() throws Exception {
        mockMvc.perform(options("/reservations/1/cancel")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

}
