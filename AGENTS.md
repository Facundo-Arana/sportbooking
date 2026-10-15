# AGENTS.md

Proyecto personal de portfolio (no comercial) para gestionar reservas de canchas. El dueño es un dev reconvirtiéndose a Spring Boot; cada cambio importante debe venir con la explicación conceptual detrás (ver "Flujo de trabajo"). Responder en español.

## Comandos (PowerShell, desde la raíz)

- Compilar todo: `.\mvnw.cmd -q -DskipTests compile`
- Compilar un módulo + sus dependencias: `.\mvnw.cmd -pl court-module -am compile`
- Correr la app (único módulo ejecutable): `.\mvnw.cmd -pl app -am spring-boot:run`
- Correr con datos de ejemplo (perfil `dev`, seed idempotente): `.\mvnw.cmd -pl app -am spring-boot:run -Dspring-boot.run.profiles=dev` (o `SPRING_PROFILES_ACTIVE=dev`). Crea rol+usuario `ADMIN` (`admin@sportbooking.dev` / `admin123`), sede, canchas, franjas, calendario y reservas.
- Swagger UI: `http://localhost:8081/swagger-ui/index.html` (con "Authorize" se pega el JWT).
- Empaquetar: `.\mvnw.cmd -DskipTests package`
- Correr todos los tests: `.\mvnw.cmd test`. Un módulo: `.\mvnw.cmd -pl reservation-module test`. Verificación end-to-end: `.\mvnw.cmd verify`.
- **No hay config de lint/formatter.** La verificación sigue siendo compilar + tests + probar endpoints con Postman.

## Testing

- **Prerrequisito de entorno (solo para los tests de integración)**: Docker Desktop con backend WSL2, instalado y corriendo. En Windows: `wsl --install` (admin + reinicio) y luego Docker Desktop. Diagnóstico: `docker info` debe devolver `ServerVersion` sin error (si dice `no se reconoce el comando` o falla el daemon, los tests de integración no corren). Los tests **unit** y de **slice** no necesitan Docker.
- **Separación por capas** (D6 del design):
  - **Unit** (JUnit 5 + Mockito, sin contexto Spring): junto al service, dentro de cada módulo (`<modulo>/src/test/java/...`). No dependen de base ni de Docker.
  - **Slice** (`@WebMvcTest` + `spring-security-test`): junto al controller, en cada módulo. Mockean el service con `@MockBean` y prueban el contrato HTTP + seguridad (401/403/200) sin base.
  - **Integración** (`@SpringBootTest` + Testcontainers): solo en `app/src/test` (único módulo con `@SpringBootApplication`). Levantan un Postgres efímero y ejecutan los escenarios de las specs. Clase base: `app/src/test/java/com/example/sportbooking/AbstractIntegrationTest.java` (patrón singleton de `PostgreSQLContainer` + `@ServiceConnection`).
