package com.example.sportbooking.timeblock.controller;

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

import com.example.sportbooking.timeblock.dto.TimeBlockDTO;
import com.example.sportbooking.timeblock.entity.TimeBlock;
import com.example.sportbooking.timeblock.exception.TimeBlockNotFoundException;
import com.example.sportbooking.timeblock.service.TimeBlockService;
import com.example.sportbooking.venue.exception.VenueNotFoundException;

// Mismo criterio que VenueController/CourtController: sin reglas nuevas de
// seguridad. ".anyRequest().authenticated()" en SecurityConfiguration ya
// protege "/time-blocks/**" automáticamente.
@RestController
@RequestMapping("/time-blocks")
public class TimeBlockController {

	private final TimeBlockService timeBlockService;

	public TimeBlockController(TimeBlockService timeBlockService) {
		this.timeBlockService = timeBlockService;
	}

	@PostMapping
	public ResponseEntity<?> create(@RequestBody TimeBlockDTO dto) {
		try {
			TimeBlock created = timeBlockService.create(dto);
			return ResponseEntity.status(HttpStatus.CREATED).body(created);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		} catch (VenueNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		}
	}

	// ?venueId=X opcional, mismo criterio que CourtController.findAll.
	@GetMapping
	public ResponseEntity<List<TimeBlock>> findAll(@RequestParam(required = false) Long venueId) {
		return ResponseEntity.ok(timeBlockService.findAll(venueId));
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> findById(@PathVariable Long id) {
		try {
			return ResponseEntity.ok(timeBlockService.findById(id));
		} catch (TimeBlockNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		}
	}

	@PutMapping("/{id}")
	public ResponseEntity<?> update(@PathVariable Long id, @RequestBody TimeBlockDTO dto) {
		try {
			return ResponseEntity.ok(timeBlockService.update(id, dto));
		} catch (IllegalArgumentException e) {
			// A diferencia de CourtController.update: acá el service SÍ puede
			// tirar IllegalArgumentException en el update (la validación
			// end_time > start_time se re-chequea después del merge parcial),
			// así que hace falta este catch que court-module no necesitaba.
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		} catch (TimeBlockNotFoundException | VenueNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable Long id) {
		try {
			timeBlockService.delete(id);
			return ResponseEntity.noContent().build();
		} catch (TimeBlockNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		}
	}

}
