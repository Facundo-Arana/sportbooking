package com.example.sportbooking.court.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.sportbooking.court.dto.CourtDTO;
import com.example.sportbooking.court.dto.CourtResponseDTO;
import com.example.sportbooking.court.mapper.CourtMapper;
import com.example.sportbooking.court.service.CourtService;

// Autorización por rol: create() usa el venue_id que viene en el body
// (#dto.venue_id, todavía no existe una Court contra la cual chequear);
// update()/delete() resuelven el venue A PARTIR de la Court existente
// (#id -> Court -> Venue) vía VenueAuthorization.canManageCourt(). En
// ambos casos, ADMIN global pasa igual. findAll()/findById() quedan
// abiertos a cualquier autenticado (catálogo de canchas).
//
// Sin try/catch: CourtNotFoundException/VenueNotFoundException/
// IllegalArgumentException las traduce el GlobalExceptionHandler
// (app-module) -- ver bitácora punto 20.
@RestController
@RequestMapping("/courts")
public class CourtController {

	private final CourtService courtService;
	private final CourtMapper courtMapper;

	public CourtController(CourtService courtService, CourtMapper courtMapper) {
		this.courtService = courtService;
		this.courtMapper = courtMapper;
	}

	@PreAuthorize("hasRole('ADMIN') or @venueAuthorization.canManageVenue(#dto.venue_id)")
	@PostMapping
	public ResponseEntity<CourtResponseDTO> create(@RequestBody CourtDTO dto) {
		CourtResponseDTO created = courtMapper.toResponseDTO(courtService.create(dto));
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	// ?venueId=X es opcional: sin el param devuelve todas las canchas, con el
	// param filtra por sede (pantalla "canchas de esta sede" del front).
	@GetMapping
	public ResponseEntity<List<CourtResponseDTO>> findAll(@RequestParam(required = false) Long venueId) {
		return ResponseEntity.ok(courtService.findAll(venueId).stream().map(courtMapper::toResponseDTO).toList());
	}

	@GetMapping("/{id}")
	public ResponseEntity<CourtResponseDTO> findById(@PathVariable Long id) {
		return ResponseEntity.ok(courtMapper.toResponseDTO(courtService.findById(id)));
	}

	@PreAuthorize("hasRole('ADMIN') or @venueAuthorization.canManageCourt(#id)")
	@PutMapping("/{id}")
	public ResponseEntity<CourtResponseDTO> update(@PathVariable Long id, @RequestBody CourtDTO dto) {
		return ResponseEntity.ok(courtMapper.toResponseDTO(courtService.update(id, dto)));
	}

	@PreAuthorize("hasRole('ADMIN') or @venueAuthorization.canManageCourt(#id)")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		courtService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
