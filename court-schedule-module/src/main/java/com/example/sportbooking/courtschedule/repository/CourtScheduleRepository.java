package com.example.sportbooking.courtschedule.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.sportbooking.courtschedule.entity.CourtSchedule;

public interface CourtScheduleRepository extends JpaRepository<CourtSchedule, Long> {

	// Mismo patrón que CourtRepository.findByVenue_Id / TimeBlockRepository.
	// findByVenue_Id: filtrar los horarios de UNA cancha en particular
	// (GET /court-schedules?courtId=X), pantalla "grilla semanal de esta
	// cancha" del front.
	List<CourtSchedule> findByCourt_Id(Long courtId);

}
