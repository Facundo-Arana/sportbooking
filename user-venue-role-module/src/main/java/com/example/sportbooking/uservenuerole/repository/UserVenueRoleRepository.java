package com.example.sportbooking.uservenuerole.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.sportbooking.uservenuerole.entity.UserVenueRole;

public interface UserVenueRoleRepository extends JpaRepository<UserVenueRole, Long> {

	// Filtros opcionales para GET /user-venue-roles, mismo patrón que
	// ReservationRepository (findByCourt_Id / findByDate / findByCourt_IdAndDate):
	// "¿qué roles tiene asignados este usuario?" y "¿quién tiene roles en
	// este venue?" son las dos preguntas de negocio más probables.
	List<UserVenueRole> findByUser_Id(Long userId);

	List<UserVenueRole> findByVenue_Id(Long venueId);

	List<UserVenueRole> findByVenue_IdAndUser_Id(Long venueId, Long userId);

	// Chequeo de duplicado en el ALTA, mismo criterio que
	// UserRepository.findByEmail en UserService.registerUser: valida en la
	// capa de aplicación ANTES de llegar al INSERT, para devolver un mensaje
	// prolijo en vez de depender solo del constraint UNIQUE de la base. La
	// base (user_venue_role_unique) queda como red de seguridad real contra
	// la misma condición de carrera del punto 19/20 de la bitácora.
	boolean existsByVenue_IdAndUser_IdAndRole_Id(Long venueId, Long userId, Long roleId);

}
