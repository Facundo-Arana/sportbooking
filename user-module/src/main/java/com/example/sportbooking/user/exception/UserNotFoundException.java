package com.example.sportbooking.user.exception;

// Primer módulo que necesita esta excepción: hasta ahora user-module solo
// validaba duplicados en el alta (email/username ya en uso), nunca había
// que resolver "¿existe el usuario con este id?" porque nada lo referenciaba
// por FK. reservation-module es el primer caso (fk_reserved_by_user_id /
// fk_reserved_for_user_id) y la reutiliza dos veces en el mismo service,
// mismo criterio que CourtNotFoundException/TimeBlockNotFoundException en
// court-schedule-module.
public class UserNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public UserNotFoundException(Long id) {
		super("No existe un usuario con id " + id);
	}

}
