package com.example.sportbooking.timeblock.dto;

import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeBlockDTO {

	// Referencia al venue por id, mismo criterio que CourtDTO.venue_id.
	private Long venue_id;

	private String block_group;

	private String label;

	// El cliente manda "HH:mm" o "HH:mm:ss" (ISO-8601), Jackson lo parsea
	// solo a LocalTime.
	private LocalTime start_time;

	private LocalTime end_time;

}
