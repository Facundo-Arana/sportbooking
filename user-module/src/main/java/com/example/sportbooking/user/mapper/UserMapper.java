package com.example.sportbooking.user.mapper;

import org.mapstruct.Mapper;

import com.example.sportbooking.user.dto.UserResponseDTO;
import com.example.sportbooking.user.entity.User;

// No hace falta @Mapping(ignore=...) para password_hash ni globalRoles:
// MapStruct solo mapea las propiedades que existen en AMBOS lados (source y
// target) por nombre. Como UserResponseDTO no las tiene, MapStruct ni
// intenta tocarlas — quedan afuera por construcción, no por una exclusión
// explícita que alguien podría borrar sin querer.
@Mapper(componentModel = "spring")
public interface UserMapper {

	UserResponseDTO toResponseDTO(User user);

}
