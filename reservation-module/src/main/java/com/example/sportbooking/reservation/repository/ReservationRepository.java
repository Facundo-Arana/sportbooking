package com.example.sportbooking.reservation.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.sportbooking.reservation.entity.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	// Filtros opcionales para GET /reservations, mismo patrón que
	// findByVenue_Id/findByCourt_Id de los módulos anteriores.
	List<Reservation> findByCourt_Id(Long courtId);

	List<Reservation> findByDate(LocalDate date);

	List<Reservation> findByCourt_IdAndDate(Long courtId, LocalDate date);

	// Chequeo de superposición (double booking) en el ALTA: ¿ya existe otra
	// reserva NO cancelada para esta cancha, esta franja horaria y esta
	// fecha? "status" se compara contra un valor fijo ("CANCELLED") en vez
	// de ignorarlo del todo, porque una reserva cancelada no debería seguir
	// bloqueando el turno.
	boolean existsByCourt_IdAndTimeBlock_IdAndDateAndStatusNot(Long courtId, Long timeBlockId, LocalDate date,
			String status);

	// Misma idea para el UPDATE, pero excluyendo la propia reserva (IdNot):
	// si no se excluye, reprogramar una reserva sin cambiar nada seguiría
	// "chocando" consigo misma.
	boolean existsByCourt_IdAndTimeBlock_IdAndDateAndStatusNotAndIdNot(Long courtId, Long timeBlockId, LocalDate date,
			String status, Long id);

}
