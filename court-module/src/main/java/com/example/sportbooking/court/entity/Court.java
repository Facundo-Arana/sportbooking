package com.example.sportbooking.court.entity;

import java.sql.Timestamp;

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
@Table(name = "court", schema = "platform")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class Court {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Mismo patrón que UserGlobalRole.user / UserGlobalRole.role: el campo es
	// el objeto relacionado (Venue), y @JoinColumn apunta a la columna FK real
	// (fk_venue_id) que ya existe en la tabla. Hibernate resuelve el JOIN solo.
	@ManyToOne
	@JoinColumn(name = "fk_venue_id")
	private Venue venue;

	private String name;

	private String sport_type;

	private boolean indoor;

	private String surface_type;

	private Integer max_players;

	private Timestamp created_at;

	private Boolean is_active;

	// Constructor de alta: mismo criterio que Venue (ver bitácora, punto 5) —
	// seteamos created_at e is_active acá en vez de confiar en los DEFAULT de
	// Postgres. Si estos campos quedaran en null, Hibernate mandaría un
	// INSERT con esos valores explícitos en NULL, pisando el default de la
	// columna (CURRENT_TIMESTAMP y true respectivamente).
	public Court(Venue venue, String name, String sport_type, boolean indoor, String surface_type,
			Integer max_players) {
		this.venue = venue;
		this.name = name;
		this.sport_type = sport_type;
		this.indoor = indoor;
		this.surface_type = surface_type;
		this.max_players = max_players;
		this.created_at = new Timestamp(System.currentTimeMillis());
		this.is_active = true;
	}

}
