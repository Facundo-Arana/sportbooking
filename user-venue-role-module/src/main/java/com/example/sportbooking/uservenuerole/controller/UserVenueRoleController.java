package com.example.sportbooking.uservenuerole.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

// Autorización por rol -- último eslabón del punto 21: quién puede asignar
// o revocar un rol EN UN VENUE es la misma pregunta que "quién puede
// manejar ese venue" (create() usa #dto.venue_id; delete() resuelve el
// venue de la fila existente vía canManageUserVenueRole(#id)). Es
// deliberadamente circular en el buen sentido: un OWNER de un venue puede
// asignar roles a otros usuarios en su propio venue, no solo un ADMIN
// global -- así un dueño de sede puede sumar personal sin depender del
// administrador de la plataforma para cada alta.
// findAll()/findById() quedan sin cambios (abiertos a cualquier
// autenticado), mismo criterio que el resto de los endpoints de lectura --
// si más adelante hace falta ocultar quién tiene qué rol, es un cambio
// puntual acá.
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

	@PreAuthorize("hasRole('ADMIN') or @venueAuthorization.canManageVenue(#dto.venue_id)")
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

	@PreAuthorize("hasRole('ADMIN') or @venueAuthorization.canManageUserVenueRole(#id)")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		userVenueRoleService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
