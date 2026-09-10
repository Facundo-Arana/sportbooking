package com.example.sportbooking.venue.entity;

import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "venue", schema = "platform")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class Venue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// nullable=false: analogía con User.email/username (ver bitácora punto 7)
	// — un venue sin nombre no tiene sentido de negocio, pero hasta ahora
	// nada lo impedía a nivel de base.
	@Column(nullable = false)
	private String name;

	private String address;

	private String phone;

	private Timestamp created_at;

	// Constructor de alta: igual que en User, seteamos created_at acá en vez de
	// confiar en el DEFAULT CURRENT_TIMESTAMP de la columna. Si dejáramos el
	// campo en null, Hibernate mandaría un INSERT con created_at = NULL
	// explícito, pisando el default de la base.
	public Venue(String name, String address, String phone) {
		this.name = name;
		this.address = address;
		this.phone = phone;
		this.created_at = new Timestamp(System.currentTimeMillis());
	}

}
