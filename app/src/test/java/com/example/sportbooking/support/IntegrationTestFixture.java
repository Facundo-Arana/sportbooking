package com.example.sportbooking.support;

import java.time.LocalTime;

import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.sportbooking.court.entity.Court;
import com.example.sportbooking.court.repository.CourtRepository;
import com.example.sportbooking.courtschedule.entity.CourtSchedule;
import com.example.sportbooking.courtschedule.repository.CourtScheduleRepository;
import com.example.sportbooking.reservation.repository.ReservationRepository;
import com.example.sportbooking.timeblock.entity.TimeBlock;
import com.example.sportbooking.timeblock.repository.TimeBlockRepository;
import com.example.sportbooking.user.entity.Role;
import com.example.sportbooking.user.entity.User;
import com.example.sportbooking.user.entity.UserGlobalRole;
import com.example.sportbooking.user.repository.RoleRepository;
import com.example.sportbooking.user.repository.UserRepository;
import com.example.sportbooking.uservenuerole.repository.UserVenueRoleRepository;
import com.example.sportbooking.venue.entity.Venue;
import com.example.sportbooking.venue.repository.VenueRepository;

/**
 * Fixture de datos de los tests de integracion (D7 del design): cada test
 * arranca de un estado conocido (ADMIN, cliente, sede, canchas, franjas y
 * calendario) porque el container empieza vacio y {@code DevDataSeeder} no
 * corre en el perfil {@code test}.
 *
 * <p>{@link #clean()} borra en orden inverso a las FK para no violar
 * constraints; se ejecuta antes de cada test (ver
 * {@code AbstractIntegrationTest}).
 */
public class IntegrationTestFixture {

    public static final String ADMIN_EMAIL = "admin@sportbooking.dev";
    public static final String ADMIN_PASSWORD = "admin123";
    public static final String CLIENT_EMAIL = "client@sportbooking.dev";
    public static final String CLIENT_PASSWORD = "client123";

    /** 2026-10-12 es lunes (day_of_week = 1, dia con calendario en el seed). */
    public static final java.time.LocalDate MONDAY = java.time.LocalDate.of(2026, 10, 12);
    /** 2026-10-13 es martes (day_of_week = 2, SIN calendario en el seed). */
    public static final java.time.LocalDate TUESDAY = java.time.LocalDate.of(2026, 10, 13);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;
    private final TimeBlockRepository timeBlockRepository;
    private final CourtScheduleRepository courtScheduleRepository;
    private final ReservationRepository reservationRepository;
    private final UserVenueRoleRepository userVenueRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public IntegrationTestFixture(UserRepository userRepository, RoleRepository roleRepository,
            VenueRepository venueRepository, CourtRepository courtRepository, TimeBlockRepository timeBlockRepository,
            CourtScheduleRepository courtScheduleRepository, ReservationRepository reservationRepository,
            UserVenueRoleRepository userVenueRoleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.venueRepository = venueRepository;
        this.courtRepository = courtRepository;
        this.timeBlockRepository = timeBlockRepository;
        this.courtScheduleRepository = courtScheduleRepository;
        this.reservationRepository = reservationRepository;
        this.userVenueRoleRepository = userVenueRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Borra todo respetando el orden de las FK (hijos primero). */
    public void clean() {
        reservationRepository.deleteAll();
        userVenueRoleRepository.deleteAll();
        courtScheduleRepository.deleteAll();
        courtRepository.deleteAll();
        timeBlockRepository.deleteAll();
        venueRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
    }

    public Seed seed() {
        Role adminRole = roleRepository.save(Role.builder().name("ADMIN").build());
        Role clientRole = roleRepository.save(Role.builder().name("CLIENT").build());

        User admin = new User("admin", ADMIN_EMAIL, passwordEncoder.encode(ADMIN_PASSWORD), "Administrador");
        UserGlobalRole adminLink = UserGlobalRole.builder().role(adminRole).build();
        adminLink.setUser(admin);
        admin.getGlobalRoles().add(adminLink);
        userRepository.save(admin);

        User client = new User("client", CLIENT_EMAIL, passwordEncoder.encode(CLIENT_PASSWORD), "Cliente");
        userRepository.save(client);

        Venue venue = venueRepository.save(new Venue("Club Test", "Calle 123", "555-0000"));
        Court court1 = courtRepository.save(new Court(venue, "Cancha 1", "futbol", true, "sintetico", 10));
        Court court2 = courtRepository.save(new Court(venue, "Cancha 2", "padel", false, "cemento", 4));

        TimeBlock block1 = timeBlockRepository.save(
                new TimeBlock(venue, "manana", "08:00-09:00", LocalTime.of(8, 0), LocalTime.of(9, 0)));
        TimeBlock block2 = timeBlockRepository.save(
                new TimeBlock(venue, "manana", "09:00-10:00", LocalTime.of(9, 0), LocalTime.of(10, 0)));

        // Se guardan en orden invertido a proposito: el endpoint de
        // disponibilidad debe devolverlos ordenados por start_time (block1
        // antes que block2), no por id de insercion.
        courtScheduleRepository.save(new CourtSchedule(court1, block2, (short) 1, "test"));
        courtScheduleRepository.save(new CourtSchedule(court1, block1, (short) 1, "test"));
        courtScheduleRepository.save(new CourtSchedule(court2, block1, (short) 1, "test"));

        return new Seed(admin, client, adminRole, clientRole, venue, court1, court2, block1, block2);
    }

    public record Seed(User admin, User client, Role adminRole, Role clientRole, Venue venue, Court court1,
            Court court2, TimeBlock block1, TimeBlock block2) {
    }

}
