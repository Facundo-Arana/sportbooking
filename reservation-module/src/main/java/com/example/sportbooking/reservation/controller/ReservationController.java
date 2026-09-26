package com.example.sportbooking.reservation.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.sportbooking.reservation.dto.ReservationDTO;
import com.example.sportbooking.reservation.dto.ReservationPaymentDTO;
import com.example.sportbooking.reservation.dto.ReservationResponseDTO;
import com.example.sportbooking.reservation.dto.ReservationStatusDTO;
import com.example.sportbooking.reservation.mapper.ReservationMapper;
import com.example.sportbooking.reservation.service.ReservationService;

// Autorización por rol, con un criterio DISTINTO al resto de los
// controllers de gestión (Venue/Court/TimeBlock/CourtSchedule):
// - create()/findAll()/findById(): sin @PreAuthorize nuevo, siguen abiertos
//   a cualquier usuario autenticado. Reservar una cancha no es una acción
//   de gestión del venue, es la acción central de un cliente cualquiera --
//   exigir rol acá rompería el caso de uso principal de la app.
// - update()/delete(): "puede manejar ESTA reserva puntual" == la creó
//   (reservedByUser, para poder cancelar/modificar la propia) O tiene un
//   rol asignado en el venue de la cancha reservada (personal de la sede,
//   que gestiona la agenda completa) O es ADMIN global. Las primeras dos
//   condiciones viven juntas en VenueAuthorization.canManageReservation().
//
// Punto 24 (autorización por campo): "puede manejar la reserva" (recurso)
// no es lo mismo que "puede tocar status/payment_status" (campos). El dueño
// puede reprogramar y cancelar la propia, pero no fijar el estado ni el
// pago: eso es gestión de la sede. Se resolvió por ENDPOINTS POR INTENCIÓN,
// no validando el body: PUT ya no recibe status/payment_status (mass
// assignment imposible por construcción), y los cambios de estado van por
// PATCH .../cancel (dueño o personal), .../status y .../payment (solo
// personal). Ver canManageReservationVenue, que separa "dueño" de
// "personal del venue".
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

	@PreAuthorize("hasRole('ADMIN') or @venueAuthorization.canManageReservation(#id)")
	@PutMapping("/{id}")
	public ResponseEntity<ReservationResponseDTO> update(@PathVariable Long id, @RequestBody ReservationDTO dto) {
		return ResponseEntity.ok(reservationMapper.toResponseDTO(reservationService.update(id, dto)));
	}

	// Cancelar la propia reserva: mismo criterio que update()/delete(), el
	// dueño alcanza (canManageReservation incluye owner). No lleva body
	// porque la intención es el único dato.
	@PreAuthorize("hasRole('ADMIN') or @venueAuthorization.canManageReservation(#id)")
	@PatchMapping("/{id}/cancel")
	public ResponseEntity<ReservationResponseDTO> cancel(@PathVariable Long id) {
		return ResponseEntity.ok(reservationMapper.toResponseDTO(reservationService.cancel(id)));
	}

	// Confirmar/reponer/cancelar desde la sede: exige rol en el venue
	// (canManageReservationVenue), NO alcanza con ser el dueño.
	@PreAuthorize("hasRole('ADMIN') or @venueAuthorization.canManageReservationVenue(#id)")
	@PatchMapping("/{id}/status")
	public ResponseEntity<ReservationResponseDTO> updateStatus(@PathVariable Long id,
			@RequestBody ReservationStatusDTO dto) {
		return ResponseEntity.ok(reservationMapper.toResponseDTO(reservationService.updateStatus(id, dto.getStatus())));
	}

	// Pago: exclusivamente gestión de la sede, el dueño no puede marcarlo
	// PAID/REFUNDED (mass assignment / OWASP API3).
	@PreAuthorize("hasRole('ADMIN') or @venueAuthorization.canManageReservationVenue(#id)")
	@PatchMapping("/{id}/payment")
	public ResponseEntity<ReservationResponseDTO> updatePayment(@PathVariable Long id,
			@RequestBody ReservationPaymentDTO dto) {
		return ResponseEntity
				.ok(reservationMapper.toResponseDTO(reservationService.updatePaymentStatus(id, dto.getPayment_status())));
	}

	@PreAuthorize("hasRole('ADMIN') or @venueAuthorization.canManageReservation(#id)")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		reservationService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
