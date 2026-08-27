package com.example.sportbooking.courtschedule.exception;

// Mismo criterio que CourtNotFoundException / TimeBlockNotFoundException:
// RuntimeException custom (unchecked), el controller la captura explícitamente
// y la traduce a 404.
public class CourtScheduleNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CourtScheduleNotFoundException(Long id) {
		super("No existe un horario de cancha con id " + id);
	}

}
