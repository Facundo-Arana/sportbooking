package com.example.sportbooking.reservation.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.sportbooking.reservation.dto.AvailabilityResponseDTO;
import com.example.sportbooking.reservation.dto.AvailabilitySlotDTO;
import com.example.sportbooking.reservation.service.CourtAvailability;
import com.example.sportbooking.reservation.service.ReservationService;
import com.example.sportbooking.timeblock.mapper.TimeBlockMapper;

// Endpoint de solo lectura: disponibilidad de una cancha en una fecha. Mapea
// el resultado del service (CourtAvailability) a DTOs en el borde, igual que
// el resto de los controllers. Sin @PreAuthorize: cualquier autenticado
// (la regla por defecto de SecurityConfiguration ya exige token).
//
// Comparte el prefijo /courts con CourtController (otro modulo): son paths
// distintos (/courts/{id}/availability vs /courts/{id}), no chocan.
@RestController
@RequestMapping("/courts")
public class AvailabilityController {

	private final ReservationService reservationService;
	private final TimeBlockMapper timeBlockMapper;

	public AvailabilityController(ReservationService reservationService, TimeBlockMapper timeBlockMapper) {
		this.reservationService = reservationService;
		this.timeBlockMapper = timeBlockMapper;
	}

	// ?date=YYYY-MM-DD: LocalDate se bindea en ISO-8601 sin @DateTimeFormat
	// (mismo patron que ReservationController.findAll).
	@GetMapping("/{id}/availability")
	public ResponseEntity<AvailabilityResponseDTO> getAvailability(@PathVariable Long id,
			@RequestParam LocalDate date) {
		CourtAvailability availability = reservationService.findAvailability(id, date);

		List<AvailabilitySlotDTO> slots = availability.slots().stream()
				.map(slot -> new AvailabilitySlotDTO(timeBlockMapper.toResponseDTO(slot.timeBlock()), slot.status()))
				.toList();

		return ResponseEntity.ok(new AvailabilityResponseDTO(availability.courtId(), availability.date(),
				availability.dayOfWeek(), slots));
	}

}
