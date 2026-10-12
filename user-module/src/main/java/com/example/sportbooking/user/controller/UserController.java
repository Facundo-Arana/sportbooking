package com.example.sportbooking.user.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.sportbooking.user.dto.UserResponseDTO;
import com.example.sportbooking.user.mapper.UserMapper;
import com.example.sportbooking.user.service.UserService;

// Primer UserController del proyecto: hasta ahora User se exponia solo via
// auth-module (login/me). Este endpoint es de descubrimiento para el panel
// admin: para asignar una membresia de sede (user_venue_role) el front tiene
// que poder listar y buscar usuarios. Devuelve UserResponseDTO (sin
// password_hash) y esta restringido a ADMIN global.
//
// Rompe a proposito la convencion vieja "no existe UserController" -- se
// actualizan AGENTS.md y la bitacora (punto 31).
@RestController
@RequestMapping("/users")
public class UserController {

	private final UserService userService;
	private final UserMapper userMapper;

	public UserController(UserService userService, UserMapper userMapper) {
		this.userService = userService;
		this.userMapper = userMapper;
	}

	// ?q= opcional: filtra por username o email (total o parcial). Sin "q"
	// devuelve todos. hasRole('ADMIN') -> 403 si el usuario autenticado no
	// tiene el rol global.
	@PreAuthorize("hasRole('ADMIN')")
	@GetMapping
	public ResponseEntity<List<UserResponseDTO>> findAll(@RequestParam(required = false) String q) {
		return ResponseEntity.ok(userService.findAll(q).stream().map(userMapper::toResponseDTO).toList());
	}

}
