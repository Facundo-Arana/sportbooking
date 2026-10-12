package com.example.sportbooking.config;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.sportbooking.court.entity.Court;
import com.example.sportbooking.court.repository.CourtRepository;
import com.example.sportbooking.courtschedule.entity.CourtSchedule;
import com.example.sportbooking.reservation.entity.Reservation;
import com.example.sportbooking.reservation.repository.ReservationRepository;
import com.example.sportbooking.courtschedule.repository.CourtScheduleRepository;
import com.example.sportbooking.timeblock.entity.TimeBlock;
import com.example.sportbooking.timeblock.repository.TimeBlockRepository;
import com.example.sportbooking.user.entity.Role;
import com.example.sportbooking.user.entity.User;
import com.example.sportbooking.user.entity.UserGlobalRole;
import com.example.sportbooking.user.repository.RoleRepository;
import com.example.sportbooking.user.repository.UserRepository;
import com.example.sportbooking.venue.entity.Venue;
import com.example.sportbooking.venue.repository.VenueRepository;

// Seed de datos SOLO con el perfil "dev" (mvnw spring-boot:run -Dspring-boot.run.profiles=dev
// o SPRING_PROFILES_ACTIVE=dev). Reemplaza el "insertar el ADMIN a mano" que
// describia la bitacora: sin perfil dev no corre nada.
//
// Por que ApplicationRunner y no data.sql: un data.sql pelea con ddl-auto=update
// por el orden de ejecucion y bypasea los repositories/encoder. Aca se usan
// los repositories reales y el PasswordEncoder, y el alta de reservas se hace
// directo (no via ReservationService.create, que ahora exige SecurityContext
// por la regla "propiedad derivada del token" -- en un runner no hay request).
//
// Idempotente: si ya hay venues, no siembra de nuevo.
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final VenueRepository venueRepository;
	private final CourtRepository courtRepository;
	private final TimeBlockRepository timeBlockRepository;
	private final CourtScheduleRepository courtScheduleRepository;
	private final ReservationRepository reservationRepository;
	private final PasswordEncoder passwordEncoder;

	public DevDataSeeder(UserRepository userRepository, RoleRepository roleRepository, VenueRepository venueRepository,
			CourtRepository courtRepository, TimeBlockRepository timeBlockRepository,
			CourtScheduleRepository courtScheduleRepository, ReservationRepository reservationRepository,
			PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.venueRepository = venueRepository;
		this.courtRepository = courtRepository;
		this.timeBlockRepository = timeBlockRepository;
		this.courtScheduleRepository = courtScheduleRepository;
		this.reservationRepository = reservationRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (venueRepository.count() > 0) {
			return; // ya hay datos (segundo arranque): no duplicar
		}

		// 1) Rol + usuario ADMIN. La password va hasheada con el mismo
		// PasswordEncoder del login (BCrypt); por eso no puede ser SQL plano.
		Role adminRole = roleRepository.save(Role.builder().name("ADMIN").build());

		User admin = new User("admin", "admin@sportbooking.dev", passwordEncoder.encode("admin123"), "Administrador");
		// User.globalRoles tiene cascade=ALL: agregar el link y guardar el User
		// persiste tambien el UserGlobalRole (no existe un UserGlobalRoleRepository).
		UserGlobalRole link = UserGlobalRole.builder().role(adminRole).build();
		link.setUser(admin);
		admin.getGlobalRoles().add(link);
		userRepository.save(admin);

		// 2) Sede + canchas.
		Venue venue = venueRepository.save(new Venue("Club Demo", "Av. Siempre Viva 742", "555-0100"));

		Court court1 = courtRepository.save(new Court(venue, "Cancha 1", "futbol", true, "sintetico", 10));
		Court court2 = courtRepository.save(new Court(venue, "Cancha 2", "padel", false, "cemento", 4));

		// 3) Franjas horarias (time_block) de la sede.
		List<TimeBlock> blocks = timeBlockRepository.saveAll(List.of(
				new TimeBlock(venue, "manana", "08:00-09:00", LocalTime.of(8, 0), LocalTime.of(9, 0)),
				new TimeBlock(venue, "manana", "09:00-10:00", LocalTime.of(9, 0), LocalTime.of(10, 0)),
				new TimeBlock(venue, "tarde", "18:00-19:00", LocalTime.of(18, 0), LocalTime.of(19, 0)),
				new TimeBlock(venue, "tarde", "19:00-20:00", LocalTime.of(19, 0), LocalTime.of(20, 0))));

		// 4) Calendario: Cancha 1 habilita las 4 franjas de lunes a sabado;
		// Cancha 2 solo las dos primeras de lunes a viernes. day_of_week:
		// 1=lunes..6=sabado (0=domingo no se habilita).
		for (short day = 1; day <= 6; day++) {
			for (TimeBlock block : blocks) {
				courtScheduleRepository.save(new CourtSchedule(court1, block, day, "Seed dev"));
			}
		}
		for (short day = 1; day <= 5; day++) {
			courtScheduleRepository.save(new CourtSchedule(court2, blocks.get(0), day, "Seed dev"));
			courtScheduleRepository.save(new CourtSchedule(court2, blocks.get(1), day, "Seed dev"));
		}

		// 5) Reservas de ejemplo en Cancha 1, el proximo lunes (dia habilitado
		// garantizado): una activa (ocupa la franja) y una cancelada (la libera).
		LocalDate monday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

		reservationRepository.save(new Reservation(monday, court1, blocks.get(0), admin, null, null));

		Reservation cancelled = new Reservation(monday, court1, blocks.get(1), admin, null, "Cliente invitado");
		cancelled.setStatus("CANCELLED");
		reservationRepository.save(cancelled);
	}

}
