package com.example.sportbooking.court.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.sportbooking.court.dto.CourtDTO;
import com.example.sportbooking.court.entity.Court;
import com.example.sportbooking.court.exception.CourtNotFoundException;
import com.example.sportbooking.court.repository.CourtRepository;
import com.example.sportbooking.venue.entity.Venue;
import com.example.sportbooking.venue.exception.VenueNotFoundException;
import com.example.sportbooking.venue.repository.VenueRepository;

@ExtendWith(MockitoExtension.class)
class CourtServiceTest {

    @Mock
    private CourtRepository courtRepository;

    @Mock
    private VenueRepository venueRepository;

    @InjectMocks
    private CourtService courtService;

    private CourtDTO dto(Long venueId, String name) {
        return new CourtDTO(venueId, name, "futbol", null, "sintetico", 10, null);
    }

    @Test
    void createRejectsMissingName() {
        assertThatThrownBy(() -> courtService.create(dto(1L, " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nombre");
    }

    @Test
    void createRejectsMissingVenueId() {
        assertThatThrownBy(() -> courtService.create(dto(null, "Cancha 1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("venue_id");
    }

    @Test
    void createThrowsWhenVenueDoesNotExist() {
        when(venueRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courtService.create(dto(9L, "Cancha 1")))
                .isInstanceOf(VenueNotFoundException.class);
    }

    @Test
    void createDefaultsIndoorToFalseWhenNull() {
        Venue venue = Venue.builder().id(1L).name("Club").build();
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(courtRepository.save(any(Court.class))).thenAnswer(inv -> inv.getArgument(0));

        Court created = courtService.create(dto(1L, "Cancha 1"));

        assertThat(created.isIndoor()).isFalse();
        assertThat(created.getIs_active()).isTrue();
        assertThat(created.getVenue()).isEqualTo(venue);
    }

    @Test
    void findAllFiltersByVenueWhenProvided() {
        Court court = Court.builder().id(1L).build();
        when(courtRepository.findByVenue_Id(3L)).thenReturn(List.of(court));

        assertThat(courtService.findAll(3L)).containsExactly(court);
        verify(courtRepository, never()).findAll();
    }

    @Test
    void updateRejectsChangingVenueId() {
        Venue venue = Venue.builder().id(1L).name("Club").build();
        Court existing = Court.builder().id(5L).venue(venue).build();
        when(courtRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> courtService.update(5L, dto(2L, "Cancha 1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("venue_id");
        verify(courtRepository, never()).save(any());
    }

    @Test
    void updateAcceptsSameVenueIdAndIsPartial() {
        Venue venue = Venue.builder().id(1L).name("Club").build();
        Court existing = Court.builder().id(5L).venue(venue).name("Vieja").sport_type("futbol").build();
        when(courtRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(courtRepository.save(any(Court.class))).thenAnswer(inv -> inv.getArgument(0));

        Court updated = courtService.update(5L, dto(1L, "Nueva"));

        assertThat(updated.getName()).isEqualTo("Nueva");
        assertThat(updated.getSport_type()).isEqualTo("futbol");
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(courtRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courtService.findById(5L)).isInstanceOf(CourtNotFoundException.class);
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(courtRepository.existsById(5L)).thenReturn(false);

        assertThatThrownBy(() -> courtService.delete(5L)).isInstanceOf(CourtNotFoundException.class);
    }

}
