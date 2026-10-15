package com.example.sportbooking.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.sportbooking.auth.security.util.JwtFilter;
import com.example.sportbooking.reservation.controller.ReservationController;
import com.example.sportbooking.reservation.dto.ReservationResponseDTO;
import com.example.sportbooking.reservation.entity.Reservation;
import com.example.sportbooking.reservation.mapper.ReservationMapper;
import com.example.sportbooking.reservation.service.ReservationService;
import com.example.sportbooking.security.VenueAuthorization;
import com.example.sportbooking.security.WithCustomUserDetails;

/**
 * Slice del {@link ReservationController}: el alta exige solo autenticacion;
 * las acciones sobre una reserva puntual pasan por el bean
 * {@code @venueAuthorization} (mockeado aca). Matriz 401 / 403 / 200.
 */
@WebMvcTest(controllers = ReservationController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import(SliceTestSecurityConfig.class)
class ReservationControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservationService reservationService;

    @MockBean
    private ReservationMapper reservationMapper;

    @MockBean(name = "venueAuthorization")
    private VenueAuthorization venueAuthorization;

    @Test
    void createWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithCustomUserDetails(roles = {})
    void createAsAuthenticatedReturns201() throws Exception {
        when(reservationService.create(any())).thenReturn(Reservation.builder().id(1L).status("PENDING").build());
        when(reservationMapper.toResponseDTO(any()))
                .thenReturn(new ReservationResponseDTO(1L, null, null, null, null, null, null, "PENDING", "PENDING", null));

        mockMvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    void cancelWithoutTokenReturns401() throws Exception {
        mockMvc.perform(patch("/reservations/5/cancel")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithCustomUserDetails(roles = {})
    void cancelWithoutManagePermissionReturns403() throws Exception {
        when(venueAuthorization.canManageReservation(5L)).thenReturn(false);

        mockMvc.perform(patch("/reservations/5/cancel")).andExpect(status().isForbidden());
    }

    @Test
    @WithCustomUserDetails(roles = {})
    void cancelWithManagePermissionReturns200() throws Exception {
        when(venueAuthorization.canManageReservation(5L)).thenReturn(true);
        when(reservationService.cancel(eq(5L))).thenReturn(Reservation.builder().id(5L).status("CANCELLED").build());
        when(reservationMapper.toResponseDTO(any()))
                .thenReturn(new ReservationResponseDTO(5L, null, null, null, null, null, null, "CANCELLED", "PENDING", null));

        mockMvc.perform(patch("/reservations/5/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

}
