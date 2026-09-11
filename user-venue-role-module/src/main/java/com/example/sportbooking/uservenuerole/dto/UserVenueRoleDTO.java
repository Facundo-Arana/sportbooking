package com.example.sportbooking.uservenuerole.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Sin campo "id" a propósito, mismo criterio que el resto de los *DTO de
// entrada: referencias por id a recursos existentes, el cliente no manda
// objetos completos.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVenueRoleDTO {

	private Long venue_id;

	private Long user_id;

	private Long role_id;

}
