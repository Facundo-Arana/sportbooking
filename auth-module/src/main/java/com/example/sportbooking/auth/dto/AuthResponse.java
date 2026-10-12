package com.example.sportbooking.auth.dto;

import java.util.List;

import com.example.sportbooking.user.dto.UserResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Respuesta del login: antes devolvia SOLO el token, asi que el front tenia
// que hacer un segundo request para saber quien era (y no tenia el id). Ahora
// compone la identidad completa (UserResponseDTO, ya sin password_hash) mas
// los authorities.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

	private String token;

	private UserResponseDTO user;

	// Roles globales como texto (ej. "ROLE_ADMIN"), mismo formato que los
	// GrantedAuthority que carga el JwtFilter.
	private List<String> authorities;

}
