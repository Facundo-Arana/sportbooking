package com.example.sportbooking.web;

import static org.mockito.ArgumentMatchers.any;
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
import com.example.sportbooking.user.controller.UserController;
import com.example.sportbooking.user.dto.UserResponseDTO;
import com.example.sportbooking.user.entity.User;
import com.example.sportbooking.user.mapper.UserMapper;
import com.example.sportbooking.user.service.UserService;

/**
 * Slice del {@link UserController}: directorio de usuarios restringido a
 * ADMIN global. Matriz 401 (sin token) / 403 (autenticado no-admin) / 200.
 */
@WebMvcTest(controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import(SliceTestSecurityConfig.class)
class UserControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private UserMapper userMapper;

    @Test
    void listWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/users")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithCustomUserDetails(roles = {})
    void listAsNonAdminReturns403() throws Exception {
        mockMvc.perform(get("/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithCustomUserDetails(roles = { "ADMIN" })
    void listAsAdminReturns200WithPublicShape() throws Exception {
        when(userService.findAll(any())).thenReturn(List.of(User.builder().id(1L).username("juan").build()));
        when(userMapper.toResponseDTO(any()))
                .thenReturn(new UserResponseDTO(1L, "juan", "juan@test.com", "Juan", null, null));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("juan"))
                .andExpect(jsonPath("$[0].password_hash").doesNotExist());
    }

}
