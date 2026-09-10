package com.example.sportbooking.timeblock.controller;

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

import com.example.sportbooking.timeblock.dto.TimeBlockDTO;
import com.example.sportbooking.timeblock.dto.TimeBlockResponseDTO;
import com.example.sportbooking.timeblock.mapper.TimeBlockMapper;
import com.example.sportbooking.timeblock.service.TimeBlockService;

// Mismo criterio que VenueController/CourtController: sin reglas nuevas de
// seguridad. ".anyRequest().authenticated()" en SecurityConfiguration ya
// protege "/time-blocks/**" automáticamente.
//
// Sin try/catch: TimeBlockNotFoundException/VenueNotFoundException/
// IllegalArgumentException las traduce el GlobalExceptionHandler
// (app-module) -- ver bitácora punto 20. Antes update() tenía un catch
// extra de IllegalArgumentException que CourtController no necesitaba (la
// re-validación de end_time > start_time tras el merge parcial); ahora ese
// matiz desaparece del controller porque el handler ya cubre
// IllegalArgumentException en cualquier método, sin distinción.
@RestController
@RequestMapping("/time-blocks")
public class TimeBlockController {

	private final TimeBlockService timeBlockService;
	private final TimeBlockMapper timeBlockMapper;

	public TimeBlockController(TimeBlockService timeBlockService, TimeBlockMapper timeBlockMapper) {
		this.timeBlockService = timeBlockService;
		this.timeBlockMapper = timeBlockMapper;
	}

	@PostMapping
	public ResponseEntity<TimeBlockResponseDTO> create(@RequestBody TimeBlockDTO dto) {
		TimeBlockResponseDTO created = timeBlockMapper.toResponseDTO(timeBlockService.create(dto));
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	// ?venueId=X opcional, mismo criterio que CourtController.findAll.
	@GetMapping
	public ResponseEntity<List<TimeBlockResponseDTO>> findAll(@RequestParam(required = false) Long venueId) {
		return ResponseEntity
				.ok(timeBlockService.findAll(venueId).stream().map(timeBlockMapper::toResponseDTO).toList());
	}

	@GetMapping("/{id}")
	public ResponseEntity<TimeBlockResponseDTO> findById(@PathVariable Long id) {
		return ResponseEntity.ok(timeBlockMapper.toResponseDTO(timeBlockService.findById(id)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<TimeBlockResponseDTO> update(@PathVariable Long id, @RequestBody TimeBlockDTO dto) {
		return ResponseEntity.ok(timeBlockMapper.toResponseDTO(timeBlockService.update(id, dto)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		timeBlockService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
