package com.example.sportbooking.court.dto;

import java.sql.Timestamp;

import com.example.sportbooking.venue.dto.VenueResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// A diferencia de CourtDTO (request), que recibe venue_id (Long) porque el
// cliente solo conoce el id de una sede existente, acá devolvemos el venue
// completo (VenueResponseDTO) porque el consumidor de la respuesta sí se
// beneficia de tener nombre/dirección/teléfono sin tener que pedirlos aparte
// con un segundo GET /venues/{id}.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourtResponseDTO {

	private Long id;

	private VenueResponseDTO venue;

	private String name;

	private String sport_type;

	private Boolean indoor;

	private String surface_type;

	private Integer max_players;

	private Timestamp created_at;

	private Boolean is_active;

}
