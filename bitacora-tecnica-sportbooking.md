# Bitácora Técnica — Sportbooking

> Fuente de verdad del estado, las decisiones y la deuda del proyecto. **Versión compacta**: se recortó el narrativo histórico de bugs (queda en el historial de git). Lo que se conserva son el estado actual, las convenciones fijas, los conceptos útiles para entrevista y los pendientes.

---

## Estado actual

- Monolito modular Maven. `sportbooking` raíz = aggregator (`packaging=pom`); `app` = único jar ejecutable y depende de los 8 módulos.
- Dominios implementados, todos con estructura `entity → dto → exception → repository → service → controller → mapper`: `user`, `auth`, `venue`, `court`, `time-block`, `court-schedule`, `user-venue-role`, `reservation`.
- CRUD con alta / listado con filtros / detalle / update parcial / baja. `user-venue-role` **no tiene `PUT`**: su identidad ES el trío de FKs, "editarla" equivale a borrar y crear.
- `reservation` tiene endpoints por intención además del CRUD: `PATCH /reservations/{id}/cancel` (dueño o personal), `.../status` y `.../payment` (solo personal del venue). `PUT` quedó solo para reprogramar (sin `status`/`payment_status`).
- `reservation` además: propiedad derivada del token (no del body), validación contra el calendario de la cancha (`reservation → court-schedule`) y disponibilidad por cancha/fecha (`GET /courts/{id}/availability`).
- Identidad: login y `/auth/me` devuelven perfil (`UserResponseDTO`) + authorities.
- Directorio (panel admin): `GET /users?q=` (`hasRole('ADMIN')`) y `GET /roles` (autenticado) en `user-module`.
- Seguridad: JWT stateless + autorización por rol/venue (`@PreAuthorize` + bean `VenueAuthorization`).
- Respuestas de la API: Response DTOs por entity, mapeados con MapStruct **en el controller**.
- Documentación: OpenAPI/Swagger UI (`/swagger-ui/index.html`) con esquema bearer JWT.
- Datos de desarrollo: `DevDataSeeder` con perfil `dev` (ADMIN + sede + canchas + franjas + calendario + reservas).
- Errores centralizados en `GlobalExceptionHandler` (app-module): 400 / 404 / 409 / 401 / 500.
- **Sin tests automatizados**. Verificación = `mvn compile` + Postman/Swagger.

---

## Convenciones y decisiones fijas

- **Nombres de campos**: snake_case cuando reflejan la columna (`venue_id`, `block_group`, `start_time`, `payment_status`, `created_at`). Es intencional.
- **Tipos**: `LocalTime` para columnas `time`; `Short` para `day_of_week` (smallint, `0`=domingo…`6`=sábado); `LocalDate` para fecha; `Boolean` wrapper en campos opcionales de update parcial (`indoor`, `is_active`, `is_enabled`) para distinguir "no vino" (`null`) de `false`.
- **Alta**: `created_at`, `status`, `payment_status`, `is_active` se setean a mano en el constructor. No confiar en defaults de la columna (Hibernate manda `NULL` explícito y pisa el default de Postgres).
- **Update parcial**: solo se pisan los campos no-null del DTO. Las reglas cruzadas se re-validan contra el **estado final de la entity**, no contra el DTO.
- **`venue_id` inmutable en `PUT`** (Court/TimeBlock/CourtSchedule/Reservation): reenviar el mismo valor = no-op, uno distinto = 400. Mover entre venues = borrar y crear.
- **Excepciones → HTTP** las traduce solo `GlobalExceptionHandler`. En los services: `IllegalArgumentException` → 400 (dato inválido), `*NotFoundException` → 404 (recurso inexistente), `IllegalStateException` → 409 (choca contra el estado actual del sistema). No agregar try/catch en controllers.
- **Controllers**: devuelven Response DTOs (`*Mapper.toResponseDTO`), nunca entities JPA. El mapeo va en el controller; el service devuelve entities.
- **Filtros por relación**: query derivado (`findByVenue_Id`, `findByCourt_Id`), expuesto como `?venueId=` / `?courtId=` opcional.
- **Dependencias entre módulos**: cada módulo depende por `pom.xml` de los que referencia y reutiliza sus `*Repository` / `*NotFoundException` (ej. `ReservationService` usa `CourtRepository`, `TimeBlockRepository`, `UserRepository`).
- **Beans transversales en `app-module`**: `GlobalExceptionHandler` y `VenueAuthorization` viven ahí porque `app` es el único módulo que depende de todos los demás. No moverlos (crearía dependencia circular).
- **Duplicación aceptada**: `validateSameVenue` / `validateVenueUnchanged` están repetidos en dos services a propósito; se extrae recién a la 3ra necesidad real.

