package com.example.sportbooking.timeblock.dto;

import java.time.LocalTime;

import com.example.sportbooking.venue.dto.VenueResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Mismo criterio que CourtResponseDTO: TimeBlockDTO (request) recibe
// venue_id, este devuelve el venue completo.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeBlockResponseDTO {

	private Long id;

	private VenueResponseDTO venue;

	private String block_group;

	private String label;

	private LocalTime start_time;

	private LocalTime end_time;

}
