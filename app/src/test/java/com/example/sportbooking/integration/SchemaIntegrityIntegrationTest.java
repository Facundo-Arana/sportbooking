package com.example.sportbooking.integration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.sportbooking.reservation.entity.Reservation;
import com.example.sportbooking.AbstractIntegrationTest;
import com.example.sportbooking.reservation.repository.ReservationRepository;
import com.example.sportbooking.support.IntegrationTestFixture;

/**
 * Integracion del endurecimiento del esquema (spec {@code schema-integrity}):
 * la base —no la capa de servicio— rechaza datos invalidos.
 *
 * <p>Se insertan filas con SQL directo ({@link JdbcTemplate}) para saltear las
 * validaciones de aplicacion y comprobar que el {@code NOT NULL}, los {@code
 * CHECK} y el indice unico parcial del baseline {@code V1} hacen su trabajo.
 */
class SchemaIntegrityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReservationRepository reservationRepository;

    // --- Requisito: Columnas obligatorias no nulas (2.2) ---------------------

    @Test
    void courtWithoutVenueIsRejectedByDatabase() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO platform.court (fk_venue_id, name, indoor, created_at, is_active) "
                        + "VALUES (?, ?, ?, now(), true)",
                null, "Cancha sin sede", true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reservationWithoutStatusIsRejectedByDatabase() {
        IntegrationTestFixture.Seed seed = fixture.seed();
        assertThatThrownBy(() -> insertReservation(seed, null, "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- Requisito: Dominios controlados por CHECK (2.3) ---------------------

    @Test
    void reservationWithInvalidStatusIsRejectedByDatabase() {
        IntegrationTestFixture.Seed seed = fixture.seed();
        assertThatThrownBy(() -> insertReservation(seed, "BOGUS", "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reservationWithInvalidPaymentStatusIsRejectedByDatabase() {
        IntegrationTestFixture.Seed seed = fixture.seed();
        assertThatThrownBy(() -> insertReservation(seed, "PENDING", "BOGUS"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reservationWithValidDomainValuesPersists() {
        IntegrationTestFixture.Seed seed = fixture.seed();
        assertThatCode(() -> insertReservation(seed, "CONFIRMED", "PAID"))
                .doesNotThrowAnyException();
    }

    @Test
    void timeBlockWithInvertedRangeIsRejectedByDatabase() {
        IntegrationTestFixture.Seed seed = fixture.seed();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO platform.time_block (fk_venue_id, block_group, label, start_time, end_time) "
                        + "VALUES (?, ?, ?, ?, ?)",
                seed.venue().getId(), "test", "10:00-09:00", Time.valueOf("10:00:00"), Time.valueOf("09:00:00")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void timeBlockWithValidRangePersists() {
        IntegrationTestFixture.Seed seed = fixture.seed();
        assertThatCode(() -> jdbcTemplate.update(
                "INSERT INTO platform.time_block (fk_venue_id, block_group, label, start_time, end_time) "
                        + "VALUES (?, ?, ?, ?, ?)",
                seed.venue().getId(), "test", "10:00-11:00", Time.valueOf("10:00:00"), Time.valueOf("11:00:00")))
                .doesNotThrowAnyException();
    }

    @Test
    void courtScheduleWithDayOfWeekOutOfRangeIsRejectedByDatabase() {
        IntegrationTestFixture.Seed seed = fixture.seed();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO platform.court_schedule (fk_court_id, fk_time_block_id, day_of_week, is_enabled) "
                        + "VALUES (?, ?, ?, true)",
                seed.court1().getId(), seed.block1().getId(), (short) 9))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- Requisito: Sin solapamiento de reservas activas (2.4) ---------------

    @Test
    void twoActiveReservationsOnSameSlotCannotCoexist() {
        IntegrationTestFixture.Seed seed = fixture.seed();

        reservationRepository.save(newReservation(seed, "PENDING"));

        assertThatThrownBy(() -> reservationRepository.save(newReservation(seed, "CONFIRMED")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cancelledReservationDoesNotBlockNewReservationOnSameSlot() {
        IntegrationTestFixture.Seed seed = fixture.seed();

        Reservation active = reservationRepository.save(newReservation(seed, "PENDING"));
        active.setStatus("CANCELLED");
        reservationRepository.save(active);

        // La cancelada queda fuera del indice parcial: la franja se puede reusar.
        assertThatCode(() -> reservationRepository.save(newReservation(seed, "PENDING")))
                .doesNotThrowAnyException();
    }

    // --- Helpers -------------------------------------------------------------

    private Reservation newReservation(IntegrationTestFixture.Seed seed, String status) {
        Reservation reservation = new Reservation(LocalDate.parse(IntegrationTestFixture.MONDAY.toString()),
                seed.court1(), seed.block1(), seed.client(), null, null);
        reservation.setStatus(status);
        return reservation;
    }

    private void insertReservation(IntegrationTestFixture.Seed seed, String status, String paymentStatus) {
        jdbcTemplate.update(
                "INSERT INTO platform.reservation "
                        + "(date, fk_court_id, fk_time_block_id, fk_reserved_by_user_id, status, payment_status, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, now())",
                Date.valueOf(IntegrationTestFixture.MONDAY), seed.court1().getId(), seed.block1().getId(),
                seed.client().getId(), status, paymentStatus);
    }

}
