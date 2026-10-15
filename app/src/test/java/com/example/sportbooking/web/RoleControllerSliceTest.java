package com.example.sportbooking.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.example.sportbooking.security.WithCustomUserDetails;
import com.example.sportbooking.user.controller.RoleController;
import com.example.sportbooking.user.dto.RoleResponseDTO;
import com.example.sportbooking.user.entity.Role;
import com.example.sportbooking.user.mapper.RoleMapper;
import com.example.sportbooking.user.service.RoleService;

/**
 * Slice del {@link RoleController}: catalogo de roles, solo exige
 * autenticacion. Matriz 401 / 200.
 */
@WebMvcTest(controllers = RoleController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import(SliceTestSecurityConfig.class)
class RoleControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoleService roleService;

    @MockBean
    private RoleMapper roleMapper;

    @Test
    void listWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/roles")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithCustomUserDetails(roles = {})
    void listAsAuthenticatedReturns200() throws Exception {
        when(roleService.findAll()).thenReturn(List.of(Role.builder().id(1L).name("ADMIN").build()));
        when(roleMapper.toResponseDTO(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RoleResponseDTO(1L, "ADMIN"));

        mockMvc.perform(get("/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("ADMIN"));
    }

}
