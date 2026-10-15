package com.example.sportbooking;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import com.example.sportbooking.court.repository.CourtRepository;
import com.example.sportbooking.courtschedule.repository.CourtScheduleRepository;
import com.example.sportbooking.reservation.repository.ReservationRepository;
import com.example.sportbooking.support.IntegrationTestFixture;
import com.example.sportbooking.timeblock.repository.TimeBlockRepository;
import com.example.sportbooking.user.repository.RoleRepository;
import com.example.sportbooking.user.repository.UserRepository;
import com.example.sportbooking.uservenuerole.repository.UserVenueRoleRepository;
import com.example.sportbooking.venue.repository.VenueRepository;

/**
 * Clase base de los tests de integracion (unico modulo con
 * {@code @SpringBootApplication}, por eso viven en {@code app}).
 *
 * <p>Patron <em>singleton container</em> (D4 del design): el
 * {@link PostgreSQLContainer} se declara en un campo estatico y se arranca
 * UNA vez por JVM, asi que se reusa entre todas las clases de test. No se usa
 * {@code @Testcontainers}/{@code @Container} a proposito: esa extension
 * arranca y <b>detiene</b> el container por clase, lo que mataria el
 * container compartido despues de la primera clase. Testcontainers registra
 * un shutdown hook que lo apaga al terminar la JVM.
 *
 * <p>{@code @ServiceConnection} hace que Spring Boot derive el
 * {@code ConnectionDetails} del container y autoconfigure el DataSource con
 * precedencia sobre {@code application.properties}: el Postgres nativo de dev
 * no se toca y no hay url/user/password hardcodeados (D3).
 *
 * <p>{@code @ActiveProfiles("test")} activa {@code application-test.properties}
 * (create-drop sobre el schema {@code platform}). Cada test arranca con la base
 * limpia: {@link #setUpFixture()} borra y reconstruye el {@code fixture}.
 * {@code DevDataSeeder} es {@code @Profile("dev")} y no corre aca.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private CourtRepository courtRepository;
    @Autowired
    private TimeBlockRepository timeBlockRepository;
    @Autowired
    private CourtScheduleRepository courtScheduleRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private UserVenueRoleRepository userVenueRoleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Fixture de datos; la subclase lo siembra con {@code fixture.seed()}. */
    protected IntegrationTestFixture fixture;

    @BeforeEach
    void setUpFixture() {
        fixture = new IntegrationTestFixture(userRepository, roleRepository, venueRepository, courtRepository,
                timeBlockRepository, courtScheduleRepository, reservationRepository, userVenueRoleRepository,
                passwordEncoder);
        fixture.clean();
    }

}
