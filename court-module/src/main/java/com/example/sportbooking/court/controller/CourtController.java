package com.example.sportbooking.court.controller;

import java.util.List;
import java.util.Map;

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
import com.example.sportbooking.court.entity.Court;
import com.example.sportbooking.court.exception.CourtNotFoundException;
import com.example.sportbooking.court.service.CourtService;
import com.example.sportbooking.venue.exception.VenueNotFoundException;

// Mismo criterio que VenueController: sin reglas nuevas de seguridad. La
// SecurityConfiguration del auth-module ya tiene ".anyRequest().authenticated()"
// como regla por defecto, así que "/courts/**" queda protegido automáticamente.
// Cuando exista user_venue_role, acá es donde se va a agregar @PreAuthorize
// para validar que el usuario tenga rol sobre el venue de la cancha.
@RestController
@RequestMapping("/courts")
public class CourtController {

	private final CourtService courtService;

	public CourtController(CourtService courtService) {
		this.courtService = courtService;
	}

	@PostMapping
	public ResponseEntity<?> create(@RequestBody CourtDTO dto) {
		try {
			Court created = courtService.create(dto);
			return ResponseEntity.status(HttpStatus.CREATED).body(created);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		} catch (VenueNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		}
	}

	// ?venueId=X es opcional: sin el param devuelve todas las canchas, con el
	// param filtra por sede (pantalla "canchas de esta sede" del front).
	@GetMapping
	public ResponseEntity<List<Court>> findAll(@RequestParam(required = false) Long venueId) {
		return ResponseEntity.ok(courtService.findAll(venueId));
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> findById(@PathVariable Long id) {
		try {
			return ResponseEntity.ok(courtService.findById(id));
		} catch (CourtNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		}
	}

	@PutMapping("/{id}")
	public ResponseEntity<?> update(@PathVariable Long id, @RequestBody CourtDTO dto) {
		try {
			return ResponseEntity.ok(courtService.update(id, dto));
		} catch (CourtNotFoundException | VenueNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable Long id) {
		try {
			courtService.delete(id);
			return ResponseEntity.noContent().build();
		} catch (CourtNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		}
	}

}
