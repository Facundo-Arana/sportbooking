package com.example.sportbooking.reservation.service;

import java.time.LocalDate;
import java.util.List;

import com.example.sportbooking.timeblock.entity.TimeBlock;

// Resultado interno del calculo de disponibilidad: "calendario menos reservas".
// No es una entity (no se persiste) ni el DTO de la API. El service devuelve
// esto y el controller lo mapea a AvailabilityResponseDTO -- asi el mapeo
// entity -> DTO sigue en el borde (convencion del proyecto) y el service no
// arrastra DTOs de la capa web.
public record CourtAvailability(Long courtId, LocalDate date, Short dayOfWeek, List<Slot> slots) {

	// timeBlock se expone como entity porque el controller lo pasa por
	// TimeBlockMapper (mismo mapper que usa el resto de la API).
	public record Slot(TimeBlock timeBlock, boolean occupied) {

		public String status() {
			return occupied ? "occupied" : "free";
		}
	}

}
