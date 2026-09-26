package com.example.sportbooking.courtschedule.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.sportbooking.court.entity.Court;
import com.example.sportbooking.court.exception.CourtNotFoundException;
import com.example.sportbooking.court.repository.CourtRepository;
import com.example.sportbooking.courtschedule.dto.CourtScheduleDTO;
import com.example.sportbooking.courtschedule.entity.CourtSchedule;
import com.example.sportbooking.courtschedule.exception.CourtScheduleNotFoundException;
import com.example.sportbooking.courtschedule.repository.CourtScheduleRepository;
import com.example.sportbooking.timeblock.entity.TimeBlock;
import com.example.sportbooking.timeblock.exception.TimeBlockNotFoundException;
import com.example.sportbooking.timeblock.repository.TimeBlockRepository;

@Service
public class CourtScheduleService {

	private final CourtScheduleRepository courtScheduleRepository;
	private final CourtRepository courtRepository;
	private final TimeBlockRepository timeBlockRepository;

	public CourtScheduleService(CourtScheduleRepository courtScheduleRepository, CourtRepository courtRepository,
			TimeBlockRepository timeBlockRepository) {
		this.courtScheduleRepository = courtScheduleRepository;
		this.courtRepository = courtRepository;
		this.timeBlockRepository = timeBlockRepository;
	}

	public CourtSchedule create(CourtScheduleDTO dto) {
		if (dto.getCourt_id() == null) {
			throw new IllegalArgumentException("El court_id es obligatorio");
		}
		if (dto.getTime_block_id() == null) {
			throw new IllegalArgumentException("El time_block_id es obligatorio");
		}
		if (dto.getDay_of_week() == null) {
			throw new IllegalArgumentException("El day_of_week es obligatorio");
		}
		validateDayOfWeek(dto.getDay_of_week());

		// CourtNotFoundException / TimeBlockNotFoundException -> 404:
		// reutilizadas desde court-module y time-block-module, mismo
		// criterio que VenueNotFoundException en CourtService/TimeBlockService.
		Court court = courtRepository.findById(dto.getCourt_id())
				.orElseThrow(() -> new CourtNotFoundException(dto.getCourt_id()));
		TimeBlock timeBlock = timeBlockRepository.findById(dto.getTime_block_id())
				.orElseThrow(() -> new TimeBlockNotFoundException(dto.getTime_block_id()));

		validateSameVenue(court, timeBlock);

		CourtSchedule courtSchedule = new CourtSchedule(court, timeBlock, dto.getDay_of_week(), dto.getNotes());
		return courtScheduleRepository.save(courtSchedule);
	}

	public List<CourtSchedule> findAll(Long courtId) {
		if (courtId != null) {
			return courtScheduleRepository.findByCourt_Id(courtId);
		}
		return courtScheduleRepository.findAll();
	}

	public CourtSchedule findById(Long id) {
		return courtScheduleRepository.findById(id)
				.orElseThrow(() -> new CourtScheduleNotFoundException(id));
	}

	public CourtSchedule update(Long id, CourtScheduleDTO dto) {
		CourtSchedule courtSchedule = findById(id); // ya tira CourtScheduleNotFoundException si no existe

		// Venue de ORIGEN, capturado antes de aplicar el merge parcial (ver
		// validateVenueUnchanged mas abajo y bitacora, punto 22).
		Long originalVenueId = courtSchedule.getCourt().getVenue().getId();

		// Update parcial, mismo criterio que los services anteriores: solo
		// se pisan los campos que vinieron en el body.
		if (dto.getCourt_id() != null) {
			Court court = courtRepository.findById(dto.getCourt_id())
					.orElseThrow(() -> new CourtNotFoundException(dto.getCourt_id()));
			courtSchedule.setCourt(court);
		}
		if (dto.getTime_block_id() != null) {
			TimeBlock timeBlock = timeBlockRepository.findById(dto.getTime_block_id())
					.orElseThrow(() -> new TimeBlockNotFoundException(dto.getTime_block_id()));
			courtSchedule.setTimeBlock(timeBlock);
		}
		if (dto.getDay_of_week() != null) {
			validateDayOfWeek(dto.getDay_of_week());
			courtSchedule.setDay_of_week(dto.getDay_of_week());
		}
		if (dto.getIs_enabled() != null) {
			courtSchedule.setIs_enabled(dto.getIs_enabled());
		}
		if (dto.getNotes() != null) {
			courtSchedule.setNotes(dto.getNotes());
		}

		// Re-validamos contra el estado FINAL de la entity (igual criterio
		// que TimeBlockService.validateTimeRange en el update): si el PUT
		// solo trae court_id, la franja horaria que ya tenía guardada puede
		// quedar apuntando a un venue distinto, y viceversa.
		validateVenueUnchanged(originalVenueId, courtSchedule.getCourt());
		validateSameVenue(courtSchedule.getCourt(), courtSchedule.getTimeBlock());

		return courtScheduleRepository.save(courtSchedule);
	}

	public void delete(Long id) {
		if (!courtScheduleRepository.existsById(id)) {
			throw new CourtScheduleNotFoundException(id);
		}
		courtScheduleRepository.deleteById(id);
	}

	// Validación cruzada nueva (no existía en los módulos anteriores): la
	// cancha y la franja horaria tienen que pertenecer al MISMO venue. No
	// tendría sentido asignarle a una cancha de la sede A un horario
	// definido para la sede B. Es la misma categoría que
	// TimeBlockService.validateTimeRange (consistencia interna del
	// recurso), pero acá la consistencia es entre dos relaciones en vez de
	// dos campos simples.
	private void validateSameVenue(Court court, TimeBlock timeBlock) {
		Long courtVenueId = court.getVenue().getId();
		Long timeBlockVenueId = timeBlock.getVenue().getId();
		if (!courtVenueId.equals(timeBlockVenueId)) {
			throw new IllegalArgumentException(
					"La cancha y la franja horaria deben pertenecer al mismo venue");
		}
	}

	// Punto 22 de la bitacora: el @PreAuthorize de update() autoriza contra
	// el venue ORIGEN del horario (canManageCourtSchedule(#id)), nunca contra
	// el destino. validateSameVenue() no alcanza: si el PUT trae court_id Y
	// time_block_id de otro venue, ambos son consistentes ENTRE SI y pasa,
	// pero el horario "se mudo" a un venue donde el usuario no tiene rol.
	// Se permite reprogramar dentro del mismo venue (otra cancha, otra
	// franja); mover entre venues = eliminar y crear de nuevo (donde el
	// @PreAuthorize de create() si chequea el venue destino).
	private void validateVenueUnchanged(Long originalVenueId, Court finalCourt) {
		if (!originalVenueId.equals(finalCourt.getVenue().getId())) {
			throw new IllegalArgumentException(
					"Un horario no se puede mover a una cancha de otro venue; eliminalo y crea uno nuevo en el venue destino");
		}
	}

	private void validateDayOfWeek(Short dayOfWeek) {
		if (dayOfWeek < 0 || dayOfWeek > 6) {
			throw new IllegalArgumentException("day_of_week debe estar entre 0 (domingo) y 6 (sábado)");
		}
	}

}
