package com.example.sportbooking.timeblock.exception;

// Mismo criterio que CourtNotFoundException / VenueNotFoundException:
// RuntimeException custom (unchecked), el controller la captura explícitamente
// y la traduce a 404.
public class TimeBlockNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public TimeBlockNotFoundException(Long id) {
		super("No existe una franja horaria con id " + id);
	}

}
