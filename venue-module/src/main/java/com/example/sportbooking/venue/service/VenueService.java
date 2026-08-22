package com.example.sportbooking.venue.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.sportbooking.venue.dto.VenueDTO;
import com.example.sportbooking.venue.entity.Venue;
import com.example.sportbooking.venue.exception.VenueNotFoundException;
import com.example.sportbooking.venue.repository.VenueRepository;

@Service
public class VenueService {

	private final VenueRepository venueRepository;

	public VenueService(VenueRepository venueRepository) {
		this.venueRepository = venueRepository;
	}

	public Venue create(VenueDTO dto) {
		if (dto.getName() == null || dto.getName().isBlank()) {
			// IllegalArgumentException para "dato de entrada inválido", distinto
			// de VenueNotFoundException ("el recurso no existe"). El controller
			// mapea cada una a un status HTTP distinto (400 vs 404).
			throw new IllegalArgumentException("El nombre del venue es obligatorio");
		}

		Venue venue = new Venue(dto.getName(), dto.getAddress(), dto.getPhone());
		return venueRepository.save(venue);
	}

	public List<Venue> findAll() {
		return venueRepository.findAll();
	}

	public Venue findById(Long id) {
		return venueRepository.findById(id)
				.orElseThrow(() -> new VenueNotFoundException(id));
	}

	public Venue update(Long id, VenueDTO dto) {
		Venue venue = findById(id); // ya tira VenueNotFoundException si no existe

		// Update parcial: solo se pisan los campos que vinieron en el body.
		// Si el cliente no manda "phone", no lo borra.
		if (dto.getName() != null) {
			venue.setName(dto.getName());
		}
		if (dto.getAddress() != null) {
			venue.setAddress(dto.getAddress());
		}
		if (dto.getPhone() != null) {
			venue.setPhone(dto.getPhone());
		}

		return venueRepository.save(venue);
	}

	public void delete(Long id) {
		if (!venueRepository.existsById(id)) {
			throw new VenueNotFoundException(id);
		}
		venueRepository.deleteById(id);
	}

}
