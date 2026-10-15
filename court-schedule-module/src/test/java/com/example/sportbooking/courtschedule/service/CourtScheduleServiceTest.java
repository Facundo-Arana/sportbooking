package com.example.sportbooking.courtschedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.sportbooking.court.repository.CourtRepository;
import com.example.sportbooking.courtschedule.entity.CourtSchedule;
import com.example.sportbooking.courtschedule.repository.CourtScheduleRepository;
import com.example.sportbooking.timeblock.repository.TimeBlockRepository;

/**
 * Unit test de la API de lectura de {@link CourtScheduleService}: la regla de
 * calendario que consume reservation-module. Sin Spring ni base: se mockea el
 * repositorio (Mockito) y se verifica la delegacion + el cortocircuito de
 * argumentos nulos.
 */
@ExtendWith(MockitoExtension.class)
class CourtScheduleServiceTest {

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @Mock
    private CourtRepository courtRepository;

    @Mock
    private TimeBlockRepository timeBlockRepository;

    @InjectMocks
    private CourtScheduleService courtScheduleService;

    @Test
    void isReservableReturnsFalseWhenAnyArgumentIsNull() {
        assertThat(courtScheduleService.isReservable(null, 2L, (short) 1)).isFalse();
        assertThat(courtScheduleService.isReservable(1L, null, (short) 1)).isFalse();
        assertThat(courtScheduleService.isReservable(1L, 2L, null)).isFalse();
        verifyNoInteractions(courtScheduleRepository);
    }

    @Test
    void isReservableReturnsTrueWhenEnabledSlotExists() {
        when(courtScheduleRepository.existsEnabledSlot(1L, 2L, (short) 3)).thenReturn(true);

        assertThat(courtScheduleService.isReservable(1L, 2L, (short) 3)).isTrue();

        verify(courtScheduleRepository).existsEnabledSlot(1L, 2L, (short) 3);
    }

    @Test
    void isReservableReturnsFalseWhenCalendarDisabledOrMissing() {
        when(courtScheduleRepository.existsEnabledSlot(1L, 2L, (short) 3)).thenReturn(false);

        assertThat(courtScheduleService.isReservable(1L, 2L, (short) 3)).isFalse();
    }

    @Test
    void findReservableSlotsDelegatesToRepository() {
        CourtSchedule slot = CourtSchedule.builder().id(9L).day_of_week((short) 5).is_enabled(true).build();
        when(courtScheduleRepository.findEnabledSlots(1L, (short) 5)).thenReturn(List.of(slot));

        List<CourtSchedule> result = courtScheduleService.findReservableSlots(1L, (short) 5);

        assertThat(result).containsExactly(slot);
        verify(courtScheduleRepository).findEnabledSlots(1L, (short) 5);
    }

}
