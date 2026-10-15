package com.example.sportbooking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.example.sportbooking.support.AbstractSecuredIntegrationTest;
import com.example.sportbooking.support.IntegrationTestFixture;

/**
 * Integracion de disponibilidad: escenarios de la spec
 * {@code court-availability} (free/occupied, CANCELLED libera la franja,
 * cancha inexistente -> 404, orden por start_time).
 */
class CourtAvailabilityIntegrationTest extends AbstractSecuredIntegrationTest {

    private long createReservation(String token, long courtId, long timeBlockId, String date) throws Exception {
        String body = mockMvc.perform(post("/reservations").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("date", date, "court_id", courtId, "time_block_id", timeBlockId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void slotsAreOrderedByStartTimeAndInitiallyFree() throws Exception {
        IntegrationTestFixture.Seed seed = fixture.seed();
        String token = login(IntegrationTestFixture.CLIENT_EMAIL, IntegrationTestFixture.CLIENT_PASSWORD);

        mockMvc.perform(get("/courts/{id}/availability", seed.court1().getId())
                .param("date", IntegrationTestFixture.MONDAY.toString())
                .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(2))
                .andExpect(jsonPath("$.slots[0].timeBlock.start_time").value("08:00:00"))
                .andExpect(jsonPath("$.slots[1].timeBlock.start_time").value("09:00:00"))
                .andExpect(jsonPath("$.slots[0].status").value("free"))
                .andExpect(jsonPath("$.slots[1].status").value("free"));
    }

    @Test
    void activeReservationOccupiesSlotAndCancellationFreesIt() throws Exception {
        IntegrationTestFixture.Seed seed = fixture.seed();
        String token = login(IntegrationTestFixture.CLIENT_EMAIL, IntegrationTestFixture.CLIENT_PASSWORD);

        long reservationId = createReservation(token, seed.court1().getId(), seed.block1().getId(),
                IntegrationTestFixture.MONDAY.toString());

        mockMvc.perform(get("/courts/{id}/availability", seed.court1().getId())
                .param("date", IntegrationTestFixture.MONDAY.toString())
                .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].status").value("occupied"))
                .andExpect(jsonPath("$.slots[1].status").value("free"));

        mockMvc.perform(patch("/reservations/{id}/cancel", reservationId).header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/courts/{id}/availability", seed.court1().getId())
                .param("date", IntegrationTestFixture.MONDAY.toString())
                .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].status").value("free"));
    }

    @Test
    void unknownCourtReturns404() throws Exception {
        fixture.seed();
        String token = login(IntegrationTestFixture.CLIENT_EMAIL, IntegrationTestFixture.CLIENT_PASSWORD);

        mockMvc.perform(get("/courts/999999/availability").param("date", IntegrationTestFixture.MONDAY.toString())
                .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

}
