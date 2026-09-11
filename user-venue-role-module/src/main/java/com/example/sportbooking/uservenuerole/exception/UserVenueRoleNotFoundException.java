package com.example.sportbooking.uservenuerole.exception;

public class UserVenueRoleNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public UserVenueRoleNotFoundException(Long id) {
		super("No existe una asignación de rol con id " + id);
	}

}
