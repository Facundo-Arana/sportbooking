package com.example.sportbooking.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Body del PATCH /reservations/{id}/payment (ver bitácora punto 24). Mismo
// criterio que ReservationStatusDTO: aislar el campo que solo gestiona el
// personal del venue en su propio endpoint/DTO, en vez de dejarlo viajar
// dentro del DTO genérico de update.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservationPaymentDTO {

	private String payment_status;

}
