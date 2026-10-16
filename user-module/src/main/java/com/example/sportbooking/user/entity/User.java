package com.example.sportbooking.user.entity;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


// @Table(name = "app_user"): antes era "user", palabra reservada de SQL que
// obligaba a comillas dobles en todo SQL crudo. Las consultas JPQL y los
// derived queries no cambian (usan el nombre de la entity, no de la tabla).
@Entity
@Table(name = "app_user", schema = "platform")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class User {
	
	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	private Long id;
	
	// unique=true + nullable=false: hasta ahora la unicidad de username/email
	// se validaba solo en UserService (findByUsername/findByEmail antes del
	// save), lo cual no protege ante dos requests simultáneos insertando el
	// mismo valor (race condition: ambos pasan la validación de app antes de
	// que cualquiera haga commit). Estas anotaciones hacen que Hibernate
	// agregue el constraint real en la base (ddl-auto=update), que es la
	// única defensa efectiva contra ese caso — ver bitácora punto 7.
	@Column(unique = true, nullable = false)
	private String username;
	
	@Column(unique = true, nullable = false)
	private String email;
	
	// @JsonIgnore: hasta ahora User nunca viajaba completo en una respuesta
	// JSON (registro devuelve solo username, login devuelve un token, /me
	// arma un Map a mano con email+authorities). reservation-module es el
	// primer caso que serializa un User anidado de verdad (Reservation
	// .reservedByUser / .reservedForUser), así que sin este @JsonIgnore el
	// hash de la contraseña se filtraría en la respuesta de /reservations.
	@JsonIgnore
	@Column(nullable = false)
	private String password_hash;
	
	private String name;
	
	// nullable=false: el constructor de alta siempre setea created_at (no se
	// confia en un DEFAULT de columna). documenta el NOT NULL del baseline.
	@Column(nullable = false)
	private Timestamp created_at;
	
	// @JsonManagedReference / @JsonBackReference (ver UserGlobalRole.user):
	// parche de infraestructura de serialización, ver bitácora punto 14. Corta
	// el ciclo User -> globalRoles -> UserGlobalRole -> user -> globalRoles...
	// que Jackson recorre indefinidamente al serializar un User completo (algo
	// que reservation-module es el primer caso en hacer de verdad, ver punto
	// 13). No resuelve el problema de fondo (seguir devolviendo entities JPA
	// en vez de DTOs de respuesta) — eso queda para el próximo paso.
	@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
	@JsonManagedReference
    private Set<UserGlobalRole> globalRoles = new HashSet<>();

	public User(String username, String email, String password_hash, String name) {
		super();
		this.username = username;
		this.email = email;
		this.password_hash = password_hash;
		this.name = name;
		this.created_at = new Timestamp(System.currentTimeMillis());
	}
	
	
}