- **Perfil `test`** (`app/src/test/resources/application-test.properties`): `create-drop` de Hibernate sobre el schema `platform`, con `jwt.secret` de test. El DataSource lo provee el container (`@ServiceConnection`); el Postgres nativo de dev no se toca. `DevDataSeeder` es `@Profile("dev")`, así que **no** corre en test: cada test siembra su propio fixture.
- **DDL de referencia**: `db/schema-reference.sql` guarda el esquema como insumo para una futura adopción de Flyway (deuda #3). Está **fuera del classpath**: no lo referencia `spring.sql.init` ni `@Sql`, no participa del build ni de los tests.

## Arquitectura

- Maven multi-módulo. `sportbooking` raíz es solo aggregator (`packaging=pom`); `app` es el único jar ejecutable.
- Un módulo por dominio: `user`, `auth`, `venue`, `court`, `time-block`, `court-schedule`, `user-venue-role`, `reservation`. Cada uno es una librería jar con estructura fija: `entity → dto → exception → repository → service → controller → mapper`.
- Package raíz `com.example.sportbooking.<dominio>`. Todos comparten package base y `app` está en la cima, así que `@SpringBootApplication` (`app/.../SportbookingApplication.java`) hace component scan de TODOS los módulos. Un módulo nuevo debe usar `com.example.sportbooking.<dominio>` (sin guiones: `timeblock`, `courtschedule`, `uservenuerole`).
- `app/pom.xml` depende de los 8 módulos. Por eso los beans transversales viven ahí y no en un módulo común:
  - `app/.../exception/GlobalExceptionHandler.java`
  - `app/.../security/VenueAuthorization.java`
  Moverlos exigiría un módulo compartido nuevo o crearía dependencia circular. No duplicar.
- Dependencias entre módulos espejan las FK de la base: `court/time-block → venue`, `court-schedule → court + time-block`, `user-venue-role → user + venue`, `reservation → court + time-block + user + court-schedule` (esta última por regla de negocio: la reserva se valida contra el calendario). Relaciones `@ManyToOne` + `@JoinColumn`, no ids sueltos.
- `user-module` ahora tiene controller propio (`UserController`, `RoleController`); `User`/`Role` también se exponen vía `auth-module`.

## Convenciones

- Campos Java en snake_case cuando reflejan la columna (`venue_id`, `block_group`, `start_time`, `payment_status`, `created_at`). Es intencional; **no renombrar a camelCase**.
- Tipos: `LocalTime` para columnas `time`; `Short` para `day_of_week` (smallint, `0`=domingo…`6`=sábado); `LocalDate` para fecha; `Boolean` wrapper en campos opcionales de update parcial (`indoor`, `is_active`, `is_enabled`) para distinguir "no vino" (`null`) de `false`.
- `created_at`, `status`, `payment_status`, `is_active`: se setean a mano en el constructor de alta. No confiar en defaults de columna (Hibernate manda `NULL` explícito y pisa el default de Postgres).
- El update es parcial: solo se pisan campos no-null del DTO. Las reglas cruzadas se re-validan contra el **estado final de la entity**, no contra el DTO (`validateTimeRange`, `validateSameVenue`).
- `venue_id` es **inmutable** en `PUT` (Court/TimeBlock/CourtSchedule/Reservation): mover entre venues = borrar y crear. No reintroducir la reasignación.
- Excepciones → HTTP las traduce SOLO `GlobalExceptionHandler`: `*NotFoundException`→404, `IllegalArgumentException`→400, `IllegalStateException`→409, `DataIntegrityViolationException`→409. No agregar try/catch en controllers; lanzar la excepción correcta desde el service.
- Controllers devuelven Response DTOs mapeados con MapStruct (`*Mapper.toResponseDTO`), nunca entities JPA. El mapeo va en el controller, no en el service (el service devuelve entities).
- `lombok-mapstruct-binding` en `annotationProcessorPaths` del `pom.xml` raíz es necesario para que Lombok y MapStruct convivan. No sacarlo.
- Filtros por relación: query derivado (`findByVenue_Id`, `findByCourt_Id`), expuesto como `?venueId=` / `?courtId=` opcional.

## Seguridad

- JWT stateless; `SecurityConfiguration` en `auth-module`. `/auth/login`, `/auth/register`, preflight `OPTIONS /**` y las rutas de Swagger (`/swagger-ui/**`, `/v3/api-docs/**`) son públicos; `/auth/me` y todo lo demás, autenticado. (Antes `/auth/**` entero era `permitAll`, lo que dejaba `/auth/me` sin proteger → 500 sin token; ver bitácora 38.)
- Autorización de recursos vía `@PreAuthorize` con SpEL que llama al bean `@venueAuthorization` **por nombre** (no por import). No crear dependencia directa de `app`.
- `@EnableMethodSecurity` en `SecurityConfiguration` es obligatorio; sin él, `@PreAuthorize` se ignora en silencio.
- `hasRole('ADMIN')` es bypass global y se compone aparte del chequeo de venue en cada `@PreAuthorize`.
- CORS configurable por propiedad: `app.cors.allowed-origins` (default `http://localhost:5173`, dev server de Vite) y `app.cors.allowed-methods` (incluye `PATCH`). El `CorsConfigurationSource` se engancha dentro de `SecurityConfiguration`; no declarar un `CorsFilter` suelto.
- Identidad/ownership desde el token: `login` y `/auth/me` devuelven perfil + authorities; el `reservedByUser` de una reserva se deriva del usuario autenticado (no se acepta `reserved_by_user_id` en el body).

## Base de datos

- PostgreSQL local, schema `platform`. Config en `app/src/main/resources/application.properties`: puerto 8081, `ddl-auto=update`. Los secretos se resuelven por variable de entorno (prioridad) o por `application-local.properties` (no versionado, en el root; importado con `spring.config.import`). La config versionada solo tiene placeholders (`${DB_PASSWORD:}`, `${JWT_SECRET:}`, etc.). El `spring-boot-maven-plugin` corre con `workingDirectory` = root del repo.
- `ddl-auto=update` aplica `ALTER TABLE` al arrancar y **falla en silencio** si hay datos que violan un constraint nuevo. No hay Flyway/Liquibase ni migraciones versionadas. `db/schema-reference.sql` es solo un **insumo de referencia** para adoptar Flyway (deuda #3): no lo consume el build ni los tests.
- Los constraints de FK los crea/administra Hibernate con `ddl-auto=update` (nombres generados tipo `fk<hash>`), incluso sobre tablas existentes. **No** agregar FK a mano con otro nombre: ddl-auto no reconoce el nombre y crea un duplicado sobre la misma columna (bitácora punto 26).
- Seed de desarrollo: `DevDataSeeder` (`@Profile("dev")`, idempotente) crea rol+usuario `ADMIN` (password hasheada con el `PasswordEncoder`), sede, canchas, franjas, calendario y reservas. Se activa corriendo con el perfil `dev` (ver Comandos). No requiere insertar el `ADMIN` a mano.

## Flujo de trabajo

- `bitacora-tecnica-sportbooking.md` es la fuente de verdad de qué cambió y por qué. Todo cambio de código debe agregar/actualizar una entrada numerada: síntoma → causa raíz → fix → **archivos modificados** (ruta completa) → **concepto para profundizar**. Los archivos listados deben coincidir 1 a 1 con lo realmente tocado.
- Los cambios se aplican **directo sobre los archivos del repo** (no se entregan listados de archivos ni zips); el usuario testea después con `mvn compile` + Postman.
- Priorizar claridad y explicación sobre atajos; el objetivo es defender el proyecto en entrevistas.

## Gotchas

- El `pom.xml` raíz fija **Java 17** (`java.version`, source/target). Aunque el stack objetivo mencione Java 21, el build real es 17; no usar features de 21 sin actualizar la config.
- Un solo `application.properties` versionado, en `app` (no por módulo). `application-local.properties` vive en el root y NO se versiona (está en `.gitignore`): ahí van los secretos de desarrollo. Se importa con `optional:file:./` y `optional:file:../` porque el *working directory* depende de cómo se lance (Maven CLI = root; Eclipse/IntelliJ al correr la main class y `java -jar` desde `app` = `app/`); si no se encuentra, `optional:` lo ignora en silencio y falla con `SCRAM-based authentication, but no password was provided`.
- `UserController` (`GET /users?q=`, solo `ADMIN`) y `RoleController` (`GET /roles`) existen en `user-module`. `User`/`Role` también se exponen vía `auth-module` (identidad) y `UserResponseDTO` anidado en otros módulos.
- `User.password_hash` tiene `@JsonIgnore` y `User.globalRoles` ↔ `UserGlobalRole.user` usan `@JsonManagedReference`/`@JsonBackReference` como defensa; la regla igual es no exponer entities (usar DTOs).
