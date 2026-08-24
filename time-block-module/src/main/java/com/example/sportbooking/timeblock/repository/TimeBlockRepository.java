package com.example.sportbooking.timeblock.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.sportbooking.timeblock.entity.TimeBlock;

public interface TimeBlockRepository extends JpaRepository<TimeBlock, Long> {

	// Mismo patrón que CourtRepository.findByVenue_Id: filtrar franjas
	// horarias por sede (GET /time-blocks?venueId=X). court_schedule
	// (próximo módulo) también va a necesitar consultar esto.
	List<TimeBlock> findByVenue_Id(Long venueId);

}
