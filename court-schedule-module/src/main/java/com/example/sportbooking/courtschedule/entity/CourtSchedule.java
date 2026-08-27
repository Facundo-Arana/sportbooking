package com.example.sportbooking.courtschedule.entity;

import com.example.sportbooking.court.entity.Court;
import com.example.sportbooking.timeblock.entity.TimeBlock;

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
@Table(name = "court_schedule", schema = "platform")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class CourtSchedule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Primer caso con DOS @ManyToOne simultáneos hacia módulos distintos.
	// Mismo patrón que Court.venue / TimeBlock.venue: el campo es el objeto
	// relacionado, @JoinColumn apunta a la FK real de la tabla.
	@ManyToOne
	@JoinColumn(name = "fk_court_id")
	private Court court;

	@ManyToOne
	@JoinColumn(name = "fk_time_block_id")
	private TimeBlock timeBlock;

	// day_of_week es int2 (smallint) en Postgres (ver sportbooking.sql) ->
	// Short en Java, no Integer, para que el mapeo sea 1 a 1 con el tipo de
	// columna real (mismo criterio que LocalTime en TimeBlock: usar el tipo
	// que representa exactamente lo que hay en la base).
	private Short day_of_week;

	// Boolean (wrapper), no boolean primitivo: mismo motivo que
	// Court.is_active - en el update parcial hace falta distinguir "no vino
	// en el body" (null) de "vino en false a propósito".
	private Boolean is_enabled;

	private String notes;

	// Constructor de alta: is_enabled se setea acá en true, mismo criterio
	// que Court.is_active/created_at (bitácora punto 5 y 9) - no confiamos
	// en el DEFAULT true de la columna, lo dejamos explícito.
	public CourtSchedule(Court court, TimeBlock timeBlock, Short day_of_week, String notes) {
		this.court = court;
		this.timeBlock = timeBlock;
		this.day_of_week = day_of_week;
		this.notes = notes;
		this.is_enabled = true;
	}

}
