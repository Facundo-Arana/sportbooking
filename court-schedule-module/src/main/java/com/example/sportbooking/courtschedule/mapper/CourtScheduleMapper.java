package com.example.sportbooking.courtschedule.mapper;

import org.mapstruct.Mapper;

import com.example.sportbooking.court.mapper.CourtMapper;
import com.example.sportbooking.courtschedule.dto.CourtScheduleResponseDTO;
import com.example.sportbooking.courtschedule.entity.CourtSchedule;
import com.example.sportbooking.timeblock.mapper.TimeBlockMapper;

// uses acepta varios mappers a la vez: uno para el campo "court", otro para
// "timeBlock". MapStruct elige el que matchea el tipo de cada campo.
@Mapper(componentModel = "spring", uses = { CourtMapper.class, TimeBlockMapper.class })
public interface CourtScheduleMapper {

	CourtScheduleResponseDTO toResponseDTO(CourtSchedule courtSchedule);

}
