package com.example.sportbooking.uservenuerole.mapper;

import org.mapstruct.Mapper;

import com.example.sportbooking.user.mapper.RoleMapper;
import com.example.sportbooking.user.mapper.UserMapper;
import com.example.sportbooking.uservenuerole.dto.UserVenueRoleResponseDTO;
import com.example.sportbooking.uservenuerole.entity.UserVenueRole;
import com.example.sportbooking.venue.mapper.VenueMapper;

// uses = tres mappers a la vez, mismo patrón que CourtScheduleMapper (que
// usaba dos). MapStruct elige el mapper correcto para cada campo según el
// tipo (venue -> VenueMapper, user -> UserMapper, role -> RoleMapper).
@Mapper(componentModel = "spring", uses = { UserMapper.class, VenueMapper.class, RoleMapper.class })
public interface UserVenueRoleMapper {

	UserVenueRoleResponseDTO toResponseDTO(UserVenueRole userVenueRole);

}
