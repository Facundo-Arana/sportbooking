package com.example.sportbooking.auth.dto;

import java.util.List;

import com.example.sportbooking.user.dto.UserResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Identidad del usuario autenticado, sin token (a diferencia de AuthResponse,
// que si lo incluye porque es la respuesta del login). Es el shape de
// GET /auth/me: el front llama ese endpoint con su token y obtiene perfil +
// roles en una sola respuesta.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserIdentityResponse {

	private UserResponseDTO user;

	private List<String> authorities;

}
