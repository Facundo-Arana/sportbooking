package com.example.sportbooking.court.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.sportbooking.court.dto.CourtDTO;
import com.example.sportbooking.court.entity.Court;
import com.example.sportbooking.court.exception.CourtNotFoundException;
import com.example.sportbooking.court.repository.CourtRepository;
import com.example.sportbooking.venue.entity.Venue;
import com.example.sportbooking.venue.exception.VenueNotFoundException;
import com.example.sportbooking.venue.repository.VenueRepository;

@Service
public class CourtService {

	private final CourtRepository courtRepository;
	private final VenueRepository venueRepository;

	public CourtService(CourtRepository courtRepository, VenueRepository venueRepository) {
		this.courtRepository = courtRepository;
		this.venueRepository = venueRepository;
	}

	public Court create(CourtDTO dto) {
		if (dto.getName() == null || dto.getName().isBlank()) {
			// IllegalArgumentException -> 400: el dato en sí está mal formado.
			throw new IllegalArgumentException("El nombre de la cancha es obligatorio");
		}
		if (dto.getVenue_id() == null) {
			throw new IllegalArgumentException("El venue_id es obligatorio");
		}

		// VenueNotFoundException -> 404: acá el dato SÍ está bien formado
		// (es un id válido), lo que falla es que la referencia no existe.
		// Reutilizamos la excepción del venue-module en vez de inventar una
		// nueva: la relación conceptual es "recurso referenciado inexistente"
		// en ambos casos, no hace falta un tipo nuevo por módulo.
		Venue venue = venueRepository.findById(dto.getVenue_id())
				.orElseThrow(() -> new VenueNotFoundException(dto.getVenue_id()));

		boolean indoor = dto.getIndoor() != null && dto.getIndoor();

		Court court = new Court(venue, dto.getName(), dto.getSport_type(), indoor, dto.getSurface_type(),
				dto.getMax_players());
		return courtRepository.save(court);
	}

	public List<Court> findAll(Long venueId) {
		if (venueId != null) {
			return courtRepository.findByVenue_Id(venueId);
		}
		return courtRepository.findAll();
	}

	public Court findById(Long id) {
		return courtRepository.findById(id)
				.orElseThrow(() -> new CourtNotFoundException(id));
	}

	public Court update(Long id, CourtDTO dto) {
		Court court = findById(id); // ya tira CourtNotFoundException si no existe

		// venue_id es INMUTABLE despues del alta (bitacora, punto 22). Dos
		// razones: (1) seguridad -- el @PreAuthorize del controller evalua el
		// venue ACTUAL de la cancha antes de correr este metodo, asi que si
		// el PUT pudiera cambiar el venue, un usuario con rol solo en la
		// sede A podria mover su cancha a la sede B; (2) integridad -- los
		// court_schedule y reservation ya creados para esta cancha apuntan a
		// time_blocks del venue original, mover la cancha los dejaria
		// inconsistentes (violando validateSameVenue). Si el body reenvia el
		// MISMO venue_id (el front puede mandar el formulario completo) es
		// un no-op; si trae uno distinto, se rechaza con 400.
		Long currentVenueId = court.getVenue() != null ? court.getVenue().getId() : null;
		if (dto.getVenue_id() != null && !dto.getVenue_id().equals(currentVenueId)) {
			throw new IllegalArgumentException(
					"El venue_id de una cancha no se puede modificar; eliminala y crea una nueva en el venue destino");
		}

		// Update parcial, igual criterio que VenueService: solo se pisan los
		// campos que vinieron en el body.
		if (dto.getName() != null) {
			court.setName(dto.getName());
		}
		if (dto.getSport_type() != null) {
			court.setSport_type(dto.getSport_type());
		}
		if (dto.getIndoor() != null) {
			court.setIndoor(dto.getIndoor());
		}
		if (dto.getSurface_type() != null) {
			court.setSurface_type(dto.getSurface_type());
		}
		if (dto.getMax_players() != null) {
			court.setMax_players(dto.getMax_players());
		}
		if (dto.getIs_active() != null) {
			court.setIs_active(dto.getIs_active());
		}

		return courtRepository.save(court);
	}

	public void delete(Long id) {
		if (!courtRepository.existsById(id)) {
			throw new CourtNotFoundException(id);
		}
		courtRepository.deleteById(id);
	}

}
