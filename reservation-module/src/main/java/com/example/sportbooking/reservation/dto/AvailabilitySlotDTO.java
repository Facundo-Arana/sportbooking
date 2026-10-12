package com.example.sportbooking.reservation.dto;

import com.example.sportbooking.timeblock.dto.TimeBlockResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Una franja del dia con su estado: "free" u "occupied".
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilitySlotDTO {

	private TimeBlockResponseDTO timeBlock;

	private String status;

}
