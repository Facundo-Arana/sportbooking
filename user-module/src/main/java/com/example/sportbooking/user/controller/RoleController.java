package com.example.sportbooking.user.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.sportbooking.user.dto.RoleResponseDTO;
import com.example.sportbooking.user.mapper.RoleMapper;
import com.example.sportbooking.user.service.RoleService;

// Catalogo de roles para que el front ofrezca las opciones al asignar una
// membresia de sede. Autenticado (sin @PreAuthorize: alcanza con tener token
// valido, la regla por defecto de SecurityConfiguration).
@RestController
@RequestMapping("/roles")
public class RoleController {

	private final RoleService roleService;
	private final RoleMapper roleMapper;

	public RoleController(RoleService roleService, RoleMapper roleMapper) {
		this.roleService = roleService;
		this.roleMapper = roleMapper;
	}

	@GetMapping
	public ResponseEntity<List<RoleResponseDTO>> findAll() {
		return ResponseEntity.ok(roleService.findAll().stream().map(roleMapper::toResponseDTO).toList());
	}

}
