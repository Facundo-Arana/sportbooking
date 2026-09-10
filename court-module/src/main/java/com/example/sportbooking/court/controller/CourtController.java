package com.example.sportbooking.court.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.sportbooking.court.dto.CourtDTO;
import com.example.sportbooking.court.dto.CourtResponseDTO;
import com.example.sportbooking.court.mapper.CourtMapper;
import com.example.sportbooking.court.service.CourtService;

// Mismo criterio que VenueController: sin reglas nuevas de seguridad. La
// SecurityConfiguration del auth-module ya tiene ".anyRequest().authenticated()"
// como regla por defecto, así que "/courts/**" queda protegido automáticamente.
// Cuando exista user_venue_role, acá es donde se va a agregar @PreAuthorize
// para validar que el usuario tenga rol sobre el venue de la cancha.
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

	@PutMapping("/{id}")
	public ResponseEntity<CourtResponseDTO> update(@PathVariable Long id, @RequestBody CourtDTO dto) {
		return ResponseEntity.ok(courtMapper.toResponseDTO(courtService.update(id, dto)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		courtService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
