package com.example.sportbooking.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.sportbooking.user.dto.UserDTO;
import com.example.sportbooking.user.entity.User;
import com.example.sportbooking.user.repository.UserRepository;

// Unitarias puras (sin contexto Spring): repositorio y encoder simulados.
// Aislan la regla de negocio de UserService: alta valida, duplicados y hashing.
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private UserService userService;

	private UserDTO dto(String username, String email, String password) {
		UserDTO dto = new UserDTO();
		dto.setUsername(username);
		dto.setEmail(email);
		dto.setPassword(password);
		dto.setName("Nombre");
		return dto;
	}

	@Test
	void altaValidaPersisteUsuarioConPasswordHasheadoYCreatedAt() {
		UserDTO input = dto("juan", "juan@example.com", "S3cret!");
		when(userRepository.findByEmail("juan@example.com")).thenReturn(Optional.empty());
		when(userRepository.findByUsername("juan")).thenReturn(Optional.empty());
		when(passwordEncoder.encode("S3cret!")).thenReturn("$2a$10$hash-generado");
		when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

		User result = userService.registerUser(input);

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		User saved = captor.getValue();

		assertThat(result).isSameAs(saved);
		assertThat(saved.getUsername()).isEqualTo("juan");
		assertThat(saved.getEmail()).isEqualTo("juan@example.com");
		assertThat(saved.getCreated_at()).isNotNull();
		// FR-005: no se guarda el texto plano, se guarda la salida del encoder.
		assertThat(saved.getPassword_hash()).isEqualTo("$2a$10$hash-generado");
		assertThat(saved.getPassword_hash()).isNotEqualTo("S3cret!");
	}

	@Test
	void emailDuplicadoLanzaIllegalArgumentYSaveNuncaSeInvoca() {
		UserDTO input = dto("juan", "juan@example.com", "S3cret!");
		when(userRepository.findByEmail("juan@example.com"))
				.thenReturn(Optional.of(new User("otro", "juan@example.com", "hash", null)));

		assertThatThrownBy(() -> userService.registerUser(input))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Ya existe un usuario registrado con ese email");

		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	void usernameDuplicadoLanzaIllegalArgumentYSaveNuncaSeInvoca() {
		UserDTO input = dto("juan", "juan@example.com", "S3cret!");
		when(userRepository.findByEmail("juan@example.com")).thenReturn(Optional.empty());
		when(userRepository.findByUsername("juan"))
				.thenReturn(Optional.of(new User("juan", "otro@example.com", "hash", null)));

		assertThatThrownBy(() -> userService.registerUser(input))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Ese nombre de usuario ya está en uso");

		verify(userRepository, never()).save(any(User.class));
	}
}
