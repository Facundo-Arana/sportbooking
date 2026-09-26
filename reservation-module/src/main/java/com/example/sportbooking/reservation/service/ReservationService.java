package com.example.sportbooking.reservation.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.sportbooking.court.entity.Court;
import com.example.sportbooking.court.exception.CourtNotFoundException;
import com.example.sportbooking.court.repository.CourtRepository;
import com.example.sportbooking.reservation.dto.ReservationDTO;
import com.example.sportbooking.reservation.entity.Reservation;
import com.example.sportbooking.reservation.exception.ReservationNotFoundException;
import com.example.sportbooking.reservation.repository.ReservationRepository;
import com.example.sportbooking.timeblock.entity.TimeBlock;
import com.example.sportbooking.timeblock.exception.TimeBlockNotFoundException;
import com.example.sportbooking.timeblock.repository.TimeBlockRepository;
import com.example.sportbooking.user.entity.User;
import com.example.sportbooking.user.exception.UserNotFoundException;
import com.example.sportbooking.user.repository.UserRepository;

@Service
public class ReservationService {

	// Estados válidos de status/payment_status. Son varchar sin CHECK
	// constraint en la base (ver sportbooking.sql), así que la validación de
	// "valor permitido" queda del lado de la aplicación, igual criterio que
	// day_of_week en court-schedule-module (ahí era un rango numérico, acá
	// es un conjunto cerrado de strings).
	private static final List<String> VALID_STATUSES = List.of("PENDING", "CONFIRMED", "CANCELLED");
	private static final List<String> VALID_PAYMENT_STATUSES = List.of("PENDING", "PAID", "REFUNDED");

	private static final String CANCELLED = "CANCELLED";

	private final ReservationRepository reservationRepository;
	private final CourtRepository courtRepository;
	private final TimeBlockRepository timeBlockRepository;
	private final UserRepository userRepository;

	public ReservationService(ReservationRepository reservationRepository, CourtRepository courtRepository,
			TimeBlockRepository timeBlockRepository, UserRepository userRepository) {
		this.reservationRepository = reservationRepository;
		this.courtRepository = courtRepository;
		this.timeBlockRepository = timeBlockRepository;
		this.userRepository = userRepository;
	}

	public Reservation create(ReservationDTO dto) {
		if (dto.getDate() == null) {
			throw new IllegalArgumentException("El date es obligatorio");
		}
		if (dto.getCourt_id() == null) {
			throw new IllegalArgumentException("El court_id es obligatorio");
		}
		if (dto.getTime_block_id() == null) {
			throw new IllegalArgumentException("El time_block_id es obligatorio");
		}
		if (dto.getReserved_by_user_id() == null) {
			throw new IllegalArgumentException("El reserved_by_user_id es obligatorio");
		}
		validateReservedFor(dto.getReserved_for_user_id(), dto.getReserved_for_name());

		// CourtNotFoundException / TimeBlockNotFoundException / UserNotFoundException
		// -> 404: reutilizadas desde court-module, time-block-module y user-module,
		// mismo criterio que court-schedule-module con Court y TimeBlock.
		Court court = courtRepository.findById(dto.getCourt_id())
				.orElseThrow(() -> new CourtNotFoundException(dto.getCourt_id()));
		TimeBlock timeBlock = timeBlockRepository.findById(dto.getTime_block_id())
				.orElseThrow(() -> new TimeBlockNotFoundException(dto.getTime_block_id()));

		// Punto 17 de la bitácora: misma validación cruzada que ya existe en
		// CourtScheduleService (ver ese service) - court y timeBlock son las
		// mismas dos relaciones, así que heredan la misma regla de negocio.
		validateSameVenue(court, timeBlock);

		User reservedByUser = userRepository.findById(dto.getReserved_by_user_id())
				.orElseThrow(() -> new UserNotFoundException(dto.getReserved_by_user_id()));

		User reservedForUser = null;
		if (dto.getReserved_for_user_id() != null) {
			reservedForUser = userRepository.findById(dto.getReserved_for_user_id())
					.orElseThrow(() -> new UserNotFoundException(dto.getReserved_for_user_id()));
		}

		validateNoOverlap(dto.getCourt_id(), dto.getTime_block_id(), dto.getDate());

		Reservation reservation = new Reservation(dto.getDate(), court, timeBlock, reservedByUser, reservedForUser,
				dto.getReserved_for_name());
		return reservationRepository.save(reservation);
	}

