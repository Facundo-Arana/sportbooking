package com.example.sportbooking.venue.dto;

import java.sql.Timestamp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO de RESPUESTA, no de request. VenueDTO (mismo paquete) sigue siendo el
// que recibe el body de POST/PUT (name/address/phone, sin id ni created_at,
// porque esos los genera el servidor). Este es nuevo: lo que la API
// devuelve en GET/POST/PUT, con id incluido y desacoplado de la entity JPA
// (ver bitácora, punto 15.2 — antes los controllers devolvían Venue
// directo).
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueResponseDTO {

	private Long id;

	private String name;

	private String address;

	private String phone;

	private Timestamp created_at;

}
