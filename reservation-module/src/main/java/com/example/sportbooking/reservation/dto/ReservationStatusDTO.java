package com.example.sportbooking.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Body del PATCH /reservations/{id}/status (gestión de la reserva, ver
// bitácora punto 24). Un DTO de un solo campo: la intención del endpoint ya
// acota QUÉ se puede cambiar, así que no hace falta el resto de los campos
// de ReservationDTO ni aceptar un cambio de estado mezclado con una
// reprogramación.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservationStatusDTO {

	private String status;

}
