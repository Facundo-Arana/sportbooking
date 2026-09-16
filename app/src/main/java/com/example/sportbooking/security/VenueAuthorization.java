package com.example.sportbooking.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.example.sportbooking.auth.security.util.CustomUserDetails;
import com.example.sportbooking.court.repository.CourtRepository;
import com.example.sportbooking.courtschedule.repository.CourtScheduleRepository;
import com.example.sportbooking.reservation.repository.ReservationRepository;
import com.example.sportbooking.timeblock.repository.TimeBlockRepository;
import com.example.sportbooking.uservenuerole.repository.UserVenueRoleRepository;

// Vive en app-module, no en user-venue-role-module, por la misma razón que
// GlobalExceptionHandler (ver bitácora punto 20): es el único módulo que ya
// depende en su pom.xml de los 7 módulos de dominio + auth-module, así que
// es el único que puede resolver "¿el usuario autenticado puede manejar
// este venue/court/time-block/court-schedule/reservation?" sin crear una
// dependencia circular (user-venue-role-module depende de venue-module, así
// que venue-module NO puede depender de vuelta de user-venue-role-module).
//
// Los controllers de cada módulo (Venue/Court/TimeBlock/CourtSchedule/
// Reservation/UserVenueRole) referencian este bean por NOMBRE dentro de la
// expresión SpEL de @PreAuthorize ("@venueAuthorization.canManageX(...)"),
// no por import de clase Java -- Spring resuelve el bean en tiempo de
// ejecución contra el ApplicationContext, así que ningún módulo de dominio
// necesita depender de este para poder usarlo. Es el mismo truco que
// permite que GlobalExceptionHandler viva en un solo lugar sin que cada
// módulo lo importe.
@Component("venueAuthorization")
public class VenueAuthorization {

	private final UserVenueRoleRepository userVenueRoleRepository;
	private final CourtRepository courtRepository;
	private final TimeBlockRepository timeBlockRepository;
	private final CourtScheduleRepository courtScheduleRepository;
	private final ReservationRepository reservationRepository;

	public VenueAuthorization(UserVenueRoleRepository userVenueRoleRepository, CourtRepository courtRepository,
			TimeBlockRepository timeBlockRepository, CourtScheduleRepository courtScheduleRepository,
			ReservationRepository reservationRepository) {
		this.userVenueRoleRepository = userVenueRoleRepository;
		this.courtRepository = courtRepository;
		this.timeBlockRepository = timeBlockRepository;
		this.courtScheduleRepository = courtScheduleRepository;
		this.reservationRepository = reservationRepository;
	}

	// El usuario autenticado nunca llega null a estos métodos en la práctica
	// (SecurityConfiguration ya exige authenticated() antes de evaluar
	// @PreAuthorize), pero se chequea igual por robustez -- si algún día se
	// abre un endpoint sin autenticación por error, esto no explota con NPE,
	// simplemente niega el acceso.
	private CustomUserDetails currentUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails)) {
			return null;
		}
		return (CustomUserDetails) auth.getPrincipal();
	}

	// Regla base de todo el esquema: "puede manejar el venue" = tiene AL
	// MENOS UNA fila en user_venue_role para ese venue, sin distinguir por
	// ahora qué Role puntual tiene asignado (OWNER, STAFF, lo que sea que
	// tenga el catálogo de roles). Es una decisión deliberada de no
	// sobre-diseñar: hoy no hay un segundo nivel de permisos pedido (ej.
	// "STAFF puede ver pero no borrar"), así que separar por nombre de rol
	// acá sería anticipar un requisito que todavía no existe -- mismo
	// criterio de "esperar a la 3ra necesidad real" que ya aparece en la
	// bitácora (punto 17, sobre duplicar validateSameVenue). Si en el
	// futuro hace falta esa distinción, este es el único método a tocar.
	public boolean canManageVenue(Long venueId) {
		CustomUserDetails user = currentUser();
		if (user == null || venueId == null) {
			return false;
		}
		Long userId = user.getUser().getId();
		return !userVenueRoleRepository.findByVenue_IdAndUser_Id(venueId, userId).isEmpty();
	}

	public boolean canManageCourt(Long courtId) {
		if (courtId == null) {
			return false;
		}
		return courtRepository.findById(courtId)
				.map(court -> court.getVenue() != null && canManageVenue(court.getVenue().getId()))
				.orElse(false);
	}

	public boolean canManageTimeBlock(Long timeBlockId) {
		if (timeBlockId == null) {
			return false;
		}
		return timeBlockRepository.findById(timeBlockId)
				.map(timeBlock -> timeBlock.getVenue() != null && canManageVenue(timeBlock.getVenue().getId()))
				.orElse(false);
	}

	public boolean canManageCourtSchedule(Long courtScheduleId) {
		if (courtScheduleId == null) {
			return false;
		}
		return courtScheduleRepository.findById(courtScheduleId)
				.map(schedule -> schedule.getCourt() != null && schedule.getCourt().getVenue() != null
						&& canManageVenue(schedule.getCourt().getVenue().getId()))
				.orElse(false);
	}

	public boolean canManageUserVenueRole(Long userVenueRoleId) {
		if (userVenueRoleId == null) {
			return false;
		}
		return userVenueRoleRepository.findById(userVenueRoleId)
				.map(uvr -> uvr.getVenue() != null && canManageVenue(uvr.getVenue().getId()))
				.orElse(false);
	}

	// Distinto de los anteriores: una reserva la puede manejar (editar/
	// cancelar) quien la creó -- no hace falta tener rol en el venue para
	// cancelar TU PROPIA reserva -- o alguien con rol en el venue de la
	// cancha reservada (personal de la sede, que gestiona la agenda de
	// todos los clientes). Ninguna de las dos cosas exige ser global ADMIN;
	// ese bypass se agrega aparte en el @PreAuthorize de cada endpoint
	// ("hasRole('ADMIN') or @venueAuthorization.canManageX(...)") para no
	// mezclar dos conceptos distintos (rol global vs. relación con el
	// recurso puntual) dentro del mismo método.
	public boolean canManageReservation(Long reservationId) {
		CustomUserDetails user = currentUser();
		if (user == null || reservationId == null) {
			return false;
		}
		Long userId = user.getUser().getId();
		return reservationRepository.findById(reservationId).map(reservation -> {
			boolean isOwner = reservation.getReservedByUser() != null
					&& userId.equals(reservation.getReservedByUser().getId());
			boolean venueStaff = reservation.getCourt() != null && reservation.getCourt().getVenue() != null
					&& canManageVenue(reservation.getCourt().getVenue().getId());
			return isOwner || venueStaff;
		}).orElse(false);
	}

}
