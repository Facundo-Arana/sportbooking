package com.example.sportbooking.reservation.mapper;

import org.mapstruct.Mapper;

import com.example.sportbooking.court.mapper.CourtMapper;
import com.example.sportbooking.reservation.dto.ReservationResponseDTO;
import com.example.sportbooking.reservation.entity.Reservation;
import com.example.sportbooking.timeblock.mapper.TimeBlockMapper;
import com.example.sportbooking.user.mapper.UserMapper;

// UserMapper se usa DOS veces acá adentro (reservedByUser y reservedForUser
// son ambos tipo User -> UserResponseDTO): no hace falta declarar el mapper
// dos veces en "uses", MapStruct lo reutiliza para cualquier campo que
// matchee el tipo. Si reservedForUser viene null en la entity, MapStruct
// genera el chequeo de null automáticamente y deja reservedForUser en null
// en el DTO también (no explota con NPE).
@Mapper(componentModel = "spring", uses = { CourtMapper.class, TimeBlockMapper.class, UserMapper.class })
public interface ReservationMapper {

	ReservationResponseDTO toResponseDTO(Reservation reservation);

}
