package com.example.sportbooking.courtschedule.controller;

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

import com.example.sportbooking.court.exception.CourtNotFoundException;
import com.example.sportbooking.courtschedule.dto.CourtScheduleDTO;
import com.example.sportbooking.courtschedule.dto.CourtScheduleResponseDTO;
import com.example.sportbooking.courtschedule.exception.CourtScheduleNotFoundException;
import com.example.sportbooking.courtschedule.mapper.CourtScheduleMapper;
import com.example.sportbooking.courtschedule.service.CourtScheduleService;
import com.example.sportbooking.timeblock.exception.TimeBlockNotFoundException;

// Mismo criterio que los controllers anteriores: sin reglas nuevas de
// seguridad, "/court-schedules/**" queda protegido por
// ".anyRequest().authenticated()" en SecurityConfiguration.
@RestController
@RequestMapping("/court-schedules")
public class CourtScheduleController {

	private final CourtScheduleService courtScheduleService;
	private final CourtScheduleMapper courtScheduleMapper;

	public CourtScheduleController(CourtScheduleService courtScheduleService,
			CourtScheduleMapper courtScheduleMapper) {
		this.courtScheduleService = courtScheduleService;
		this.courtScheduleMapper = courtScheduleMapper;
	}

	@PostMapping
	public ResponseEntity<?> create(@RequestBody CourtScheduleDTO dto) {
		try {
			CourtScheduleResponseDTO created = courtScheduleMapper.toResponseDTO(courtScheduleService.create(dto));
			return ResponseEntity.status(HttpStatus.CREATED).body(created);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		} catch (CourtNotFoundException | TimeBlockNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		}
	}

	// ?courtId=X opcional, mismo criterio que CourtController/TimeBlockController.
	@GetMapping
	public ResponseEntity<List<CourtScheduleResponseDTO>> findAll(@RequestParam(required = false) Long courtId) {
		return ResponseEntity
				.ok(courtScheduleService.findAll(courtId).stream().map(courtScheduleMapper::toResponseDTO).toList());
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> findById(@PathVariable Long id) {
		try {
			return ResponseEntity.ok(courtScheduleMapper.toResponseDTO(courtScheduleService.findById(id)));
		} catch (CourtScheduleNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		}
	}

	@PutMapping("/{id}")
	public ResponseEntity<?> update(@PathVariable Long id, @RequestBody CourtScheduleDTO dto) {
		try {
			return ResponseEntity.ok(courtScheduleMapper.toResponseDTO(courtScheduleService.update(id, dto)));
		} catch (IllegalArgumentException e) {
			// Igual motivo que TimeBlockController.update: el service puede
			// tirar IllegalArgumentException en el update (day_of_week fuera
			// de rango, o la re-validación de "mismo venue" tras el merge
			// parcial).
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		} catch (CourtScheduleNotFoundException | CourtNotFoundException | TimeBlockNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable Long id) {
		try {
			courtScheduleService.delete(id);
			return ResponseEntity.noContent().build();
		} catch (CourtScheduleNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		}
	}

}
