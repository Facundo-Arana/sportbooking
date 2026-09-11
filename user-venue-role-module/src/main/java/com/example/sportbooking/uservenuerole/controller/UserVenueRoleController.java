package com.example.sportbooking.uservenuerole.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.sportbooking.uservenuerole.dto.UserVenueRoleDTO;
import com.example.sportbooking.uservenuerole.dto.UserVenueRoleResponseDTO;
import com.example.sportbooking.uservenuerole.mapper.UserVenueRoleMapper;
import com.example.sportbooking.uservenuerole.service.UserVenueRoleService;

// Igual que el resto: sin reglas nuevas de seguridad todavía, "/user-venue-roles/**"
// queda protegido por ".anyRequest().authenticated()" en SecurityConfiguration.
// Este módulo es justamente el que hace FALTA para poder empezar a agregar
// @PreAuthorize real en los demás controllers (Venue/Court/TimeBlock/...),
// pero ese paso de conectar user_venue_role con Spring Security todavía no
// se hizo -- queda anotado como próximo pendiente en la bitácora (punto 21).
//
// Sin PUT: ver justificación en UserVenueRoleService (no tiene update()).
// Sin try/catch: VenueNotFoundException/UserNotFoundException/
// RoleNotFoundException/UserVenueRoleNotFoundException (-> 404) e
// IllegalArgumentException (-> 400) las traduce el GlobalExceptionHandler
// (app-module).
@RestController
@RequestMapping("/user-venue-roles")
public class UserVenueRoleController {

	private final UserVenueRoleService userVenueRoleService;
	private final UserVenueRoleMapper userVenueRoleMapper;

	public UserVenueRoleController(UserVenueRoleService userVenueRoleService,
			UserVenueRoleMapper userVenueRoleMapper) {
		this.userVenueRoleService = userVenueRoleService;
		this.userVenueRoleMapper = userVenueRoleMapper;
	}

	@PostMapping
	public ResponseEntity<UserVenueRoleResponseDTO> create(@RequestBody UserVenueRoleDTO dto) {
		UserVenueRoleResponseDTO created = userVenueRoleMapper.toResponseDTO(userVenueRoleService.create(dto));
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	// ?venueId=X y/o ?userId=X, ambos opcionales y combinables (ver
	// UserVenueRoleService.findAll).
	@GetMapping
	public ResponseEntity<List<UserVenueRoleResponseDTO>> findAll(@RequestParam(required = false) Long venueId,
			@RequestParam(required = false) Long userId) {
		return ResponseEntity.ok(userVenueRoleService.findAll(venueId, userId).stream()
				.map(userVenueRoleMapper::toResponseDTO).toList());
	}

	@GetMapping("/{id}")
	public ResponseEntity<UserVenueRoleResponseDTO> findById(@PathVariable Long id) {
		return ResponseEntity.ok(userVenueRoleMapper.toResponseDTO(userVenueRoleService.findById(id)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		userVenueRoleService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
