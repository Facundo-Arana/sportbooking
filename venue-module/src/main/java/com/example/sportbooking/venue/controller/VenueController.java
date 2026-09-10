package com.example.sportbooking.venue.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.sportbooking.venue.dto.VenueDTO;
import com.example.sportbooking.venue.dto.VenueResponseDTO;
import com.example.sportbooking.venue.mapper.VenueMapper;
import com.example.sportbooking.venue.service.VenueService;

// Nota: no hay ninguna regla nueva de seguridad para "/venues/**". La
// SecurityConfiguration del auth-module ya tiene ".anyRequest().authenticated()"
// como regla por defecto, así que estos endpoints van a requerir
// "Authorization: Bearer <token>" automáticamente (usá /auth/login para
// conseguir uno). Cuando llegue el momento de roles por venue
// (user_venue_role), acá es donde se va a agregar @PreAuthorize.
//
// Ya no hay try/catch acá: VenueNotFoundException/IllegalArgumentException
// se propagan solas y las traduce a HTTP el GlobalExceptionHandler
// (app-module). Como bonus, el tipo de retorno deja de ser ResponseEntity<?>
// -- ahora el compilador sabe exactamente qué devuelve cada método en el
// camino feliz (ver bitácora punto 20).
@RestController
@RequestMapping("/venues")
public class VenueController {

	private final VenueService venueService;
	private final VenueMapper venueMapper;

	public VenueController(VenueService venueService, VenueMapper venueMapper) {
		this.venueService = venueService;
		this.venueMapper = venueMapper;
	}

	@PostMapping
	public ResponseEntity<VenueResponseDTO> create(@RequestBody VenueDTO dto) {
		VenueResponseDTO created = venueMapper.toResponseDTO(venueService.create(dto));
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@GetMapping
	public ResponseEntity<List<VenueResponseDTO>> findAll() {
		// El mapeo entity -> DTO pasa acá, en el controller (el "borde" de la
		// API), no en el service: VenueService.findAll() sigue devolviendo
		// List<Venue> porque la capa de servicio trabaja con el modelo de
		// dominio (entities), no con el contrato de la API. Si mañana
		// aparece otro consumidor del service (un job batch, un test) que
		// necesita el Venue completo, no tiene que lidiar con un DTO
		// recortado para la API REST.
		return ResponseEntity.ok(venueService.findAll().stream().map(venueMapper::toResponseDTO).toList());
	}

	@GetMapping("/{id}")
	public ResponseEntity<VenueResponseDTO> findById(@PathVariable Long id) {
		return ResponseEntity.ok(venueMapper.toResponseDTO(venueService.findById(id)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<VenueResponseDTO> update(@PathVariable Long id, @RequestBody VenueDTO dto) {
		return ResponseEntity.ok(venueMapper.toResponseDTO(venueService.update(id, dto)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		venueService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
