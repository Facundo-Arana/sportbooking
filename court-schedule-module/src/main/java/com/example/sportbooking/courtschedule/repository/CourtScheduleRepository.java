package com.example.sportbooking.courtschedule.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.sportbooking.courtschedule.entity.CourtSchedule;

public interface CourtScheduleRepository extends JpaRepository<CourtSchedule, Long> {

	// Mismo patrón que CourtRepository.findByVenue_Id / TimeBlockRepository.
	// findByVenue_Id: filtrar los horarios de UNA cancha en particular
	// (GET /court-schedules?courtId=X), pantalla "grilla semanal de esta
	// cancha" del front.
	List<CourtSchedule> findByCourt_Id(Long courtId);

	// ¿La franja (timeBlock) es reservable para esta cancha y este dia de la
	// semana? = existe un court_schedule habilitado que la cubre. Lo usa
	// reservation-module al crear/reprogramar (regla "reserva dentro del
	// calendario"). El nombre de la propiedad booleana (is_enabled) con
	// snake_case en JPQL usa el nombre del campo tal cual.
	@Query("SELECT COUNT(cs) > 0 FROM CourtSchedule cs WHERE cs.court.id = :courtId "
			+ "AND cs.timeBlock.id = :timeBlockId AND cs.day_of_week = :dayOfWeek AND cs.is_enabled = true")
	boolean existsEnabledSlot(@Param("courtId") Long courtId, @Param("timeBlockId") Long timeBlockId,
			@Param("dayOfWeek") Short dayOfWeek);

	// Todas las franjas reservables de una cancha para un dia de la semana,
	// ordenadas por hora de inicio. Es el insumo del endpoint de
	// disponibilidad (GET /courts/{id}/availability).
	@Query("SELECT cs FROM CourtSchedule cs WHERE cs.court.id = :courtId "
			+ "AND cs.day_of_week = :dayOfWeek AND cs.is_enabled = true ORDER BY cs.timeBlock.start_time")
	List<CourtSchedule> findEnabledSlots(@Param("courtId") Long courtId, @Param("dayOfWeek") Short dayOfWeek);

}
