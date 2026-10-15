package com.example.sportbooking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.example.sportbooking.support.AbstractSecuredIntegrationTest;
import com.example.sportbooking.support.IntegrationTestFixture;

/**
 * Integracion de reserva: escenarios de la spec {@code reservation-booking}
 * (ownership desde el token, franja fuera de calendario -> 400, franja
 * ocupada -> 409, reprogramacion invalida).
 */
class ReservationBookingIntegrationTest extends AbstractSecuredIntegrationTest {

    private long createReservation(String token, long courtId, long timeBlockId, String date) throws Exception {
        return createReservation(token, Map.of("date", date, "court_id", courtId, "time_block_id", timeBlockId));
    }

    private long createReservation(String token, Map<String, Object> body) throws Exception {
        String response = mockMvc.perform(post("/reservations").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void reservationIsAttributedToAuthenticatedUser() throws Exception {
        IntegrationTestFixture.Seed seed = fixture.seed();
        String token = login(IntegrationTestFixture.CLIENT_EMAIL, IntegrationTestFixture.CLIENT_PASSWORD);

        mockMvc.perform(post("/reservations").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "date", IntegrationTestFixture.MONDAY.toString(),
                        "court_id", seed.court1().getId(),
                        // El body intenta fijar otro owner: debe ignorarse.
                        "reserved_by_user_id", seed.admin().getId(),
                        "time_block_id", seed.block1().getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservedByUser.id").value(seed.client().getId().intValue()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void reservationForThirdPartyNameKeepsAuthenticatedUserAsOwner() throws Exception {
        IntegrationTestFixture.Seed seed = fixture.seed();
        String token = login(IntegrationTestFixture.CLIENT_EMAIL, IntegrationTestFixture.CLIENT_PASSWORD);

        mockMvc.perform(post("/reservations").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "date", IntegrationTestFixture.MONDAY.toString(),
                        "court_id", seed.court1().getId(),
                        "time_block_id", seed.block1().getId(),
                        "reserved_for_name", "Invitado"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservedByUser.id").value(seed.client().getId().intValue()))
                .andExpect(jsonPath("$.reserved_for_name").value("Invitado"));
    }

    @Test
    void reservationOutsideCalendarReturns400() throws Exception {
        IntegrationTestFixture.Seed seed = fixture.seed();
        String token = login(IntegrationTestFixture.CLIENT_EMAIL, IntegrationTestFixture.CLIENT_PASSWORD);

        mockMvc.perform(post("/reservations").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "date", IntegrationTestFixture.TUESDAY.toString(),
                        "court_id", seed.court1().getId(),
                        "time_block_id", seed.block1().getId()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overlappingReservationReturns409() throws Exception {
        IntegrationTestFixture.Seed seed = fixture.seed();
        String token = login(IntegrationTestFixture.CLIENT_EMAIL, IntegrationTestFixture.CLIENT_PASSWORD);

        createReservation(token, seed.court1().getId(), seed.block1().getId(), IntegrationTestFixture.MONDAY.toString());

        mockMvc.perform(post("/reservations").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "date", IntegrationTestFixture.MONDAY.toString(),
                        "court_id", seed.court1().getId(),
                        "time_block_id", seed.block1().getId()))))
                .andExpect(status().isConflict());
    }

    @Test
    void reprogrammingToOccupiedSlotReturns409() throws Exception {
        IntegrationTestFixture.Seed seed = fixture.seed();
        String token = login(IntegrationTestFixture.CLIENT_EMAIL, IntegrationTestFixture.CLIENT_PASSWORD);

        long reservationId = createReservation(token, seed.court1().getId(), seed.block1().getId(),
                IntegrationTestFixture.MONDAY.toString());
        createReservation(token, seed.court1().getId(), seed.block2().getId(), IntegrationTestFixture.MONDAY.toString());

        mockMvc.perform(put("/reservations/{id}", reservationId).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "date", IntegrationTestFixture.MONDAY.toString(),
                        "court_id", seed.court1().getId(),
                        "time_block_id", seed.block2().getId()))))
                .andExpect(status().isConflict());
    }

}