---

## Seguridad

- `SecurityConfiguration` (auth-module): JWT stateless; `/auth/**` y preflight `OPTIONS /**` públicos; todo lo demás autenticado.
- `@EnableMethodSecurity` es obligatorio; sin él, `@PreAuthorize` se ignora **en silencio**.
- Autorización de recursos vía `@PreAuthorize` con SpEL que llama al bean `@venueAuthorization` **por nombre** (no por import), así los módulos de dominio no dependen de `app`.
- Regla base: `canManageVenue(venueId)` = el usuario tiene al menos una fila en `user_venue_role` para ese venue (sin distinguir el nombre del rol todavía).
- `canManageReservation`: la puede manejar quien la creó (dueño) **o** quien tiene rol en el venue de la cancha. `canManageReservationVenue`: solo quien tiene rol en el venue (sin dueño), para las acciones de gestión (`PATCH .../status`, `.../payment`). El bypass `hasRole('ADMIN')` se agrega aparte en cada `@PreAuthorize`.
- CORS: origen y métodos configurables por propiedad (`app.cors.allowed-origins`, default `http://localhost:5173`; `app.cors.allowed-methods` incluye `PATCH`). El `CorsConfigurationSource` se engancha dentro de `SecurityConfiguration` (`.cors()`); no declarar un `CorsFilter` suelto.
- Rutas de Swagger UI / OpenAPI (`/swagger-ui/**`, `/v3/api-docs/**`) son `permitAll` (dev/portfolio); los endpoints del API siguen protegidos.
- Ownership derivado del token: el `reservedByUser` de una reserva sale del usuario autenticado, nunca del body (`reserved_by_user_id` eliminado del DTO de alta).
- Defensa en profundidad: `User.password_hash` con `@JsonIgnore`; `User.globalRoles` ↔ `UserGlobalRole.user` con `@JsonManagedReference`/`@JsonBackReference`. Igual, la regla es no exponer entities.

---

## Base de datos

- PostgreSQL local, schema `platform`. Config única en `app/src/main/resources/application.properties`: puerto 8081, `ddl-auto=update`. Los secretos salen de placeholders (`${DB_USERNAME:}`, `${DB_PASSWORD:}`, `${JWT_SECRET:}`) resueltos por **variable de entorno** (prioridad) o por `application-local.properties` (no versionado), importado con `spring.config.import`. El plugin `spring-boot-maven-plugin` corre con `workingDirectory` = root del repo, para que `./application-local.properties` se encuentre siempre.
- `ddl-auto=update` aplica `ALTER TABLE` al arrancar y **falla en silencio** si hay datos que violan un constraint nuevo. No hay Flyway/Liquibase ni scripts versionados.
- Constraints reales vía anotaciones en la entity: `UNIQUE`/`NOT NULL` en `user.email`/`username`/`password_hash` y `venue.name`.
- Los constraints de FK los crea/administra Hibernate con `ddl-auto=update` (nombres generados tipo `fk<hash>`), incluso sobre tablas ya existentes. **No** agregar FK a mano con otro nombre: ddl-auto no reconoce el nombre y crea un duplicado sobre la misma columna (ver punto 26).
- Seed de desarrollo: `DevDataSeeder` (`@Profile("dev")`, idempotente) crea rol+usuario `ADMIN` (password hasheada), sede, canchas, franjas, calendario y reservas. Reemplaza el "insertar el `ADMIN` a mano".

---

## Flujo de trabajo

- El asistente **modifica directamente los archivos del repo**; el usuario testea después con `mvn compile` + Postman. No se entregan listados de archivos ni zips.
- Cada cambio importante actualiza esta bitácora (estado / convenciones / conceptos / pendientes) y viene con la explicación del concepto detrás, para poder defenderlo en entrevistas.
- Proyecto personal de portfolio (no comercial). Claridad por sobre atajos.

