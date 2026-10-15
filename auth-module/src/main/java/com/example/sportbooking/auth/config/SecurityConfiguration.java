package com.example.sportbooking.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.example.sportbooking.auth.security.util.JwtFilter;

@Configuration
@EnableWebSecurity
// prePostEnabled=true por defecto en Spring Security 6: habilita que
// @PreAuthorize se evalúe en cualquier método de un bean de Spring (los
// @RestController de cada módulo, en este caso), ANTES de que el método se
// ejecute. Sin esta anotación, @PreAuthorize en los controllers es una
// anotación muerta que Spring ignora silenciosamente -- no da error, pero
// tampoco protege nada, así que es fácil no notar el olvido en desarrollo.
@EnableMethodSecurity
public class SecurityConfiguration {

	@SuppressWarnings("unused")
	private final JwtFilter jwtFilter;

	public SecurityConfiguration(JwtFilter jwtFilter) {
		this.jwtFilter = jwtFilter;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
	
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtFilter jwtFilter,
			CorsConfigurationSource corsConfigurationSource) throws Exception {
		http
			// Engancha el CorsConfigurationSource de CorsConfigurations directo en
			// la cadena de filtros de Spring Security. Reemplaza al CorsFilter
			// suelto que había antes (ver el comentario en CorsConfigurations):
			// así el filtro de CORS corre en el punto correcto, antes de la
			// autorización, y no después.
			.cors(cors -> cors.configurationSource(corsConfigurationSource))
			.csrf(csrf -> csrf.disable())
			.sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// Sin este entry point explicito, un request sin token a un endpoint
			// protegido devuelve 403 (el default de Spring Security es
			// Http403ForbiddenEntryPoint cuando no hay httpBasic/formLogin). Las
			// specs user-identity/user-directory documentan 401 para "sin
			// autenticacion" (mismo codigo que el JwtFilter usa para token
			// invalido), asi que se fija 401 de forma consistente.
			.exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.authorizeHttpRequests(auth -> auth
				// El preflight OPTIONS nunca trae Authorization header, así que
				// tiene que estar permitido explícitamente para CUALQUIER path,
				// incluidos los protegidos - si no, un preflight a /reservations
				// se rechaza con 401 antes de llegar al controller real.
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				// Solo login/register son publicos. "/auth/**" a secas dejaba
				// /auth/me fuera del filtro de autorizacion: sin token llegaba
				// al controller, que hacia getAuthentication().getPrincipal()
				// sobre un contexto vacio -> NPE -> 500, cuando la spec
				// user-identity pide 401 para "sin autenticacion".
				.requestMatchers("/auth/login", "/auth/register").permitAll()
				// Swagger UI / OpenAPI: publicos para poder explorar el contrato
				// sin token (es un banco de pruebas de dev/portfolio). Los
				// endpoints del API siguen protegidos: esto solo abre la UI y el
				// JSON de la spec.
				.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
				.anyRequest().authenticated()
			)
			.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);  

		return http.build();
	}
	
	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
	    return config.getAuthenticationManager();
	}

}


