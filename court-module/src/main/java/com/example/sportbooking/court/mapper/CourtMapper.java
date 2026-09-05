package com.example.sportbooking.court.mapper;

import org.mapstruct.Mapper;

import com.example.sportbooking.court.dto.CourtResponseDTO;
import com.example.sportbooking.court.entity.Court;
import com.example.sportbooking.venue.mapper.VenueMapper;

// uses = VenueMapper.class: para mapear el campo "venue" (tipo Venue en la
// entity, tipo VenueResponseDTO en el DTO), MapStruct necesita saber cómo
// convertir un Venue en un VenueResponseDTO. En vez de repetir esa lógica
// acá, delega en VenueMapper.toResponseDTO(...), que ya existe en
// venue-module. Este es el patrón que se repite en cascada en el resto de
// los mappers (TimeBlockMapper, CourtScheduleMapper, ReservationMapper):
// cada mapper de "hoja" se reutiliza en los mappers de las entities que lo
// referencian, igual que las FKs se reutilizan en las relaciones @ManyToOne.
@Mapper(componentModel = "spring", uses = VenueMapper.class)
public interface CourtMapper {

	CourtResponseDTO toResponseDTO(Court court);

}
