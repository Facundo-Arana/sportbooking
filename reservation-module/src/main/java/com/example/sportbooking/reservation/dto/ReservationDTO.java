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

	// Solo se usan en el update (cancelar, confirmar pago, etc.) - en el
	// alta el service los fuerza a "PENDING" y no lee estos campos del DTO.
	private String status;

	private String payment_status;

}
