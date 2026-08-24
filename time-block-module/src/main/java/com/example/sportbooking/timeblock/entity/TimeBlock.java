package com.example.sportbooking.timeblock.entity;

import java.time.LocalTime;

import com.example.sportbooking.venue.entity.Venue;

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
@Table(name = "time_block", schema = "platform")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class TimeBlock {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Mismo patrón que Court.venue: @ManyToOne + @JoinColumn hacia la FK real
	// (fk_venue_id). Cada franja horaria pertenece a una sede.
	@ManyToOne
	@JoinColumn(name = "fk_venue_id")
	private Venue venue;

	// block_group agrupa franjas relacionadas (ej. turno "mañana"/"tarde"); es
	// una etiqueta de negocio, no tiene lógica propia en esta capa.
	private String block_group;

	private String label;

	// LocalTime (java.time), no java.sql.Time: Hibernate 6 lo mapea nativo a
	// la columna "time" de Postgres, y Jackson lo serializa como "HH:mm:ss"
	// sin configuración extra (spring-boot-starter-web ya trae el módulo
	// jackson-datatype-jsr310 de forma transitiva).
	private LocalTime start_time;

	private LocalTime end_time;

	// A diferencia de Venue/Court, la tabla time_block (ver sportbooking.sql)
	// no tiene created_at ni is_active, así que no hace falta setear nada a
	// mano en el constructor de alta más allá de lo que manda el cliente.
	public TimeBlock(Venue venue, String block_group, String label, LocalTime start_time, LocalTime end_time) {
		this.venue = venue;
		this.block_group = block_group;
		this.label = label;
		this.start_time = start_time;
		this.end_time = end_time;
	}

}
