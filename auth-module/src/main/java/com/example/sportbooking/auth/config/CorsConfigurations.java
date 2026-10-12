package com.example.sportbooking.auth.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfigurations {

	// Origen y metodos externalizados a propiedades (ver application.properties,
	// seccion "CORS"): antes estaban hardcodeados a Angular (:4200) y sin
	// PATCH, que bloqueaba el preflight de /reservations/{id}/cancel|status|payment
	// (son PATCH). Con @Value + List<String>, Spring separa por comas. El :default
	// de SpEL mantiene el valor util si la propiedad no esta definida.
	@Value("${app.cors.allowed-origins:http://localhost:5173}")
	private List<String> allowedOrigins;

	@Value("${app.cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}")
	private List<String> allowedMethods;

	// Se expone como CorsConfigurationSource (no como CorsFilter suelto, que
	// era el enfoque anterior) para que Spring Security lo enchufe directo en
	// su propia cadena de filtros vía http.cors(...) en SecurityConfiguration.
	//
	// Motivo del cambio: un @Bean CorsFilter aparte se registra con el orden
	// por defecto de Spring Boot (Ordered.LOWEST_PRECEDENCE), que corre
	// DESPUÉS del filtro de Spring Security (registrado muy temprano, con
	// SecurityProperties.DEFAULT_FILTER_ORDER). Un preflight OPTIONS a un
	// endpoint protegido (por ejemplo /reservations) llegaba primero a
	// Spring Security, que lo rechazaba con 401/403 por no venir autenticado
	// - el CorsFilter nunca llegaba a agregar los headers de CORS a esa
	// respuesta, así que el browser lo veía como un error de CORS (no como
	// un 401 legible). Iba a "romper silenciosamente" apenas el front
	// hiciera una request real con preflight a un endpoint protegido, no
	// solo por el puerto mal configurado (ver el otro cambio, más abajo).
	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(allowedOrigins);
		config.setAllowedMethods(allowedMethods);
		config.setAllowedHeaders(List.of("*"));
		config.setAllowCredentials(true); // Necesario si usás autenticación con cookies o tokens

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

}
