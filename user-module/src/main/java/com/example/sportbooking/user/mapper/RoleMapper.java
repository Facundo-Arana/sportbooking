package com.example.sportbooking.user.mapper;

import org.mapstruct.Mapper;

import com.example.sportbooking.user.dto.RoleResponseDTO;
import com.example.sportbooking.user.entity.Role;

@Mapper(componentModel = "spring")
public interface RoleMapper {

	RoleResponseDTO toResponseDTO(Role role);

}
