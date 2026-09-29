package com.example.sportbooking.uservenuerole.entity;

import com.example.sportbooking.user.entity.Role;
import com.example.sportbooking.user.entity.User;
import com.example.sportbooking.venue.entity.Venue;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// Primer módulo con TRES @ManyToOne hacia módulos externos distintos, igual
// que Reservation, pero acá los tres son relaciones "puras" (no hay ningún
// campo propio más allá de las FKs -- es una tabla de asignación, no un
// recurso con datos propios).
@Entity
@Table(name = "user_venue_role", schema = "platform",
		// Ya existe en sportbooking.sql como "user_venue_role_unique": un mismo
		// usuario no puede tener el mismo rol asignado dos veces en el mismo
		// venue. Se declara acá también para que quede documentado en la
		// entity y para que ddl-auto=update lo recree si alguna vez se arranca
		// contra una base nueva desde cero.
		uniqueConstraints = @UniqueConstraint(name = "user_venue_role_unique",
				columnNames = { "fk_venue_id", "fk_user_id", "fk_role_id" }))
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class UserVenueRole {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "fk_venue_id")
	private Venue venue;

	// fk_venue_id / fk_user_id / fk_role_id: los constraints de FK los
	// administra Hibernate con ddl-auto=update, con nombres generados tipo
	// fk<hash>. No agregarlos a mano con otro nombre: ddl-auto crea un
	// duplicado (mismo caso que Reservation, ver comentario ahí; bitácora
	// punto 26). El @ManyToOne no necesita la FK física para el JOIN.
	@ManyToOne
	@JoinColumn(name = "fk_user_id")
	private User user;

	@ManyToOne
	@JoinColumn(name = "fk_role_id")
	private Role role;

	public UserVenueRole(Venue venue, User user, Role role) {
		this.venue = venue;
		this.user = user;
		this.role = role;
	}

}
