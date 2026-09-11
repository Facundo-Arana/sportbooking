package com.example.sportbooking.user.exception;

// Mismo criterio que UserNotFoundException/VenueNotFoundException:
// RuntimeException para no ensuciar las firmas con throws, y la agrega
// GlobalExceptionHandler (app-module) a su lista de *NotFoundException -> 404.
public class RoleNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RoleNotFoundException(Long id) {
		super("No existe un rol con id " + id);
	}

}
