package com.example.sportbooking.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.example.sportbooking.auth.security.util.JwtFilter;
import com.example.sportbooking.reservation.controller.AvailabilityController;
import com.example.sportbooking.reservation.service.CourtAvailability;
import com.example.sportbooking.reservation.service.ReservationService;
import com.example.sportbooking.security.WithCustomUserDetails;
import com.example.sportbooking.timeblock.mapper.TimeBlockMapper;

/**
 * Slice del {@link AvailabilityController}: endpoint de lectura que solo exige
 * autenticacion. Matriz 401 / 200.
 */
@WebMvcTest(controllers = AvailabilityController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import(SliceTestSecurityConfig.class)
class AvailabilityControllerSliceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservationService reservationService;

    @MockBean
    private TimeBlockMapper timeBlockMapper;

    @Test
    void availabilityWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/courts/1/availability").param("date", MONDAY.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithCustomUserDetails(roles = {})
    void availabilityAsAuthenticatedReturns200() throws Exception {
        when(reservationService.findAvailability(eq(1L), eq(MONDAY)))
                .thenReturn(new CourtAvailability(1L, MONDAY, (short) 1, List.of()));

        mockMvc.perform(get("/courts/1/availability").param("date", MONDAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courtId").value(1))
                .andExpect(jsonPath("$.day_of_week").value(1))
                .andExpect(jsonPath("$.slots").isArray());
    }

}
