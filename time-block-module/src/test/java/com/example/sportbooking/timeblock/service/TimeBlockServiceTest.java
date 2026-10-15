package com.example.sportbooking.timeblock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.sportbooking.timeblock.dto.TimeBlockDTO;
import com.example.sportbooking.timeblock.entity.TimeBlock;
import com.example.sportbooking.timeblock.exception.TimeBlockNotFoundException;
import com.example.sportbooking.timeblock.repository.TimeBlockRepository;
import com.example.sportbooking.venue.entity.Venue;
import com.example.sportbooking.venue.exception.VenueNotFoundException;
import com.example.sportbooking.venue.repository.VenueRepository;

@ExtendWith(MockitoExtension.class)
class TimeBlockServiceTest {

    @Mock
    private TimeBlockRepository timeBlockRepository;

    @Mock
    private VenueRepository venueRepository;

    @InjectMocks
    private TimeBlockService timeBlockService;

    @Test
    void createRejectsMissingVenueId() {
        assertThatThrownBy(() -> timeBlockService.create(
                new TimeBlockDTO(null, "manana", "08-09", LocalTime.of(8, 0), LocalTime.of(9, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("venue_id");
    }

    @Test
    void createRejectsMissingTimes() {
        assertThatThrownBy(() -> timeBlockService.create(new TimeBlockDTO(1L, "manana", "x", null, LocalTime.of(9, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start_time");
    }

    @Test
    void createRejectsRangeWhereEndIsNotAfterStart() {
        assertThatThrownBy(() -> timeBlockService.create(
                new TimeBlockDTO(1L, "manana", "x", LocalTime.of(9, 0), LocalTime.of(9, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posterior");
    }

    @Test
    void createThrowsWhenVenueDoesNotExist() {
        when(venueRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> timeBlockService.create(
                new TimeBlockDTO(2L, "manana", "08-09", LocalTime.of(8, 0), LocalTime.of(9, 0))))
                .isInstanceOf(VenueNotFoundException.class);
    }

    @Test
    void createPersistsTimeBlock() {
        Venue venue = Venue.builder().id(1L).name("Club").build();
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(timeBlockRepository.save(any(TimeBlock.class))).thenAnswer(inv -> inv.getArgument(0));

        TimeBlock created = timeBlockService.create(
                new TimeBlockDTO(1L, "manana", "08-09", LocalTime.of(8, 0), LocalTime.of(9, 0)));

        assertThat(created.getStart_time()).isEqualTo(LocalTime.of(8, 0));
        assertThat(created.getVenue()).isEqualTo(venue);
    }

    @Test
    void updateRevalidatesRangeAgainstFinalState() {
        TimeBlock existing = TimeBlock.builder().id(4L).start_time(LocalTime.of(8, 0)).end_time(LocalTime.of(9, 0))
                .build();
        when(timeBlockRepository.findById(4L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> timeBlockService.update(4L,
                new TimeBlockDTO(null, null, null, null, LocalTime.of(7, 30))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posterior");
        verify(timeBlockRepository, never()).save(any());
    }

    @Test
    void updateIsPartial() {
        TimeBlock existing = TimeBlock.builder().id(4L).block_group("manana").label("08-09")
                .start_time(LocalTime.of(8, 0)).end_time(LocalTime.of(9, 0)).build();
        when(timeBlockRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(timeBlockRepository.save(any(TimeBlock.class))).thenAnswer(inv -> inv.getArgument(0));

        TimeBlock updated = timeBlockService.update(4L, new TimeBlockDTO(null, null, "renombrada", null, null));

        assertThat(updated.getLabel()).isEqualTo("renombrada");
        assertThat(updated.getStart_time()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(timeBlockRepository.existsById(4L)).thenReturn(false);

        assertThatThrownBy(() -> timeBlockService.delete(4L)).isInstanceOf(TimeBlockNotFoundException.class);
    }

}
