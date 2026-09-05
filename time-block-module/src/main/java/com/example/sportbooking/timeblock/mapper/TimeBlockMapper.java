package com.example.sportbooking.timeblock.mapper;

import org.mapstruct.Mapper;

import com.example.sportbooking.timeblock.dto.TimeBlockResponseDTO;
import com.example.sportbooking.timeblock.entity.TimeBlock;
import com.example.sportbooking.venue.mapper.VenueMapper;

@Mapper(componentModel = "spring", uses = VenueMapper.class)
public interface TimeBlockMapper {

	TimeBlockResponseDTO toResponseDTO(TimeBlock timeBlock);

}
