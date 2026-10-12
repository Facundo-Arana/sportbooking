package com.example.sportbooking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

// Vive en app-module (no en auth-module) porque springdoc es una dependencia
// de app en su pom.xml. Configura el esquema de seguridad "bearer-jwt" para
// el boton "Authorize" de Swagger UI: el front de pruebas pega el token en
// "Bearer <token>" y springdoc lo agrega a cada request. El JwtFilter lo
// valida igual que un request real (no hay atajo: es el mismo camino).
@Configuration
public class OpenApiConfiguration {

	private static final String SECURITY_SCHEME_NAME = "bearer-jwt";

	@Bean
	OpenAPI sportbookingOpenAPI() {
		return new OpenAPI()
				.info(new Info().title("Sportbooking API").version("v1")
						.description("API de reservas de canchas (proyecto de portfolio)."))
				// Requerimiento global: springdoc muestra el candado en cada
				// endpoint y aplica el token salvo que se marque lo contrario.
				.addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
				.components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
						new SecurityScheme()
								.name(SECURITY_SCHEME_NAME)
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")));
	}

}
