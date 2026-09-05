package com.example.sportbooking.venue.mapper;

import org.mapstruct.Mapper;

import com.example.sportbooking.venue.dto.VenueResponseDTO;
import com.example.sportbooking.venue.entity.Venue;

// componentModel = "spring": MapStruct genera una implementación (VenueMapperImpl)
// anotada con @Component, así que se puede inyectar como cualquier otro bean de
// Spring (constructor injection en el controller, mismo patrón que los
// Repository/Service). No hace falta escribir el mapeo campo a campo: como los
// nombres de propiedad coinciden 1 a 1 entre Venue y VenueResponseDTO (id, name,
// address, phone, created_at), MapStruct los infiere solo.
@Mapper(componentModel = "spring")
public interface VenueMapper {

	VenueResponseDTO toResponseDTO(Venue venue);

}
