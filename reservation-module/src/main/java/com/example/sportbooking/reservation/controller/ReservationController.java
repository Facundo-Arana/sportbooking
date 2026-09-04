package com.example.sportbooking.reservation.controller;

import java.time.LocalDate;
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
import com.example.sportbooking.reservation.dto.ReservationDTO;
import com.example.sportbooking.reservation.entity.Reservation;
import com.example.sportbooking.reservation.exception.ReservationNotFoundException;
import com.example.sportbooking.reservation.service.ReservationService;
import com.example.sportbooking.timeblock.exception.TimeBlockNotFoundException;
import com.example.sportbooking.user.exception.UserNotFoundException;

// Mismo criterio que los controllers anteriores: sin reglas nuevas de
// seguridad, "/reservations/**" queda protegido por
// ".anyRequest().authenticated()" en SecurityConfiguration.
@RestController
@RequestMapping("/reservations")
public class ReservationController {

	private final ReservationService reservationService;

	public ReservationController(ReservationService reservationService) {
		this.reservationService = reservationService;
	}

	@PostMapping
	public ResponseEntity<?> create(@RequestBody ReservationDTO dto) {
		try {
			Reservation created = reservationService.create(dto);
			return ResponseEntity.status(HttpStatus.CREATED).body(created);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		} catch (CourtNotFoundException | TimeBlockNotFoundException | UserNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		} catch (IllegalStateException e) {
			// Primer uso de 409 CONFLICT en el proyecto: a diferencia de 400
			// (dato de entrada inválido) y 404 (recurso inexistente), acá el
			// request en sí es válido pero choca contra el estado actual del
			// sistema (ya hay otra reserva activa para ese turno). Es la
			// misma distinción conceptual que 400 vs 401 en el punto 3.1 de
			// la bitácora, aplicada a un código nuevo.
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
		}
	}

	// ?courtId=X y/o ?date=YYYY-MM-DD, ambos opcionales y combinables (ver
	// ReservationService.findAll). LocalDate se bindea solo desde el query
	// param en formato ISO-8601 (Spring Boot ya registra ese formato para
	// java.time por defecto, sin necesidad de @DateTimeFormat).
	@GetMapping
	public ResponseEntity<List<Reservation>> findAll(@RequestParam(required = false) Long courtId,
			@RequestParam(required = false) LocalDate date) {
		return ResponseEntity.ok(reservationService.findAll(courtId, date));
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> findById(@PathVariable Long id) {
		try {
			return ResponseEntity.ok(reservationService.findById(id));
		} catch (ReservationNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		}
	}

	@PutMapping("/{id}")
	public ResponseEntity<?> update(@PathVariable Long id, @RequestBody ReservationDTO dto) {
		try {
			return ResponseEntity.ok(reservationService.update(id, dto));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		} catch (ReservationNotFoundException | CourtNotFoundException | TimeBlockNotFoundException
				| UserNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable Long id) {
		try {
			reservationService.delete(id);
			return ResponseEntity.noContent().build();
		} catch (ReservationNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
		}
	}

}
