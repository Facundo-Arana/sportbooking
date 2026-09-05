package com.example.sportbooking.user.entity;


import com.fasterxml.jackson.annotation.JsonBackReference;

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
@Table(name = "user_global_role", schema = "platform")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class UserGlobalRole {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// @JsonBackReference: contraparte de User.globalRoles (@JsonManagedReference).
	// Jackson deja de serializar este campo al recorrer el grafo desde User,
	// lo que corta el ciclo. Si en el futuro se construye user-venue-role-module,
	// UserVenueRole.user va a necesitar la misma anotación (misma bidireccionalidad).
	@ManyToOne
	@JoinColumn(name = "fk_user_id")
	@JsonBackReference
	private User user;

	@ManyToOne
	@JoinColumn(name = "fk_role_id")
	private Role role;

	// Posible campo adicional:
	//private LocalDateTime assignedAt;

}
