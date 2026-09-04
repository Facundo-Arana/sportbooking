package com.example.sportbooking.reservation.exception;

// Mismo criterio que las NotFoundException de los módulos anteriores.
public class ReservationNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ReservationNotFoundException(Long id) {
		super("No existe una reserva con id " + id);
	}

}
