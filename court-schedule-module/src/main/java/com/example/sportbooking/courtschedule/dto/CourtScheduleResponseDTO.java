package com.example.sportbooking.courtschedule.dto;

import com.example.sportbooking.court.dto.CourtResponseDTO;
import com.example.sportbooking.timeblock.dto.TimeBlockResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Primer Response DTO con dos objetos anidados a la vez (court + timeBlock),
// cada uno con su propio venue anidado adentro (CourtResponseDTO.venue /
// TimeBlockResponseDTO.venue). Es intencional: para armar la grilla de
// "franjas disponibles de una cancha" el front necesita nombre de cancha,
// sede y horario en una sola respuesta, sin encadenar tres GETs.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourtScheduleResponseDTO {

	private Long id;

	private CourtResponseDTO court;

	private TimeBlockResponseDTO timeBlock;

	private Short day_of_week;

	private Boolean is_enabled;

	private String notes;

}
