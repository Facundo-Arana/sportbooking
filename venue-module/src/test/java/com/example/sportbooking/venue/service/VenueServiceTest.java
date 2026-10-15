package com.example.sportbooking.venue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.sportbooking.venue.dto.VenueDTO;
import com.example.sportbooking.venue.entity.Venue;
import com.example.sportbooking.venue.exception.VenueNotFoundException;
import com.example.sportbooking.venue.repository.VenueRepository;

@ExtendWith(MockitoExtension.class)
class VenueServiceTest {

    @Mock
    private VenueRepository venueRepository;

    @InjectMocks
    private VenueService venueService;

    @Test
    void createRejectsBlankName() {
        assertThatThrownBy(() -> venueService.create(new VenueDTO("  ", "addr", "123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nombre");
        verify(venueRepository, never()).save(any());
    }

    @Test
    void createPersistsVenue() {
        when(venueRepository.save(any(Venue.class))).thenAnswer(inv -> inv.getArgument(0));

        Venue created = venueService.create(new VenueDTO("Club", "Av 1", "555"));

        assertThat(created.getName()).isEqualTo("Club");
        assertThat(created.getAddress()).isEqualTo("Av 1");
        assertThat(created.getCreated_at()).isNotNull();
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(venueRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> venueService.findById(7L)).isInstanceOf(VenueNotFoundException.class);
    }

    @Test
    void updateIsPartial() {
        Venue existing = Venue.builder().id(1L).name("Old").address("A").phone("P").build();
        when(venueRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(venueRepository.save(any(Venue.class))).thenAnswer(inv -> inv.getArgument(0));

        Venue updated = venueService.update(1L, new VenueDTO("New", null, null));

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getAddress()).isEqualTo("A");
        assertThat(updated.getPhone()).isEqualTo("P");
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(venueRepository.existsById(7L)).thenReturn(false);

        assertThatThrownBy(() -> venueService.delete(7L)).isInstanceOf(VenueNotFoundException.class);
        verify(venueRepository, never()).deleteById(any());
    }

}
