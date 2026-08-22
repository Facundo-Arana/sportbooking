package com.example.sportbooking.court.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.sportbooking.court.entity.Court;

public interface CourtRepository extends JpaRepository<Court, Long> {

	// findByVenue_Id: el "_" navega la relación (Court.venue.id), Spring Data
	// arma el JOIN solo a partir del nombre del método. La vamos a necesitar
	// para listar "las canchas de esta sede" (GET /courts?venueId=X) y más
	// adelante la va a reutilizar court-schedule para validar pertenencia.
	List<Court> findByVenue_Id(Long venueId);

}
