package com.example.sportbooking.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.sportbooking.user.dto.UserDTO;
import com.example.sportbooking.user.entity.User;
import com.example.sportbooking.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private UserDTO dto() {
        return new UserDTO("juan", "juan@test.com", "secreto", "Juan", null, null);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.findByEmail("juan@test.com")).thenReturn(Optional.of(User.builder().id(1L).build()));

        assertThatThrownBy(() -> userService.registerUser(dto()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.findByEmail("juan@test.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("juan")).thenReturn(Optional.of(User.builder().id(1L).build()));

        assertThatThrownBy(() -> userService.registerUser(dto()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usuario");
    }

    @Test
    void registerHashesPasswordBeforeSaving() {
        when(userRepository.findByEmail("juan@test.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("juan")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secreto")).thenReturn("$2a$hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = userService.registerUser(dto());

        assertThat(created.getPassword_hash()).isEqualTo("$2a$hash");
        assertThat(created.getPassword_hash()).isNotEqualTo("secreto");
    }

    @Test
    void findAllWithoutFilterReturnsAll() {
        User user = User.builder().id(1L).build();
        when(userRepository.findAll()).thenReturn(List.of(user));

        assertThat(userService.findAll(null)).containsExactly(user);
        assertThat(userService.findAll("  ")).containsExactly(user);
    }

    @Test
    void findAllWithFilterUsesDerivedQuery() {
        User user = User.builder().id(1L).build();
        when(userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase("jua", "jua"))
                .thenReturn(List.of(user));

        assertThat(userService.findAll("jua")).containsExactly(user);
    }

}
