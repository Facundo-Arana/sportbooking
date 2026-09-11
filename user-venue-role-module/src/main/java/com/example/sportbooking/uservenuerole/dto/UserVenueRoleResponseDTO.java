package com.example.sportbooking.uservenuerole.dto;

import com.example.sportbooking.user.dto.RoleResponseDTO;
import com.example.sportbooking.user.dto.UserResponseDTO;
import com.example.sportbooking.venue.dto.VenueResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVenueRoleResponseDTO {

	private Long id;

	private VenueResponseDTO venue;

	private UserResponseDTO user;

	private RoleResponseDTO role;

}