	// Filtros combinables por cancha y/o fecha - pantalla típica de un
	// booking system: "agenda de esta cancha" (courtId), "todas las reservas
	// de hoy" (date) o ambos ("agenda de esta cancha para hoy").
	public List<Reservation> findAll(Long courtId, LocalDate date) {
		if (courtId != null && date != null) {
			return reservationRepository.findByCourt_IdAndDate(courtId, date);
		}
		if (courtId != null) {
			return reservationRepository.findByCourt_Id(courtId);
		}
		if (date != null) {
			return reservationRepository.findByDate(date);
		}
		return reservationRepository.findAll();
	}

	public Reservation findById(Long id) {
		return reservationRepository.findById(id)
				.orElseThrow(() -> new ReservationNotFoundException(id));
	}

	public Reservation update(Long id, ReservationDTO dto) {
		Reservation reservation = findById(id); // ya tira ReservationNotFoundException si no existe

		// Venue de ORIGEN, capturado antes de aplicar el merge parcial (ver
		// validateVenueUnchanged mas abajo y bitacora, punto 22).
		Long originalVenueId = reservation.getCourt().getVenue().getId();

		// reserved_by_user_id NO se actualiza a propósito: identifica quién
		// originó la reserva, es un dato histórico que no debería cambiar
		// después del alta (a diferencia de court_id/time_block_id, que sí
		// tiene sentido reprogramar).
		if (dto.getDate() != null) {
			reservation.setDate(dto.getDate());
		}
		if (dto.getCourt_id() != null) {
			Court court = courtRepository.findById(dto.getCourt_id())
					.orElseThrow(() -> new CourtNotFoundException(dto.getCourt_id()));
			reservation.setCourt(court);
		}
		if (dto.getTime_block_id() != null) {
			TimeBlock timeBlock = timeBlockRepository.findById(dto.getTime_block_id())
					.orElseThrow(() -> new TimeBlockNotFoundException(dto.getTime_block_id()));
			reservation.setTimeBlock(timeBlock);
		}
		if (dto.getReserved_for_user_id() != null) {
			User reservedForUser = userRepository.findById(dto.getReserved_for_user_id())
					.orElseThrow(() -> new UserNotFoundException(dto.getReserved_for_user_id()));
			reservation.setReservedForUser(reservedForUser);
		}
		if (dto.getReserved_for_name() != null) {
			reservation.setReserved_for_name(dto.getReserved_for_name());
		}
		if (dto.getStatus() != null) {
			validateStatus(dto.getStatus());
			reservation.setStatus(dto.getStatus());
		}
		if (dto.getPayment_status() != null) {
			validatePaymentStatus(dto.getPayment_status());
			reservation.setPayment_status(dto.getPayment_status());
		}

		// Re-validamos contra el estado FINAL de la entity (mismo criterio
		// que en CourtScheduleService.update()): si el PUT trae solo
		// court_id, el timeBlock heredado del estado previo puede quedar
		// apuntando a otro venue, y viceversa.
		validateVenueUnchanged(originalVenueId, reservation.getCourt());
		validateSameVenue(reservation.getCourt(), reservation.getTimeBlock());

		// Re-validamos contra el estado FINAL de la entity, mismo criterio
		// que validateTimeRange/validateSameVenue en los módulos anteriores:
		// si el PUT trae un solo campo (por ejemplo, solo reserved_for_user_id),
		// igual tiene que seguir cumpliéndose la exclusión mutua contra el
		// reserved_for_name que ya tenía guardado.
		validateReservedFor(
				reservation.getReservedForUser() != null ? reservation.getReservedForUser().getId() : null,
				reservation.getReserved_for_name());

		// Misma lógica de superposición que en el alta, pero excluyendo la
		// propia reserva (ver ReservationRepository.
		// existsByCourt_IdAndTimeBlock_IdAndDateAndStatusNotAndIdNot):
		// reprogramar el mismo turno sin cambiar nada no debería "chocar"
		// contra sí mismo.
		if (!CANCELLED.equals(reservation.getStatus())) {
			boolean overlaps = reservationRepository.existsByCourt_IdAndTimeBlock_IdAndDateAndStatusNotAndIdNot(
					reservation.getCourt().getId(), reservation.getTimeBlock().getId(), reservation.getDate(),
					CANCELLED, id);
			if (overlaps) {
				throw new IllegalStateException(
						"Ya existe otra reserva activa para esa cancha, franja horaria y fecha");
			}
		}

		return reservationRepository.save(reservation);
	}

	public void delete(Long id) {
		if (!reservationRepository.existsById(id)) {
			throw new ReservationNotFoundException(id);
		}
		reservationRepository.deleteById(id);
	}

