package com.example.sportbooking.reservation.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservationDTO {

	private LocalDate date;

	// Referencias por id, mismo criterio que CourtScheduleDTO.court_id/
	// time_block_id.
	private Long court_id;

	private Long time_block_id;

	private Long reserved_by_user_id;

	// Ambos opcionales y mutuamente excluyentes (ver
	// ReservationService.validateReservedFor).
	private Long reserved_for_user_id;

	private String reserved_for_name;

	// status y payment_status NO van acá a propósito (bitácora, punto 24):
	// este DTO es el body del alta y de la reprogramación (PUT), acciones
	// que no implican cambiar el estado de la reserva. Los cambios de estado
	// tienen sus propios DTOs y endpoints (PATCH /{id}/status,
	// /{id}/payment, /{id}/cancel) para que un cliente dueño de la reserva
	// no pueda hacer mass assignment sobre campos que solo gestiona el
	// personal del venue (OWASP API3).

}
