package com.example.sportbooking.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.sportbooking.user.entity.User;
import com.example.sportbooking.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

// Integración HTTP de punta a punta: atraviesa la cadena de filtros de Spring
// Security, el AuthController, el GlobalExceptionHandler y la persistencia,
// sobre H2 en memoria (perfil "test"). No depende de PostgreSQL local.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void limpiarEstado() {
		userRepository.deleteAll();
	}

	private String json(String username, String email, String password) {
		return "{\"username\":\"" + username + "\",\"email\":\"" + email
				+ "\",\"password\":\"" + password + "\",\"name\":\"Nombre\"}";
	}

	private String emailUnico() {
		return "u" + UUID.randomUUID() + "@example.com";
	}

	private String usernameUnico() {
		return "u" + UUID.randomUUID().toString().replace("-", "");
	}

	// ---------- Registro (US1) ----------

	@Test
	void registroValidoPersisteUsuarioConHashYNoFiltraPassword() throws Exception {
		String email = emailUnico();
		String username = usernameUnico();

		MvcResult result = mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(username, email, "S3cret!")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value(username))
				.andReturn();

		String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(body).doesNotContain("password");
		assertThat(body).doesNotContain("S3cret!");

		User persistido = userRepository.findByEmail(email).orElseThrow();
		assertThat(persistido.getCreated_at()).isNotNull();
		assertThat(persistido.getPassword_hash()).isNotEqualTo("S3cret!");
		assertThat(passwordEncoder.matches("S3cret!", persistido.getPassword_hash())).isTrue();
	}

	@Test
	void registroConEmailDuplicadoDevuelve400() throws Exception {
		String email = emailUnico();
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(usernameUnico(), email, "S3cret!")))
				.andExpect(status().isOk());

		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(usernameUnico(), email, "OtroPass1!")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Ya existe un usuario registrado con ese email"));
	}

	@Test
	void registroConUsernameDuplicadoDevuelve400() throws Exception {
		String username = usernameUnico();
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(username, emailUnico(), "S3cret!")))
				.andExpect(status().isOk());

		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(username, emailUnico(), "OtroPass1!")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Ese nombre de usuario ya está en uso"));
	}

	// ---------- Login (US2) ----------

	private String registrarYLogin(String email, String password) throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(usernameUnico(), email, password)))
				.andExpect(status().isOk());

		MvcResult result = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk())
				.andReturn();

		return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
				.get("token").asText();
	}

	@Test
	void loginConCredencialesValidasDevuelveTokenYHabilitaMe() throws Exception {
		String email = emailUnico();
		String token = registrarYLogin(email, "S3cret!");

		assertThat(token).isNotBlank();

		mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void loginConPasswordIncorrectaDevuelve401YNo5xx() throws Exception {
		String email = emailUnico();
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(usernameUnico(), email, "S3cret!")))
				.andExpect(status().isOk());

		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"incorrecta\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Credenciales inválidas"));
	}

	@Test
	void loginConUsuarioInexistenteDevuelve401YNo5xx() throws Exception {
		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + emailUnico() + "\",\"password\":\"S3cret!\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Credenciales inválidas"));
	}

	// BUG CONOCIDO (ver bitacora, punto 27 -> pendiente): /auth/me cae dentro de
	// "/auth/**" permitAll, asi que sin token la request llega igual al
	// controller con un principal anonimo (el String "anonymousUser") y el cast
	// a UserDetails de AuthController.me() revienta con ClassCastException ->
	// 500. El contrato de la feature (contracts/auth-endpoints.md) esperaba 401,
	// pero la produccion NO se toca en esta feature (solo tests); se fija el
	// comportamiento real como test de caracterizacion. Al corregir la
	// seguridad (proteger /auth/me), este test debe pasar a isUnauthorized().
	@Test
	void meSinTokenDevuelve500PorBugConocido() throws Exception {
		mockMvc.perform(get("/auth/me"))
				.andExpect(status().isInternalServerError());
	}
}
