package com.example.sportbooking.user.entity;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
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


@Entity
@Table(name = "user", schema = "platform")
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
	
	private String username;
	
	private String email;
	
	// @JsonIgnore: hasta ahora User nunca viajaba completo en una respuesta
	// JSON (registro devuelve solo username, login devuelve un token, /me
	// arma un Map a mano con email+authorities). reservation-module es el
	// primer caso que serializa un User anidado de verdad (Reservation
	// .reservedByUser / .reservedForUser), así que sin este @JsonIgnore el
	// hash de la contraseña se filtraría en la respuesta de /reservations.
	@JsonIgnore
	private String password_hash;
	
	private String name;
	
	private Timestamp created_at;
	
	private Timestamp updated_at;
	
	@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
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
