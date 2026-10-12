package com.example.sportbooking.reservation.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Respuesta de GET /courts/{id}/availability?date=YYYY-MM-DD. slots viene
// ordenado por start_time (lo garantiza la query de court-schedule), asi el
// front renderiza el calendario sin reordenar.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityResponseDTO {

	private Long courtId;

	private LocalDate date;

	private Short day_of_week;

	private List<AvailabilitySlotDTO> slots;

}
