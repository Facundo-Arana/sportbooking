package com.example.sportbooking.venue.exception;

// RuntimeException (unchecked) a propósito: no queremos forzar un catch/throws
// en cada capa que la atraviesa (repository -> service -> controller). El
// controller la captura explícitamente para traducirla a 404, igual que
// UserService lanza RuntimeException para los duplicados y AuthController
// las traduce a una respuesta HTTP.
public class VenueNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public VenueNotFoundException(Long id) {
		super("No existe un venue con id " + id);
	}

}
