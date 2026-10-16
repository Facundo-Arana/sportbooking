package com.example.sportbooking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.example.sportbooking.reservation.repository.ReservationRepository;
import com.example.sportbooking.support.AbstractSecuredIntegrationTest;
import com.example.sportbooking.support.IntegrationTestFixture;

/**
 * Integracion de concurrencia: escenario "Alta concurrente sobre la misma
 * franja" de la spec {@code reservation-booking} / {@code schema-integrity}.
 *
 * <p>Dos altas sobre la misma (cancha, fecha, franja reservable) se lanzan en
 * simultaneo. El check-then-act de {@code ReservationService} no basta bajo
 * concurrencia, pero el indice unico parcial {@code reservation_active_slot_unique}
 * (V1__baseline.sql) si: a lo sumo una reserva activa persiste y la otra
 * termina en 409 (via {@code IllegalStateException} o
 * {@code DataIntegrityViolationException}, ambos mapeados por
 * {@code GlobalExceptionHandler}).
 */
class ReservationConcurrencyIntegrationTest extends AbstractSecuredIntegrationTest {

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void concurrentCreationsOnSameSlotPersistAtMostOne() throws Exception {
        IntegrationTestFixture.Seed seed = fixture.seed();
        String token = login(IntegrationTestFixture.CLIENT_EMAIL, IntegrationTestFixture.CLIENT_PASSWORD);

        String body = objectMapper.writeValueAsString(Map.of(
                "date", IntegrationTestFixture.MONDAY.toString(),
                "court_id", seed.court1().getId(),
                "time_block_id", seed.block1().getId()));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new CopyOnWriteArrayList<>();
        List<String> outcomes = new CopyOnWriteArrayList<>();

        Callable<Void> attempt = () -> {
            start.await();
            var response = mockMvc.perform(post("/reservations").header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn().getResponse();
            statuses.add(response.getStatus());
            outcomes.add(response.getStatus() + " | " + response.getContentAsString());
            return null;
        };

        try {
            Future<Void> first = pool.submit(attempt);
            Future<Void> second = pool.submit(attempt);
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // Exactamente una alta gana (201) y la otra choca (409).
        assertThat(statuses).as("outcomes=%s", outcomes).containsExactlyInAnyOrder(201, 409);

        long activeReservations = reservationRepository
                .findByCourt_IdAndDate(seed.court1().getId(), IntegrationTestFixture.MONDAY).stream()
                .filter(r -> !"CANCELLED".equals(r.getStatus()))
                .count();
        assertThat(activeReservations).isEqualTo(1);
    }

}