---

## Conceptos clave para entrevista

Cada uno salió de un caso real del proyecto.

- **Maven multi-módulo**: aggregator `pom` vs jar ejecutable; `dependencies` entre módulos; component scan por package base compartido (`@SpringBootApplication` en la cima).
- **Servlet Filter vs Spring MVC Interceptor**: OSIV se engancha como interceptor, así que durante la autenticación (filtro) no hay sesión Hibernate → `LazyInitializationException`; se resuelve con `JOIN FETCH`, no cambiando a `EAGER`.
- **Serialización cíclica JPA**: una relación bidireccional genera loop infinito en Jackson; el fix de fondo es no exponer entities (DTOs), no solo anotar `@JsonManagedReference`.
- **Modelo de persistencia vs modelo de API**: mapear en el borde (controller) evita filtrar `password_hash`, no acopla el contrato HTTP al schema, y evita romperlo al cambiar una entity.
- **MapStruct**: genera el mapeo en compilación (no reflection en runtime) → un error de tipos se detecta al compilar.
- **Validación cross-field**: en update parcial hay que validar contra el estado final, no contra el payload. Extendido a validación **entre relaciones** (`validateSameVenue`).
- **Check-then-act**: la validación en service no protege ante concurrencia; hace falta un constraint real en la base. Relacionado: `ddl-auto` no versiona ni falla fuerte → en un proyecto real, Flyway/Liquibase.
- **Autorización pre-ejecución vs campo mutable**: `@PreAuthorize` evalúa el estado previo; si un `PUT` puede cambiar la dimensión contra la que se autoriza (venue), el fix más robusto es volver el campo inmutable.
- **Autorización por campo vs por recurso**: "puede manejar la reserva" (recurso) ≠ "puede cambiar `status`/`payment_status`" (campos). Resuelto con endpoints por intención en vez de validar el body: `PUT` ya no acepta esos campos (mass assignment imposible por construcción) y se agregaron `PATCH .../cancel` (dueño o personal), `.../status` y `.../payment` (solo personal). Es la misma lógica de "hacer estructuralmente imposible el caso" que el `venue_id` inmutable (punto 22). OWASP API3.
- **Ownership derivado del principal, no del body**: la identidad del dueño de un recurso la fija el token (`Authentication.getName()`), nunca el cliente. Aceptarla del payload habilita crear recursos "a nombre de otro" (OWASP API1/API3). El DTO de alta no expone el campo.
- **Read model calculado**: la disponibilidad ("calendario menos reservas") no es ninguna entity; el service la compone y el controller la mapea a DTOs. Distinguir "modelo de persistencia" de "modelo de lectura".
- **Orden de filtros**: un `CorsFilter` suelto corre después de Spring Security; CORS debe integrarse vía `HttpSecurity.cors()`, y el preflight `OPTIONS` requiere `permitAll`.
- **Excepciones de negocio custom**: el service no conoce HTTP; el controller (o `@RestControllerAdvice`) traduce a códigos.
- **`@ControllerAdvice` vs `@RestControllerAdvice`**: el segundo agrega `@ResponseBody` implícito.
- **Tipos que mapean 1:1 con la columna**: `Short` para `smallint`, `LocalTime` para `time` — no el "default cómodo" (`Integer`, `java.sql.Time`).

---

## Índice de puntos referenciados desde comentarios de código

> Los comentarios del código citan la numeración vieja de esta bitácora (antes del recorte). Equivalencias rápidas:

