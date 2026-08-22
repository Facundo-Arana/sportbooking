package com.example.sportbooking.venue.controller;

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
import org.springframework.web.bind.annotation.RestController;

import com.example.sportbooking.venue.dto.VenueDTO;
import com.example.sportbooking.venue.entity.Venue;
import com.example.sportbooking.venue.exception.VenueNotFoundException;
import com.example.sportbooking.venue.service.VenueService;

// Nota: no hay ninguna regla nueva de seguridad para "/venues/**". La
// SecurityConfiguration del auth-module ya tiene ".anyRequest().authenticated()"
// como regla por defecto, así que estos endpoints van a requerir
// "Authorization: Bearer <token>" automáticamente (usá /auth/login para
// conseguir uno). Cuando llegue el momento de roles por venue
// (user_venue_role), acá es donde se va a agregar @PreAuthorize.
@RestController
@RequestMapping("/venues")
public class VenueController {

	private final VenueService venueService;

	public VenueController(VenueService venueService) {
		this.venueService = venueService;
	}

	@PostMapping
	public ResponseEntity<?> create(@RequestBody VenueDTO dto) {
		try {
			Venue created = venueService.create(dto);
			return ResponseEntity.status(HttpStatus.CREATED).body(created);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	@GetMapping
	public ResponseEntity<List<Venue>> findAll() {
		return ResponseEntity.ok(venueService.findAll());
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> findById(@PathVariable Long id) {
		try {
			return ResponseEntity.ok(venueService.findById(id));
		} catch (VenueNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		}
	}

	@PutMapping("/{id}")
	public ResponseEntity<?> update(@PathVariable Long id, @RequestBody VenueDTO dto) {
		try {
			return ResponseEntity.ok(venueService.update(id, dto));
		} catch (VenueNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable Long id) {
		try {
			venueService.delete(id);
			return ResponseEntity.noContent().build();
		} catch (VenueNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		}
	}

}
