package com.example.sportbooking.uservenuerole.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.sportbooking.user.entity.Role;
import com.example.sportbooking.user.entity.User;
import com.example.sportbooking.user.exception.RoleNotFoundException;
import com.example.sportbooking.user.exception.UserNotFoundException;
import com.example.sportbooking.user.repository.RoleRepository;
import com.example.sportbooking.user.repository.UserRepository;
import com.example.sportbooking.uservenuerole.dto.UserVenueRoleDTO;
import com.example.sportbooking.uservenuerole.entity.UserVenueRole;
import com.example.sportbooking.uservenuerole.exception.UserVenueRoleNotFoundException;
import com.example.sportbooking.uservenuerole.repository.UserVenueRoleRepository;
import com.example.sportbooking.venue.entity.Venue;
import com.example.sportbooking.venue.exception.VenueNotFoundException;
import com.example.sportbooking.venue.repository.VenueRepository;

// Sin update(): a diferencia de los módulos anteriores, una asignación de
// rol no se "edita" -- conceptualmente no tiene sentido de negocio cambiarle
// el usuario o el venue a una fila existente (eso sería otra asignación
// distinta). El flujo correcto para cambiar un rol es delete + create, no
// un PUT parcial. Ver bitácora punto 21.
@Service
public class UserVenueRoleService {

	private final UserVenueRoleRepository userVenueRoleRepository;
	private final VenueRepository venueRepository;
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;

	public UserVenueRoleService(UserVenueRoleRepository userVenueRoleRepository, VenueRepository venueRepository,
			UserRepository userRepository, RoleRepository roleRepository) {
		this.userVenueRoleRepository = userVenueRoleRepository;
		this.venueRepository = venueRepository;
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
	}

	public UserVenueRole create(UserVenueRoleDTO dto) {
		if (dto.getVenue_id() == null) {
			throw new IllegalArgumentException("El venue_id es obligatorio");
		}
		if (dto.getUser_id() == null) {
			throw new IllegalArgumentException("El user_id es obligatorio");
		}
		if (dto.getRole_id() == null) {
			throw new IllegalArgumentException("El role_id es obligatorio");
		}

		Venue venue = venueRepository.findById(dto.getVenue_id())
				.orElseThrow(() -> new VenueNotFoundException(dto.getVenue_id()));
		User user = userRepository.findById(dto.getUser_id())
				.orElseThrow(() -> new UserNotFoundException(dto.getUser_id()));
		Role role = roleRepository.findById(dto.getRole_id())
				.orElseThrow(() -> new RoleNotFoundException(dto.getRole_id()));

		// 400, no 409: acá todavía estamos validando el INPUT antes de tocar
		// la base (mismo criterio que UserService.registerUser). El 409 real
		// (DataIntegrityViolationException -> GlobalExceptionHandler) queda
		// como red de seguridad si dos altas del mismo trío llegan a la vez.
		if (userVenueRoleRepository.existsByVenue_IdAndUser_IdAndRole_Id(dto.getVenue_id(), dto.getUser_id(),
				dto.getRole_id())) {
			throw new IllegalArgumentException("Ese usuario ya tiene ese rol asignado en ese venue");
		}

		UserVenueRole userVenueRole = new UserVenueRole(venue, user, role);
		return userVenueRoleRepository.save(userVenueRole);
	}

	// ?venueId=X y/o ?userId=X, ambos opcionales y combinables -- mismo
	// patrón que ReservationService.findAll(courtId, date).
	public List<UserVenueRole> findAll(Long venueId, Long userId) {
		if (venueId != null && userId != null) {
			return userVenueRoleRepository.findByVenue_IdAndUser_Id(venueId, userId);
		}
		if (venueId != null) {
			return userVenueRoleRepository.findByVenue_Id(venueId);
		}
		if (userId != null) {
			return userVenueRoleRepository.findByUser_Id(userId);
		}
		return userVenueRoleRepository.findAll();
	}

	public UserVenueRole findById(Long id) {
		return userVenueRoleRepository.findById(id)
				.orElseThrow(() -> new UserVenueRoleNotFoundException(id));
	}

	public void delete(Long id) {
		if (!userVenueRoleRepository.existsById(id)) {
			throw new UserVenueRoleNotFoundException(id);
		}
		userVenueRoleRepository.deleteById(id);
	}

}
