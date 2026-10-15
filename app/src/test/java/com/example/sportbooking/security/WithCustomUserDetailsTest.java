package com.example.sportbooking.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import com.example.sportbooking.court.repository.CourtRepository;
import com.example.sportbooking.courtschedule.repository.CourtScheduleRepository;
import com.example.sportbooking.reservation.repository.ReservationRepository;
import com.example.sportbooking.timeblock.repository.TimeBlockRepository;
import com.example.sportbooking.uservenuerole.entity.UserVenueRole;
import com.example.sportbooking.uservenuerole.repository.UserVenueRoleRepository;
import com.example.sportbooking.venue.controller.VenueController;
import com.example.sportbooking.venue.dto.VenueResponseDTO;
import com.example.sportbooking.venue.entity.Venue;
import com.example.sportbooking.venue.mapper.VenueMapper;
import com.example.sportbooking.venue.service.VenueService;
import com.example.sportbooking.web.SliceTestSecurityConfig;

/**
 * Verificacion de la tarea 4.1: la factory {@link WithCustomUserDetails}
 * produce un principal que las {@code @PreAuthorize} de produccion reconocen.
 *
 * <p>Usa el {@link VenueAuthorization} REAL (importado, con sus repositorios
 * mockeados), no un mock del bean: si el principal no fuera
 * {@code CustomUserDetails}, {@code currentUser()} devolveria {@code null} y
 * todos los casos darian 403. Por eso el test de "staff del venue" pasando
 * con 200 demuestra que la factory funciona de punta a punta.
 */
@WebMvcTest(controllers = VenueController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import({ SliceTestSecurityConfig.class, VenueAuthorization.class })
class WithCustomUserDetailsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VenueService venueService;

    @MockBean
    private VenueMapper venueMapper;

    @MockBean
    private UserVenueRoleRepository userVenueRoleRepository;

    @MockBean
    private CourtRepository courtRepository;

    @MockBean
    private TimeBlockRepository timeBlockRepository;

    @MockBean
    private CourtScheduleRepository courtScheduleRepository;

    @MockBean
    private ReservationRepository reservationRepository;

    private void stubSuccessfulUpdate() {
        when(venueService.update(eq(1L), any())).thenReturn(Venue.builder().id(1L).name("Nuevo").build());
        when(venueMapper.toResponseDTO(any())).thenReturn(new VenueResponseDTO(1L, "Nuevo", null, null, null));
    }

    @Test
    void withoutTokenReturns401() throws Exception {
        mockMvc.perform(put("/venues/1").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nuevo\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithCustomUserDetails(id = 1L, email = "staff@test.com", roles = {})
    void venueStaffSeesAuthoritiesAndCanManageOwnVenue() throws Exception {
        when(userVenueRoleRepository.findByVenue_IdAndUser_Id(1L, 1L))
                .thenReturn(List.of(UserVenueRole.builder().id(9L).build()));
        stubSuccessfulUpdate();

        mockMvc.perform(put("/venues/1").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nuevo\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithCustomUserDetails(id = 2L, email = "outsider@test.com", roles = {})
    void authenticatedUserWithoutVenueRoleIsForbidden() throws Exception {
        when(userVenueRoleRepository.findByVenue_IdAndUser_Id(1L, 2L)).thenReturn(List.of());

        mockMvc.perform(put("/venues/1").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nuevo\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithCustomUserDetails(id = 3L, email = "admin@test.com", roles = { "ADMIN" })
    void adminAuthorityBypassesVenueCheck() throws Exception {
        stubSuccessfulUpdate();

        mockMvc.perform(put("/venues/1").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nuevo\"}"))
                .andExpect(status().isOk());
    }

}
