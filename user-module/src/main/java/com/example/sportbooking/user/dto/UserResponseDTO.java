package com.example.sportbooking.user.dto;

import java.sql.Timestamp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// El caso más importante de los seis Response DTOs de este pendiente: la
// entity User tiene password_hash (protegido hoy con @JsonIgnore, ver
// bitácora punto 13) y globalRoles (protegido con @JsonManagedReference /
// @JsonBackReference, parche del punto 14 para cortar el ciclo de
// serialización). Este DTO no tiene NINGUNO de los dos campos: no es que se
// "escondan" con anotaciones de Jackson en la entity, directamente no
// existen en la forma que viaja por la API. Con este DTO en uso, el parche
// de @JsonManagedReference/@JsonBackReference en User/UserGlobalRole queda
// redundante para las respuestas de la API (ya no se serializa el User
// completo en ningún endpoint) — se deja igual como defensa en profundidad,
// por si algún flujo futuro vuelve a devolver la entity directo por error.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {

	private Long id;

	private String username;

	private String email;

	private String name;

	private Timestamp created_at;

}
