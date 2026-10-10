# Bitácora Técnica — Sportbooking

> Fuente de verdad del estado, las decisiones y la deuda del proyecto. **Versión compacta**: se recortó el narrativo histórico de bugs (queda en el historial de git). Lo que se conserva son el estado actual, las convenciones fijas, los conceptos útiles para entrevista y los pendientes.

---

## Estado actual

- Monolito modular Maven. `sportbooking` raíz = aggregator (`packaging=pom`); `app` = único jar ejecutable y depende de los 8 módulos.
- Dominios implementados, todos con estructura `entity → dto → exception → repository → service → controller → mapper`: `user`, `auth`, `venue`, `court`, `time-block`, `court-schedule`, `user-venue-role`, `reservation`.
- CRUD con alta / listado con filtros / detalle / update parcial / baja. `user-venue-role` **no tiene `PUT`**: su identidad ES el trío de FKs, "editarla" equivale a borrar y crear.
- `reservation` tiene endpoints por intención además del CRUD: `PATCH /reservations/{id}/cancel` (dueño o personal), `.../status` y `.../payment` (solo personal del venue). `PUT` quedó solo para reprogramar (sin `status`/`payment_status`).
- Seguridad: JWT stateless + autorización por rol/venue (`@PreAuthorize` + bean `VenueAuthorization`).
- Respuestas de la API: Response DTOs por entity, mapeados con MapStruct **en el controller**.
- Errores centralizados en `GlobalExceptionHandler` (app-module): 400 / 404 / 409 / 401 / 500.
- **Suite de pruebas** (feature `001-auth-tests`): unitarias JUnit 5 + Mockito en `user-module` (`UserServiceTest`) y `auth-module` (`AuthControllerTest`); integración HTTP con MockMvc + H2 en `app` (`AuthFlowIntegrationTest`, perfil `test`, sin PostgreSQL). `.\mvnw.cmd test` = puerta de calidad oficial.

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
- CORS: solo `http://localhost:4200` (Angular). El `CorsConfigurationSource` se engancha dentro de `SecurityConfiguration` (`.cors()`); no declarar un `CorsFilter` suelto.
- Defensa en profundidad: `User.password_hash` con `@JsonIgnore`; `User.globalRoles` ↔ `UserGlobalRole.user` con `@JsonManagedReference`/`@JsonBackReference`. Igual, la regla es no exponer entities.

---

## Base de datos

- PostgreSQL local, schema `platform`. Config única en `app/src/main/resources/application.properties`: puerto 8081, `ddl-auto=update`. Los secretos salen de placeholders (`${DB_USERNAME:}`, `${DB_PASSWORD:}`, `${JWT_SECRET:}`) resueltos por **variable de entorno** (prioridad) o por `application-local.properties` (no versionado), importado con `spring.config.import`. El plugin `spring-boot-maven-plugin` corre con `workingDirectory` = root del repo, para que `./application-local.properties` se encuentre siempre.
- `ddl-auto=update` aplica `ALTER TABLE` al arrancar y **falla en silencio** si hay datos que violan un constraint nuevo. No hay Flyway/Liquibase ni scripts versionados.
- Constraints reales vía anotaciones en la entity: `UNIQUE`/`NOT NULL` en `user.email`/`username`/`password_hash` y `venue.name`.
- Los constraints de FK los crea/administra Hibernate con `ddl-auto=update` (nombres generados tipo `fk<hash>`), incluso sobre tablas ya existentes. **No** agregar FK a mano con otro nombre: ddl-auto no reconoce el nombre y crea un duplicado sobre la misma columna (ver punto 26).
- No hay seed de datos: probar la autorización requiere un `ADMIN` en `user_global_role` creado a mano.

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
- `27` — **resuelto**: suite de pruebas automatizadas de login/registro (feature `001-auth-tests`). Síntoma: el proyecto no tenía red de seguridad; cualquier cambio en `UserService.registerUser` o `AuthController` se verificaba a mano con Postman, y la constitución afirmaba explícitamente que `mvn test` no verificaba nada. Causa raíz: sin framework de test ni base de datos de test, la verificación no era reproducible ni desatendida. Fix: suite mixta — (a) unitarias puras con Mockito y sin contexto Spring (`UserServiceTest` en `user-module`, `AuthControllerTest` en `auth-module`), (b) integración HTTP real con `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` sobre H2 en memoria (`AuthFlowIntegrationTest` en `app`). `spring-boot-starter-test` (scope `test`) se declara en el `pom.xml` raíz y `com.h2database:h2` (scope `test`) solo en `app`. El perfil `test` (`app/src/test/resources/application-test.properties`) define H2 en modo PostgreSQL, crea el schema `platform` con `INIT`, usa `ddl-auto=create-drop` y `NON_KEYWORDS=USER` (en H2 `user` es palabra reservada y `platform.user` no se podía crear sin desactivarla). `.\mvnw.cmd test` pasa a ser la puerta de calidad oficial; se enmienda el principio VI de la constitución a `1.1.0` y se sincroniza `AGENTS.md`. Hallazgo colateral: la suite destapó un bug latente —`GET /auth/me` sin token devuelve 500 en vez de 401 porque `/auth/**` es `permitAll` y el principal anónimo no es un `UserDetails`—; se dejó como pendiente (1) sin tocar producción (decisión explícita del dueño). Archivos: `pom.xml`, `app/pom.xml`, `app/src/test/resources/application-test.properties`, `user-module/src/test/java/com/example/sportbooking/user/service/UserServiceTest.java`, `auth-module/src/test/java/com/example/sportbooking/auth/controller/AuthControllerTest.java`, `app/src/test/java/com/example/sportbooking/auth/AuthFlowIntegrationTest.java`, `.specify/memory/constitution.md`, `AGENTS.md`, `bitacora-tecnica-sportbooking.md`. Concepto: pirámide de tests (unitarias rápidas y aisladas + una integración que cubre seguridad, errores y persistencia), aislamiento por test con datos únicos (`UUID`) y limpieza en `@BeforeEach`, base embebida para no depender del entorno, y el test como puerta de calidad que además detecta bugs latentes.

---

## Pendientes / deuda técnica

1. **`GET /auth/me` sin token devuelve 500** (bug latente detectado por la suite): `/auth/**` está `permitAll`, así que `/auth/me` llega igual al controller con un principal anónimo (el `String "anonymousUser"`) y `AuthController.me()` lo castea a `UserDetails` → `ClassCastException`. El contrato esperaba 401. Arreglar protegiendo `/auth/me` (o guardando el tipo de principal) y actualizar `AuthFlowIntegrationTest.meSinTokenDevuelve500PorBugConocido` a `isUnauthorized()`. Ver punto 27.
2. Remodelado más amplio del esquema de base (evaluar en conjunto, no parche por parche).
3. Adoptar un migrador de verdad (Flyway/Liquibase): `ddl-auto=update` crea constraints por su cuenta y con nombres generados, no versionables; mezclar DDL manual con él duplica FKs (ver punto 26). Mientras tanto, **no** agregar constraints a mano.
