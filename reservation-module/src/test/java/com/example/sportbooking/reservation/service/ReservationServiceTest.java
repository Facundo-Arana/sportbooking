package com.example.sportbooking.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.sportbooking.court.entity.Court;
import com.example.sportbooking.court.repository.CourtRepository;
import com.example.sportbooking.courtschedule.service.CourtScheduleService;
import com.example.sportbooking.reservation.dto.ReservationDTO;
import com.example.sportbooking.reservation.entity.Reservation;
import com.example.sportbooking.reservation.repository.ReservationRepository;
import com.example.sportbooking.timeblock.entity.TimeBlock;
import com.example.sportbooking.timeblock.repository.TimeBlockRepository;
import com.example.sportbooking.user.entity.User;
import com.example.sportbooking.user.repository.UserRepository;
import com.example.sportbooking.venue.entity.Venue;

/**
 * Unit test de {@link ReservationService} con Mockito (sin Spring ni base).
 * Cubre las reglas que la spec reservation-booking documenta: ownership
 * derivado del principal, calendario, solapamiento, venue inmutable, update
 * parcial y validacion de status/payment_status.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private CourtRepository courtRepository;

    @Mock
    private TimeBlockRepository timeBlockRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CourtScheduleService courtScheduleService;

    @InjectMocks
    private ReservationService reservationService;

    private Venue venue;
    private Court court;
    private TimeBlock timeBlock;
    private User client;

    @BeforeEach
    void setUp() {
        venue = Venue.builder().id(1L).name("Club").build();
        court = Court.builder().id(10L).venue(venue).build();
        timeBlock = TimeBlock.builder().id(20L).venue(venue).start_time(LocalTime.of(8, 0)).end_time(LocalTime.of(9, 0)).build();
        client = User.builder().id(30L).username("client").email("client@test.com").build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    private void stubReservable(boolean reservable) {
        when(courtScheduleService.isReservable(10L, 20L, (short) 1)).thenReturn(reservable);
    }

    private void stubCourtAndBlock() {
        when(courtRepository.findById(10L)).thenReturn(java.util.Optional.of(court));
        when(timeBlockRepository.findById(20L)).thenReturn(java.util.Optional.of(timeBlock));
    }

    // --- create -----------------------------------------------------------------

    @Test
    void createRejectsMissingDateCourtAndTimeBlock() {
        assertThatThrownBy(() -> reservationService.create(new ReservationDTO(null, 10L, 20L, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");
        assertThatThrownBy(() -> reservationService.create(new ReservationDTO(MONDAY, null, 20L, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("court_id");
        assertThatThrownBy(() -> reservationService.create(new ReservationDTO(MONDAY, 10L, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("time_block_id");
    }

    @Test
    void createDerivesOwnerFromAuthenticatedPrincipal() {
        stubCourtAndBlock();
        stubReservable(true);
        authenticateAs("client@test.com");
        when(userRepository.findByEmail("client@test.com")).thenReturn(java.util.Optional.of(client));
        when(reservationRepository.existsByCourt_IdAndTimeBlock_IdAndDateAndStatusNot(10L, 20L, MONDAY, "CANCELLED"))
                .thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        Reservation created = reservationService.create(new ReservationDTO(MONDAY, 10L, 20L, null, null));

        assertThat(created.getReservedByUser()).isEqualTo(client);
        assertThat(created.getStatus()).isEqualTo("PENDING");
        assertThat(created.getPayment_status()).isEqualTo("PENDING");
    }

    @Test
    void createKeepsAuthenticatedUserAsOwnerEvenWhenBodyTargetsAnotherUser() {
        User other = User.builder().id(40L).username("other").email("other@test.com").build();
        stubCourtAndBlock();
        stubReservable(true);
        authenticateAs("client@test.com");
        when(userRepository.findByEmail("client@test.com")).thenReturn(java.util.Optional.of(client));
        when(userRepository.findById(40L)).thenReturn(java.util.Optional.of(other));
        when(reservationRepository.existsByCourt_IdAndTimeBlock_IdAndDateAndStatusNot(10L, 20L, MONDAY, "CANCELLED"))
                .thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        Reservation created = reservationService.create(new ReservationDTO(MONDAY, 10L, 20L, 40L, null));

        assertThat(created.getReservedByUser()).isEqualTo(client);
        assertThat(created.getReservedForUser()).isEqualTo(other);
    }

    @Test
    void createRejectsSlotOutsideCalendar() {
        stubCourtAndBlock();
        stubReservable(false);

        assertThatThrownBy(() -> reservationService.create(new ReservationDTO(MONDAY, 10L, 20L, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("calendario");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createRejectsOverlappingActiveReservation() {
        stubCourtAndBlock();
        stubReservable(true);
        authenticateAs("client@test.com");
        when(userRepository.findByEmail("client@test.com")).thenReturn(java.util.Optional.of(client));
        when(reservationRepository.existsByCourt_IdAndTimeBlock_IdAndDateAndStatusNot(10L, 20L, MONDAY, "CANCELLED"))
                .thenReturn(true);

        assertThatThrownBy(() -> reservationService.create(new ReservationDTO(MONDAY, 10L, 20L, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reserva activa");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createRejectsBothReservedForUserAndReservedForName() {
        assertThatThrownBy(() -> reservationService.create(new ReservationDTO(MONDAY, 10L, 20L, 40L, "Invitado")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("excluyentes");
    }

    // --- update -----------------------------------------------------------------

    private Reservation existingReservation() {
        return Reservation.builder().id(50L).date(MONDAY).court(court).timeBlock(timeBlock)
                .reservedByUser(client).status("PENDING").payment_status("PENDING").build();
    }

    @Test
    void updateRejectsMovingReservationToAnotherVenue() {
        Venue otherVenue = Venue.builder().id(2L).name("Otro").build();
        Court otherCourt = Court.builder().id(11L).venue(otherVenue).build();
        when(reservationRepository.findById(50L)).thenReturn(java.util.Optional.of(existingReservation()));
        when(courtRepository.findById(11L)).thenReturn(java.util.Optional.of(otherCourt));

        assertThatThrownBy(() -> reservationService.update(50L, new ReservationDTO(null, 11L, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("otro venue");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void updateIsPartialAndRevalidatesCalendarAgainstFinalState() {
        Reservation reservation = existingReservation();
        when(reservationRepository.findById(50L)).thenReturn(java.util.Optional.of(reservation));
        when(courtScheduleService.isReservable(10L, 20L, (short) 1)).thenReturn(true);
        when(reservationRepository.existsByCourt_IdAndTimeBlock_IdAndDateAndStatusNotAndIdNot(
                10L, 20L, MONDAY, "CANCELLED", 50L)).thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        Reservation updated = reservationService.update(50L, new ReservationDTO(null, null, null, null, "Invitado"));

        assertThat(updated.getReserved_for_name()).isEqualTo("Invitado");
        assertThat(updated.getDate()).isEqualTo(MONDAY);
        assertThat(updated.getCourt()).isEqualTo(court);
    }

    @Test
    void updateRejectsReprogrammingToSlotOccupiedByAnotherReservation() {
        Reservation reservation = existingReservation();
        when(reservationRepository.findById(50L)).thenReturn(java.util.Optional.of(reservation));
        when(courtScheduleService.isReservable(10L, 20L, (short) 1)).thenReturn(true);
        when(reservationRepository.existsByCourt_IdAndTimeBlock_IdAndDateAndStatusNotAndIdNot(
                10L, 20L, MONDAY, "CANCELLED", 50L)).thenReturn(true);

        assertThatThrownBy(() -> reservationService.update(50L, new ReservationDTO(MONDAY, null, null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("otra reserva activa");
    }

    // --- status / payment_status / cancel --------------------------------------

    @Test
    void updateStatusRejectsUnknownValue() {
        assertThatThrownBy(() -> reservationService.updateStatus(50L, "BOGUS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
    }

    @Test
    void updateStatusValidatesOverlapAgainstFinalState() {
        Reservation reservation = existingReservation();
        when(reservationRepository.findById(50L)).thenReturn(java.util.Optional.of(reservation));
        when(reservationRepository.existsByCourt_IdAndTimeBlock_IdAndDateAndStatusNotAndIdNot(
                10L, 20L, MONDAY, "CANCELLED", 50L)).thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        Reservation updated = reservationService.updateStatus(50L, "CONFIRMED");

        assertThat(updated.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void updatePaymentStatusRejectsUnknownValue() {
        assertThatThrownBy(() -> reservationService.updatePaymentStatus(50L, "BOGUS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payment_status");
    }

    @Test
    void cancelMarksReservationAsCancelled() {
        Reservation reservation = existingReservation();
        when(reservationRepository.findById(50L)).thenReturn(java.util.Optional.of(reservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        Reservation cancelled = reservationService.cancel(50L);

        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
    }

}
