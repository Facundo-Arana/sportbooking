package com.example.sportbooking.reservation.controller;

import java.time.LocalDate;
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

import com.example.sportbooking.reservation.dto.ReservationDTO;
import com.example.sportbooking.reservation.dto.ReservationResponseDTO;
import com.example.sportbooking.reservation.mapper.ReservationMapper;
import com.example.sportbooking.reservation.service.ReservationService;

// Mismo criterio que los controllers anteriores: sin reglas nuevas de
// seguridad, "/reservations/**" queda protegido por
// ".anyRequest().authenticated()" en SecurityConfiguration.
//
// Sin try/catch: CourtNotFoundException/TimeBlockNotFoundException/
// UserNotFoundException/ReservationNotFoundException (-> 404),
// IllegalArgumentException (-> 400) e IllegalStateException (-> 409, turno
// ya reservado) las traduce el GlobalExceptionHandler (app-module) -- ver
// bitácora punto 20. Este controller era el que más lógica de traducción
// repetía (era el único con el caso 409), así que es donde más se nota la
// reducción.
@RestController
@RequestMapping("/reservations")
public class ReservationController {

	private final ReservationService reservationService;
	private final ReservationMapper reservationMapper;

	public ReservationController(ReservationService reservationService, ReservationMapper reservationMapper) {
		this.reservationService = reservationService;
		this.reservationMapper = reservationMapper;
	}

	@PostMapping
	public ResponseEntity<ReservationResponseDTO> create(@RequestBody ReservationDTO dto) {
		ReservationResponseDTO created = reservationMapper.toResponseDTO(reservationService.create(dto));
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	// ?courtId=X y/o ?date=YYYY-MM-DD, ambos opcionales y combinables (ver
	// ReservationService.findAll). LocalDate se bindea solo desde el query
	// param en formato ISO-8601 (Spring Boot ya registra ese formato para
	// java.time por defecto, sin necesidad de @DateTimeFormat).
	@GetMapping
	public ResponseEntity<List<ReservationResponseDTO>> findAll(@RequestParam(required = false) Long courtId,
			@RequestParam(required = false) LocalDate date) {
		return ResponseEntity
				.ok(reservationService.findAll(courtId, date).stream().map(reservationMapper::toResponseDTO)
						.toList());
	}

	@GetMapping("/{id}")
	public ResponseEntity<ReservationResponseDTO> findById(@PathVariable Long id) {
		return ResponseEntity.ok(reservationMapper.toResponseDTO(reservationService.findById(id)));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ReservationResponseDTO> update(@PathVariable Long id, @RequestBody ReservationDTO dto) {
		return ResponseEntity.ok(reservationMapper.toResponseDTO(reservationService.update(id, dto)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		reservationService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