- `3.1` — 403 por un Bearer token viejo en una request "pública"; autenticación (filtro) vs autorización.
- `5` / `9` — criterio de `created_at`/`is_active` seteados a mano en el constructor de alta (Venue/Court).
- `7` — deuda de esquema: constraints, FKs faltantes, credenciales hardcodeadas, `ddl-auto`.
- `13` — `reservation-module`; `status`/`payment_status` como strings validados (no enum todavía).
- `14` — loop de serialización `User` ↔ `UserGlobalRole`; parche `@JsonManagedReference`/`@JsonBackReference`.
- `15.2` — migración de los controllers a Response DTOs.
- `17` — `validateSameVenue` duplicado a propósito; extraer recién a la 3ra vez.
- `19` — constraints `UNIQUE`/`NOT NULL` reales en `user`/`venue`.
- `20` — `GlobalExceptionHandler` centralizado; `UserService` pasó a `IllegalArgumentException`.
- `21` — módulo `user_venue_role` (sin `PUT`); `RoleRepository`/`RoleNotFoundException` en `user-module`.
- `22` — `venue_id` inmutable en `PUT`.
- `23` — `@PreAuthorize` + `VenueAuthorization`.
- `24` — **resuelto**: autorización por campo en reservas. `PUT /reservations/{id}` ya no toca `status`/`payment_status`; se agregaron `PATCH .../cancel` (dueño o personal vía `canManageReservation`), `.../status` y `.../payment` (solo personal vía `canManageReservationVenue`). Archivos: `reservation-module/.../dto/ReservationDTO.java`, `reservation-module/.../dto/ReservationStatusDTO.java` (nuevo), `reservation-module/.../dto/ReservationPaymentDTO.java` (nuevo), `reservation-module/.../service/ReservationService.java`, `reservation-module/.../controller/ReservationController.java`, `app/.../security/VenueAuthorization.java`.
- `25` — **resuelto**: secretos fuera del repo (híbrido archivo-local + env vars). Síntoma: credenciales de Postgres y `jwt.secret` en texto plano y versionados. Causa raíz: valores escritos directo en `application.properties`. Fix: la config commiteada queda con placeholders (`${DB_USERNAME:}`, `${DB_PASSWORD:}`, `${JWT_SECRET:}`, `${DB_URL:...}`); `spring.config.import=optional:file:./application-local.properties,optional:file:../application-local.properties` importa un archivo **no versionado** con los valores locales; las **variables de entorno** del mismo nombre pisan el archivo (mayor precedencia en los `PropertySources`). El `spring-boot-maven-plugin` se corre con `workingDirectory` = root del repo. Se importan las dos rutas (`./` y `../`) porque el *working directory* al lanzar la app no es único: Maven CLI corre en el root, pero Eclipse/IntelliJ al correr la main class (o `java -jar` desde el módulo) corre en `app/`; con `optional:` una ruta que no existe se ignora en silencio y la password queda vacía (síntoma: `SCRAM-based authentication, but no password was provided`). Archivos: `app/src/main/resources/application.properties`, `app/pom.xml`, `.gitignore`, `application-local.properties` (nuevo, ignorado por Git), `AGENTS.md`.
- `26` — **resuelto (el pendiente no era real)**: se creyó que faltaban FKs a nivel de base. Síntoma: se agregaron a mano con `db/migration/V1__add_missing_fk_constraints.sql` las FK de `reservation.fk_reserved_by_user_id` / `fk_reserved_for_user_id` y `user_venue_role.fk_user_id` / `fk_role_id`; en el ERD de DBeaver aparecieron relaciones duplicadas. Causa raíz: esas FK **ya existían**, creadas por Hibernate con `ddl-auto=update` y nombres generados (`fk<hash>`); el script manual las volvió a crear con otro nombre → dos constraints sobre la misma columna. Conclusión: `ddl-auto=update` sí crea FK sobre tablas existentes (contrario a lo que decía la nota vieja); agregarlas a mano con otro nombre solo duplica. Fix: `DROP CONSTRAINT` de los cuatro constraints de nombre descriptivo (se conservan los de Hibernate, que son los que ddl-auto reconoce y no recreará) y eliminación del script. Concepto: no mezclar DDL manual con `ddl-auto`; usar Flyway/Liquibase si se quiere control de constraints. Archivos: `db/migration/V1__add_missing_fk_constraints.sql` (eliminado), `reservation-module/src/main/java/com/example/sportbooking/reservation/entity/Reservation.java`, `user-venue-role-module/src/main/java/com/example/sportbooking/uservenuerole/entity/UserVenueRole.java`, `bitacora-tecnica-sportbooking.md`, `AGENTS.md`.
- `27` — **resuelto**: CORS configurable y con `PATCH`. Síntoma: el preflight `OPTIONS` de los `PATCH /reservations/{id}/cancel|status|payment` no autorizaba el método (y el origen estaba fijado a Angular, no al SPA). Causa raíz: `CorsConfigurations` tenía el origen hardcodeado (`http://localhost:4200`) y `allowedMethods` sin `PATCH`. Fix: externalizar `app.cors.allowed-origins` (default `http://localhost:5173`, dev server de Vite) y `app.cors.allowed-methods` (incluye `PATCH`) como propiedades, con `@Value` + `List<String>` (Spring separa por comas). Archivos: `auth-module/src/main/java/com/example/sportbooking/auth/config/CorsConfigurations.java`, `app/src/main/resources/application.properties`. Concepto: el preflight `OPTIONS` no lleva `Authorization`; el método debe estar en `Access-Control-Allow-Methods` o el browser bloquea el request real. Config por propiedad vs hardcode.
- `28` — **resuelto**: OpenAPI/Swagger UI. Síntoma: el contrato solo era navegable con Postman. Causa raíz: no había dependencia OpenAPI. Fix: `springdoc-openapi-starter-webmvc-ui` 2.7.0 (línea 2.x para Spring Boot 3) en `app`; `permitAll` para `/swagger-ui/**` y `/v3/api-docs/**` en `SecurityConfiguration`; bean `OpenApiConfiguration` que declara el esquema `bearer-jwt` (HTTP bearer, `bearerFormat=JWT`) para el botón "Authorize". Archivos: `app/pom.xml`, `auth-module/src/main/java/com/example/sportbooking/auth/config/SecurityConfiguration.java`, `app/src/main/java/com/example/sportbooking/config/OpenApiConfiguration.java` (nuevo). Concepto: OpenAPI como contrato legible por máquina; por qué las rutas de la UI/JSON deben ser públicas aunque el API siga protegido.
- `29` — **resuelto**: identidad completa en login y `/auth/me`. Síntoma: el login devolvía solo el token y `/auth/me` solo email+authorities; el front no podía saber quién era (ni su `id`) sin un segundo request. Causa raíz: la respuesta no incluía el perfil. Fix: `AuthResponse` pasa a `token + user (UserResponseDTO) + authorities`; nuevo `UserIdentityResponse` (user + authorities) para `/auth/me`; `AuthController` reusa `CustomUserDetails.getUser()` (cargado con `JOIN FETCH`) y `UserMapper`. Archivos: `auth-module/src/main/java/com/example/sportbooking/auth/dto/AuthResponse.java`, `auth-module/src/main/java/com/example/sportbooking/auth/dto/UserIdentityResponse.java` (nuevo), `auth-module/src/main/java/com/example/sportbooking/auth/controller/AuthController.java`. Concepto: resolver la identidad en un solo request; reutilizar el DTO público (`UserResponseDTO`, sin `password_hash`) en vez de exponer la entity.
- `30` — **resuelto (BREAKING)**: propiedad de la reserva derivada del token. Síntoma: `POST /reservations` tomaba `reserved_by_user_id` del body, así que se podía crear una reserva "a nombre de otro". Causa raíz: el ownership venía del cliente. Fix: se quita `reserved_by_user_id` de `ReservationDTO`; `ReservationService.create` deriva el dueño del usuario autenticado (`Authentication.getName()` = email + `UserRepository.findByEmail`). Impacto: las colecciones Postman de alta deben dejar de enviar ese campo. Archivos: `reservation-module/src/main/java/com/example/sportbooking/reservation/dto/ReservationDTO.java`, `reservation-module/src/main/java/com/example/sportbooking/reservation/service/ReservationService.java`. Concepto: nunca confiar en el cliente para la identidad/ownership (OWASP API1/API3); `reservation-module` no depende de `auth-module`, usa Spring Security core + `UserRepository` (email como clave, consistente con `CustomUserDetails`).
- `31` — **resuelto**: directorio de usuarios y roles. Síntoma: el front no podía descubrir usuarios ni el catálogo de roles para armar una membresía (`user_venue_role`). Causa raíz: no existían endpoints de lectura de usuarios ni roles. Fix: nuevo `UserController` (`GET /users?q=`, `@PreAuthorize("hasRole('ADMIN')")`, filtro `findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase`) y `RoleController` (`GET /roles`, autenticado) + `RoleService`; `UserService.findAll(q)`. Rompe a propósito la convención vieja "no existe `UserController`". Archivos: `user-module/src/main/java/com/example/sportbooking/user/controller/UserController.java` (nuevo), `user-module/src/main/java/com/example/sportbooking/user/controller/RoleController.java` (nuevo), `user-module/src/main/java/com/example/sportbooking/user/service/RoleService.java` (nuevo), `user-module/src/main/java/com/example/sportbooking/user/service/UserService.java`, `user-module/src/main/java/com/example/sportbooking/user/repository/UserRepository.java`, `AGENTS.md`. Concepto: la convención documentada cambia cuando lo pide el dominio; `@PreAuthorize` por método vs regla global `authenticated()`.
- `32` — **resuelto**: la reserva respeta el calendario. Síntoma: se podía reservar una franja que la cancha no tenía habilitada ese día. Causa raíz: no existía la regla "franja reservable = `court_schedule` habilitado del día". Fix: consultas `existsEnabledSlot` / `findEnabledSlots` en `CourtScheduleRepository` (JPQL, `is_enabled`); `CourtScheduleService.isReservable/findReservableSlots`; `ReservationService.validateReservable` en `create` y `update` (400 si la franja no está en el calendario); `reservation-module` pasa a depender de `court-schedule-module` (unidireccional, sin ciclo). Archivos: `reservation-module/pom.xml`, `court-schedule-module/src/main/java/com/example/sportbooking/courtschedule/repository/CourtScheduleRepository.java`, `court-schedule-module/src/main/java/com/example/sportbooking/courtschedule/service/CourtScheduleService.java`, `reservation-module/src/main/java/com/example/sportbooking/reservation/service/ReservationService.java`. Concepto: regla de negocio compuesta; dirección de dependencias espejando la FK; conversión fecha → `day_of_week` (`getValue() % 7`, 0=domingo).
- `33` — **resuelto**: endpoint de disponibilidad. Fix: `GET /courts/{id}/availability?date=YYYY-MM-DD` (nuevo `AvailabilityController` en `reservation-module`) devuelve `slots` ordenados por `start_time` con `status` `free`/`occupied`; una reserva `CANCELLED` no ocupa; cancha inexistente → 404. El service devuelve un read model interno (`CourtAvailability`) y el controller mapea con `TimeBlockMapper` (mapeo en el borde). Archivos: `reservation-module/src/main/java/com/example/sportbooking/reservation/controller/AvailabilityController.java` (nuevo), `reservation-module/src/main/java/com/example/sportbooking/reservation/dto/AvailabilityResponseDTO.java` (nuevo), `reservation-module/src/main/java/com/example/sportbooking/reservation/dto/AvailabilitySlotDTO.java` (nuevo), `reservation-module/src/main/java/com/example/sportbooking/reservation/service/CourtAvailability.java` (nuevo), `reservation-module/src/main/java/com/example/sportbooking/reservation/service/ReservationService.java`, `reservation-module/src/main/java/com/example/sportbooking/reservation/repository/ReservationRepository.java`. Concepto: read model calculado ("calendario menos reservas"); dos controllers (módulos distintos) pueden compartir el prefijo `/courts` si los paths no chocan.
- `34` — **resuelto**: seed de desarrollo. Síntoma: crear el `ADMIN` a mano para probar autorización. Causa raíz: no había datos de ejemplo. Fix: `DevDataSeeder` (`@Profile("dev")`, `ApplicationRunner`, idempotente: si hay venues, no siembra). Crea rol+usuario `ADMIN` (password hasheada con el `PasswordEncoder`), sede, canchas, `time_block`, `court_schedule` y reservas de ejemplo (una activa, una `CANCELLED`). Commitea directo con repositories (no vía `ReservationService.create`, que exige `SecurityContext`). Archivos: `app/src/main/java/com/example/sportbooking/config/DevDataSeeder.java` (nuevo), `AGENTS.md`. Concepto: `data.sql` vs `ApplicationRunner` (orden con `ddl-auto`), perfiles de Spring, idempotencia, usuario global vía `cascade=ALL` de `User.globalRoles`.

---

## Pendientes / deuda técnica

1. Testing automatizado (hoy no hay ninguno).
2. Remodelado más amplio del esquema de base (evaluar en conjunto, no parche por parche).
3. Adoptar un migrador de verdad (Flyway/Liquibase): `ddl-auto=update` crea constraints por su cuenta y con nombres generados, no versionables; mezclar DDL manual con él duplica FKs (ver punto 26). Mientras tanto, **no** agregar constraints a mano.
