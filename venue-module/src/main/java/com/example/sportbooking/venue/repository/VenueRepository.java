package com.example.sportbooking.venue.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.sportbooking.venue.entity.Venue;

public interface VenueRepository extends JpaRepository<Venue, Long> {

	// CRUD básico (save, findById, findAll, deleteById, existsById, etc.)
	// ya viene resuelto por JpaRepository. Se agregan acá métodos derivados
	// o @Query a medida que haga falta (ej. búsqueda por nombre, por ciudad).

}
