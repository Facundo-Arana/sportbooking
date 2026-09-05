package com.example.sportbooking.reservation.dto;

import java.sql.Timestamp;
import java.time.LocalDate;

import com.example.sportbooking.court.dto.CourtResponseDTO;
import com.example.sportbooking.timeblock.dto.TimeBlockResponseDTO;
import com.example.sportbooking.user.dto.UserResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// El DTO que motivó todo este pendiente (bitácora, punto 14): antes de
// existir este archivo, /reservations devolvía la entity Reservation
// directo, con reservedByUser/reservedForUser serializando un User
// completo (parcheado con @JsonManagedReference/@JsonBackReference para no
// entrar en loop). Con este DTO, reservedByUser/reservedForUser son
// UserResponseDTO (sin password_hash ni globalRoles): el ciclo de
// serialización deja de ser un riesgo latente porque directamente no hay
// forma de que Jackson llegue a UserGlobalRole desde acá.
//
// reservedForUser puede ser null (es opcional y mutuamente excluyente con
// reserved_for_name, ver ReservationService.validateReservedFor) — Jackson
// serializa un campo null como "reservedForUser": null sin problema, no
// hace falta ningún tratamiento especial.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponseDTO {

	private Long id;

	private LocalDate date;

	private CourtResponseDTO court;

	private TimeBlockResponseDTO timeBlock;

	private UserResponseDTO reservedByUser;

	private UserResponseDTO reservedForUser;

	private String reserved_for_name;

	private String status;

	private String payment_status;

	private Timestamp created_at;

}
