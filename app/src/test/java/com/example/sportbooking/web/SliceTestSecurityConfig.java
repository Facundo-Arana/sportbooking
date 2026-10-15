package com.example.sportbooking.web;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Configuracion de seguridad para los tests de slice ({@code @WebMvcTest}).
 *
 * <p>Replica el contrato relevante de la {@code SecurityConfiguration} real
 * (stateless, preflight publico, todo lo demas autenticado, 401 si falta el
 * token, {@code @EnableMethodSecurity} para que {@code @PreAuthorize} se
 * evalue) pero SIN el {@code JwtFilter}: en un slice no hay request real con
 * JWT, el contexto se inyecta con {@code @WithCustomUserDetails}. Sin
 * {@code @EnableMethodSecurity} los {@code @PreAuthorize} serian anotaciones
 * muertas y los 403 no se testearian.
 *
 * <p>El entry point 401 explicito espeja el de produccion
 * ({@code SecurityConfiguration}): sin el, Spring Security responde 403.
 */
@TestConfiguration
@EnableMethodSecurity
public class SliceTestSecurityConfig {

    @Bean
    SecurityFilterChain sliceSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/auth/login", "/auth/register").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

}
