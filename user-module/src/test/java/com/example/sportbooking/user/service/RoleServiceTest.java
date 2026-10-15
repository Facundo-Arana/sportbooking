package com.example.sportbooking.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.sportbooking.user.entity.Role;
import com.example.sportbooking.user.repository.RoleRepository;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private RoleService roleService;

    @Test
    void findAllDelegatesToRepository() {
        Role admin = Role.builder().id(1L).name("ADMIN").build();
        when(roleRepository.findAll()).thenReturn(List.of(admin));

        assertThat(roleService.findAll()).containsExactly(admin);
        verify(roleRepository).findAll();
    }

}