	// Exclusión mutua entre dos campos OPCIONALES: es una categoría de
	// validación nueva (no existía en los módulos anteriores). No es "campo
	// obligatorio faltante" (2.x) ni "consistencia entre dos campos que
	// deben coincidir" (validateTimeRange/validateSameVenue) - acá la regla
	// es "como mucho uno de los dos puede venir cargado". No tiene sentido
	// reservar para un usuario registrado Y para un nombre libre al mismo
	// tiempo; si no viene ninguno, se asume que la reserva es para quien la
	// hizo (reservedByUser).
	private void validateReservedFor(Long reservedForUserId, String reservedForName) {
		boolean hasUser = reservedForUserId != null;
		boolean hasName = reservedForName != null && !reservedForName.isBlank();
		if (hasUser && hasName) {
			throw new IllegalArgumentException(
					"reserved_for_user_id y reserved_for_name son mutuamente excluyentes: la reserva es para un usuario registrado o para un nombre libre, no ambos");
		}
	}

	// Regla de negocio central de un booking system: no se puede reservar
	// dos veces la misma cancha en la misma franja horaria el mismo día.
	// A diferencia de las validaciones anteriores (campo nulo, referencia
	// inexistente, consistencia interna del recurso), esta es una
	// validación de UNICIDAD sobre una combinación de columnas que no está
	// declarada como constraint en la base (ver sportbooking.sql) - se
	// resuelve por completo en la capa de aplicación, con la misma
	// limitación de fondo que ya señala el punto 7 de la bitácora para
	// email/username: no protege contra una condición de carrera real (dos
	// requests simultáneos para el mismo turno). Para eso haría falta un
	// UNIQUE constraint en Postgres sobre (fk_court_id, fk_time_block_id,
	// date) con alguna condición sobre status, o una transacción con lock
	// explícito.
	private void validateNoOverlap(Long courtId, Long timeBlockId, LocalDate date) {
		boolean overlaps = reservationRepository.existsByCourt_IdAndTimeBlock_IdAndDateAndStatusNot(courtId,
				timeBlockId, date, CANCELLED);
		if (overlaps) {
			// IllegalStateException, no IllegalArgumentException: el dato de
			// entrada es válido en sí mismo (todo existe, todo tiene el tipo
			// correcto) - lo que falla es el ESTADO del sistema en ese
			// momento. Es una tercera categoría de excepción junto a
			// "dato inválido" (400) y "no encontrado" (404): "conflicto con
			// el estado actual" (409, ver ReservationController).
			throw new IllegalStateException(
					"Ya existe una reserva activa para esa cancha, franja horaria y fecha");
		}
	}

	// Duplicado literal de CourtScheduleService.validateSameVenue() (ver
	// punto 17 de la bitácora): court y timeBlock deben pertenecer al mismo
	// venue, no tendría sentido reservar una cancha del venue A en una
	// franja horaria definida para el venue B. Se mantiene duplicado a
	// propósito por ahora (mismo criterio que el resto del proyecto: no
	// abstraer hasta tener un tercer caso real) - candidato a extraer a un
	// helper compartido si aparece una tercera necesidad.
	private void validateSameVenue(Court court, TimeBlock timeBlock) {
		Long courtVenueId = court.getVenue().getId();
		Long timeBlockVenueId = timeBlock.getVenue().getId();
		if (!courtVenueId.equals(timeBlockVenueId)) {
			throw new IllegalArgumentException("La cancha y la franja horaria deben pertenecer al mismo venue");
		}
	}

	// Punto 22 de la bitacora. Aca NO alcanza con "re-chequear
	// canManageVenue() sobre el venue resultante" (fix propuesto
	// originalmente): quien maneja una reserva puede ser su DUENO (un
	// cliente sin ningun rol en el venue, ver canManageReservation), y ese
	// chequeo lo dejaria afuera incluso al reprogramar dentro de su propio
	// venue. En cambio, se fija el venue de la reserva desde el alta:
	// reprogramar cancha/franja dentro del mismo venue si, mover la reserva
	// a otro venue = cancelarla y crear una nueva (el create() ya es
	// accion abierta al cliente).
	private void validateVenueUnchanged(Long originalVenueId, Court finalCourt) {
		if (!originalVenueId.equals(finalCourt.getVenue().getId())) {
			throw new IllegalArgumentException(
					"Una reserva no se puede mover a una cancha de otro venue; cancelala y crea una nueva");
		}
	}

	private void validateStatus(String status) {
		if (!VALID_STATUSES.contains(status)) {
			throw new IllegalArgumentException("status debe ser uno de " + VALID_STATUSES);
		}
	}

	private void validatePaymentStatus(String paymentStatus) {
		if (!VALID_PAYMENT_STATUSES.contains(paymentStatus)) {
			throw new IllegalArgumentException("payment_status debe ser uno de " + VALID_PAYMENT_STATUSES);
		}
	}

}
