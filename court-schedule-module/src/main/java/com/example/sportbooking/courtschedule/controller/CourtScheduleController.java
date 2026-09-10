package com.example.sportbooking.courtschedule.controller;

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

import com.example.sportbooking.courtschedule.dto.CourtScheduleDTO;
import com.example.sportbooking.courtschedule.dto.CourtScheduleResponseDTO;
import com.example.sportbooking.courtschedule.mapper.CourtScheduleMapper;
import com.example.sportbooking.courtschedule.service.CourtScheduleService;

// Mismo criterio que los controllers anteriores: sin reglas nuevas de
// seguridad, "/court-schedules/**" queda protegido por
// ".anyRequest().authenticated()" en SecurityConfiguration.
//
// Sin try/catch: CourtScheduleNotFoundException/CourtNotFoundException/
// TimeBlockNotFoundException/IllegalArgumentException las traduce el
// GlobalExceptionHandler (app-module) -- ver bitácora punto 20.
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
	public ResponseEntity<CourtScheduleResponseDTO> create(@RequestBody CourtScheduleDTO dto) {
		CourtScheduleResponseDTO created = courtScheduleMapper.toResponseDTO(courtScheduleService.create(dto));
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	// ?courtId=X opcional, mismo criterio que CourtController/TimeBlockController.
	@GetMapping
	public ResponseEntity<List<CourtScheduleResponseDTO>> findAll(@RequestParam(required = false) Long courtId) {
		return ResponseEntity
				.ok(courtScheduleService.findAll(courtId).stream().map(courtScheduleMapper::toResponseDTO).toList());
	}

	@GetMapping("/{id}")
	public ResponseEntity<CourtScheduleResponseDTO> findById(@PathVariable Long id) {
		return ResponseEntity.ok(courtScheduleMapper.toResponseDTO(courtScheduleService.findById(id)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<CourtScheduleResponseDTO> update(@PathVariable Long id, @RequestBody CourtScheduleDTO dto) {
		return ResponseEntity.ok(courtScheduleMapper.toResponseDTO(courtScheduleService.update(id, dto)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		courtScheduleService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
