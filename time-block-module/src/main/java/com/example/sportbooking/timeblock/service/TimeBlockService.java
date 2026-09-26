package com.example.sportbooking.timeblock.service;

import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.sportbooking.timeblock.dto.TimeBlockDTO;
import com.example.sportbooking.timeblock.entity.TimeBlock;
import com.example.sportbooking.timeblock.exception.TimeBlockNotFoundException;
import com.example.sportbooking.timeblock.repository.TimeBlockRepository;
import com.example.sportbooking.venue.entity.Venue;
import com.example.sportbooking.venue.exception.VenueNotFoundException;
import com.example.sportbooking.venue.repository.VenueRepository;

@Service
public class TimeBlockService {

	private final TimeBlockRepository timeBlockRepository;
	private final VenueRepository venueRepository;

	public TimeBlockService(TimeBlockRepository timeBlockRepository, VenueRepository venueRepository) {
		this.timeBlockRepository = timeBlockRepository;
		this.venueRepository = venueRepository;
	}

	public TimeBlock create(TimeBlockDTO dto) {
		if (dto.getVenue_id() == null) {
			// IllegalArgumentException -> 400: dato de entrada inválido.
			throw new IllegalArgumentException("El venue_id es obligatorio");
		}
		if (dto.getStart_time() == null || dto.getEnd_time() == null) {
			throw new IllegalArgumentException("start_time y end_time son obligatorios");
		}
		validateTimeRange(dto.getStart_time(), dto.getEnd_time());

		// VenueNotFoundException -> 404: reutilizada desde venue-module, mismo
		// criterio que CourtService (ver bitácora, punto 5 y 9).
		Venue venue = venueRepository.findById(dto.getVenue_id())
				.orElseThrow(() -> new VenueNotFoundException(dto.getVenue_id()));

		TimeBlock timeBlock = new TimeBlock(venue, dto.getBlock_group(), dto.getLabel(), dto.getStart_time(),
				dto.getEnd_time());
		return timeBlockRepository.save(timeBlock);
	}

	public List<TimeBlock> findAll(Long venueId) {
		if (venueId != null) {
			return timeBlockRepository.findByVenue_Id(venueId);
		}
		return timeBlockRepository.findAll();
	}

	public TimeBlock findById(Long id) {
		return timeBlockRepository.findById(id)
				.orElseThrow(() -> new TimeBlockNotFoundException(id));
	}

	public TimeBlock update(Long id, TimeBlockDTO dto) {
		TimeBlock timeBlock = findById(id); // ya tira TimeBlockNotFoundException si no existe

		// venue_id es INMUTABLE despues del alta (bitacora, punto 22), mismo
		// criterio que CourtService.update(): el @PreAuthorize evalua el venue
		// ACTUAL antes de correr el metodo, y ademas los court_schedule/
		// reservation existentes que usan esta franja quedarian apuntando a
		// una cancha de otro venue. Reenviar el mismo venue_id es un no-op.
		Long currentVenueId = timeBlock.getVenue() != null ? timeBlock.getVenue().getId() : null;
		if (dto.getVenue_id() != null && !dto.getVenue_id().equals(currentVenueId)) {
			throw new IllegalArgumentException(
					"El venue_id de una franja horaria no se puede modificar; eliminala y crea una nueva en el venue destino");
		}

		// Update parcial, mismo criterio que VenueService/CourtService: solo
		// se pisan los campos que vinieron en el body.
		if (dto.getBlock_group() != null) {
			timeBlock.setBlock_group(dto.getBlock_group());
		}
		if (dto.getLabel() != null) {
			timeBlock.setLabel(dto.getLabel());
		}
		if (dto.getStart_time() != null) {
			timeBlock.setStart_time(dto.getStart_time());
		}
		if (dto.getEnd_time() != null) {
			timeBlock.setEnd_time(dto.getEnd_time());
		}

		// Re-validamos la relación start/end sobre el estado FINAL de la
		// entity, no sobre el DTO: si el PUT solo trae uno de los dos campos
		// (por ejemplo, solo end_time), igual tiene que seguir cumpliéndose
		// end_time > start_time contra el valor que ya tenía guardado el otro.
		validateTimeRange(timeBlock.getStart_time(), timeBlock.getEnd_time());

		return timeBlockRepository.save(timeBlock);
	}

	public void delete(Long id) {
		if (!timeBlockRepository.existsById(id)) {
			throw new TimeBlockNotFoundException(id);
		}
		timeBlockRepository.deleteById(id);
	}

	// Validación cruzada entre dos campos del mismo recurso: no es "campo
	// nulo" (2.x) ni "recurso inexistente" (5.x/9.x), es una regla de
	// consistencia interna del dato. Se extrae a un método privado porque se
	// necesita tanto en create() como en update() (después del merge parcial).
	private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
		if (!endTime.isAfter(startTime)) {
			throw new IllegalArgumentException("end_time debe ser posterior a start_time");
		}
	}

}
