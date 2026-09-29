package com.example.sportbooking.reservation.entity;

import java.sql.Timestamp;
import java.time.LocalDate;

import com.example.sportbooking.court.entity.Court;
import com.example.sportbooking.timeblock.entity.TimeBlock;
import com.example.sportbooking.user.entity.User;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "reservation", schema = "platform")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class Reservation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private LocalDate date;

	// Primer módulo con TRES @ManyToOne hacia módulos externos distintos
	// (court-module, time-block-module y user-module x2). Mismo patrón que
	// CourtSchedule.court/timeBlock.
	@ManyToOne
	@JoinColumn(name = "fk_court_id")
	private Court court;

	@ManyToOne
	@JoinColumn(name = "fk_time_block_id")
	private TimeBlock timeBlock;

	// Quién generó la reserva (siempre un usuario autenticado). El constraint
	// de FK (fk_reserved_by_user_id / fk_reserved_for_user_id) lo administra
	// Hibernate con ddl-auto=update, con nombres generados tipo fk<hash>. No
	// agregarlo a mano con otro nombre: ddl-auto crea un duplicado sobre la
	// misma columna (bitácora punto 26). El @ManyToOne no necesita la FK
	// física para el JOIN, pero el constraint es lo que da integridad
	// referencial real.
	@ManyToOne
	@JoinColumn(name = "fk_reserved_by_user_id")
	private User reservedByUser;

	// Para quién es la reserva. Es OPCIONAL y mutuamente excluyente con
	// reserved_for_name (ver ReservationService.validateReservedFor): o se
	// reserva para un usuario registrado (este campo), o para alguien sin
	// cuenta con solo un nombre libre (reserved_for_name), no ambos. Si
	// ninguno viene, se asume que la reserva es para reservedByUser mismo.
	@ManyToOne
	@JoinColumn(name = "fk_reserved_for_user_id")
	private User reservedForUser;

	private String reserved_for_name;

	// Sin @Builder.Default a propósito: el valor inicial se fija en el
	// constructor de alta, no acá, mismo criterio que Court.is_active /
	// CourtSchedule.is_enabled (no confiar en un default implícito).
	private String status;

	private String payment_status;

	private Timestamp created_at;

	// Constructor de alta: status y payment_status arrancan en "PENDING" a
	// mano (la columna no tiene DEFAULT en la base, ver sportbooking.sql),
	// igual criterio que created_at en User/Venue.
	public Reservation(LocalDate date, Court court, TimeBlock timeBlock, User reservedByUser, User reservedForUser,
			String reserved_for_name) {
		this.date = date;
		this.court = court;
		this.timeBlock = timeBlock;
		this.reservedByUser = reservedByUser;
		this.reservedForUser = reservedForUser;
		this.reserved_for_name = reserved_for_name;
		this.status = "PENDING";
		this.payment_status = "PENDING";
		this.created_at = new Timestamp(System.currentTimeMillis());
	}

}
