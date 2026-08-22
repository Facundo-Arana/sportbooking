package com.example.sportbooking.court.exception;

// Mismo criterio que VenueNotFoundException (venue-module): RuntimeException
// (unchecked) para no ensuciar las firmas de repository/service/controller
// con throws. El controller la captura explícitamente y la traduce a 404.
public class CourtNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CourtNotFoundException(Long id) {
		super("No existe una cancha con id " + id);
	}

}
