package com.example.sportbooking.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.sportbooking.auth.security.util.CustomUserDetailsService;
import com.example.sportbooking.auth.security.util.JwtUtil;
import com.example.sportbooking.user.entity.User;
import com.example.sportbooking.user.service.UserService;

// Unitarias del controller con MockMvc en modo "standalone" (sin contexto
// Spring ni cadena de seguridad): mockea todas las dependencias para verificar
// solo el mapeo de status/respuesta del AuthController.
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

	@Mock
	private AuthenticationManager authenticationManager;

	@Mock
	private JwtUtil jwtUtil;

	@Mock
	private CustomUserDetailsService userDetailsService;

	@Mock
	private UserService userService;

	@InjectMocks
	private AuthController authController;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
	}

	// ---------- Registro (US1) ----------

	@Test
	void registroSinEmailDevuelve400() throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"juan\",\"password\":\"S3cret!\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("email y password son obligatorios"));
	}

	@Test
	void registroSinPasswordDevuelve400() throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"juan\",\"email\":\"juan@example.com\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("email y password son obligatorios"));
	}

	@Test
	void registroConCamposEnBlancoDevuelve400() throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"juan\",\"email\":\"   \",\"password\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("email y password son obligatorios"));
	}

	@Test
	void registroValidoDevuelve200ConUsernameYNuncaExponePassword() throws Exception {
		when(userService.registerUser(any())).thenReturn(
				new User("juan", "juan@example.com", "$2a$10$hash", "Juan"));

		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"juan\",\"email\":\"juan@example.com\",\"password\":\"S3cret!\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Usuario registrado con éxito"))
				.andExpect(jsonPath("$.username").value("juan"))
				// FR-011 / invariante: nunca viaja el hash ni el texto plano.
				.andExpect(content().string(not(containsString("password"))))
				.andExpect(content().string(not(containsString("S3cret!"))))
				.andExpect(content().string(not(containsString("$2a$10$hash"))));
	}

	// ---------- Login (US2) ----------

	@Test
	void loginConCredencialesValidasDevuelve200ConToken() throws Exception {
		UserDetails userDetails = new org.springframework.security.core.userdetails.User(
				"juan@example.com", "$2a$10$hash", java.util.List.of());
		when(authenticationManager.authenticate(any()))
				.thenReturn(new UsernamePasswordAuthenticationToken("juan@example.com", null));
		when(userDetailsService.loadUserByUsername("juan@example.com")).thenReturn(userDetails);
		when(jwtUtil.generateToken("juan@example.com")).thenReturn("token-de-prueba");

		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"juan@example.com\",\"password\":\"S3cret!\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value("token-de-prueba"));
	}

	@Test
	void loginConPasswordIncorrectaDevuelve401() throws Exception {
		when(authenticationManager.authenticate(any()))
				.thenThrow(new BadCredentialsException("credenciales inválidas"));

		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"juan@example.com\",\"password\":\"incorrecta\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Credenciales inválidas"));
	}

	@Test
	void loginConUsuarioInexistenteDevuelve401YNo5xx() throws Exception {
		when(authenticationManager.authenticate(any()))
				.thenThrow(new BadCredentialsException("usuario inexistente"));

		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"nadie@example.com\",\"password\":\"S3cret!\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Credenciales inválidas"));
	}
}
