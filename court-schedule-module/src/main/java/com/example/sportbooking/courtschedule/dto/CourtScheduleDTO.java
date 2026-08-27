package com.example.sportbooking.courtschedule.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourtScheduleDTO {

	// Referencias por id, mismo criterio que CourtDTO.venue_id /
	// TimeBlockDTO.venue_id: el cliente manda ids de recursos existentes,
	// no objetos completos.
	private Long court_id;

	private Long time_block_id;

	// 0 = domingo ... 6 = sábado (convención EXTRACT(DOW) de Postgres).
	private Short day_of_week;

	private Boolean is_enabled;

	private String notes;

}
