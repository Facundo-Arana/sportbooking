package com.example.sportbooking.court.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourtDTO {

	// Referencia al venue por id, no por objeto completo: el cliente manda
	// solo el id de una sede existente, no un Venue entero.
	private Long venue_id;

	private String name;

	private String sport_type;

	// Boolean (wrapper), no boolean (primitivo): en el update parcial
	// necesitamos distinguir "no vino en el body" (null, no tocar el campo)
	// de "vino en false" (indoor = false explícito). Mismo motivo para
	// is_active más abajo.
	private Boolean indoor;

	private String surface_type;

	private Integer max_players;

	private Boolean is_active;

}
