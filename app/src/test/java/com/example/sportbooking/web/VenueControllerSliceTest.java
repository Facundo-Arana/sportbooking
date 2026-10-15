package com.example.sportbooking.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.example.sportbooking.security.WithCustomUserDetails;
import com.example.sportbooking.venue.controller.VenueController;
import com.example.sportbooking.venue.dto.VenueResponseDTO;
import com.example.sportbooking.venue.entity.Venue;
import com.example.sportbooking.venue.mapper.VenueMapper;
import com.example.sportbooking.venue.service.VenueService;

/**
 * Slice del {@link VenueController}: contrato HTTP + seguridad. Matriz de
 * seguridad con POST (solo ADMIN) y lectura autenticada. El service se mockea;
 * el contexto de seguridad se arma con {@link WithCustomUserDetails}.
 */
@WebMvcTest(controllers = VenueController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import(SliceTestSecurityConfig.class)
class VenueControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VenueService venueService;

    @MockBean
    private VenueMapper venueMapper;

    @Test
    void createWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/venues").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Club\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithCustomUserDetails(roles = {})
    void createWithoutAdminRoleReturns403() throws Exception {
        mockMvc.perform(post("/venues").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Club\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithCustomUserDetails(roles = { "ADMIN" })
    void createAsAdminReturns201() throws Exception {
        when(venueService.create(any())).thenReturn(Venue.builder().id(1L).name("Club").build());
        when(venueMapper.toResponseDTO(any())).thenReturn(new VenueResponseDTO(1L, "Club", null, null, null));

        mockMvc.perform(post("/venues").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Club\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithCustomUserDetails(roles = {})
    void findByIdAsAuthenticatedReturns200() throws Exception {
        when(venueService.findById(eq(1L))).thenReturn(Venue.builder().id(1L).name("Club").build());
        when(venueMapper.toResponseDTO(any())).thenReturn(new VenueResponseDTO(1L, "Club", null, null, null));

        mockMvc.perform(get("/venues/1")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Club"));
    }

}
