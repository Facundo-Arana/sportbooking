# AGENTS.md

Proyecto personal de portfolio (no comercial) para gestionar reservas de canchas. El dueño es un dev reconvirtiéndose a Spring Boot; cada cambio importante debe venir con la explicación conceptual detrás (ver "Flujo de trabajo"). Responder en español.

## Comandos (PowerShell, desde la raíz)

- Compilar todo: `.\mvnw.cmd -q -DskipTests compile`
- Compilar un módulo + sus dependencias: `.\mvnw.cmd -pl court-module -am compile`
- Correr la app (único módulo ejecutable): `.\mvnw.cmd -pl app -am spring-boot:run`
- Empaquetar: `.\mvnw.cmd -DskipTests package`
- **No hay tests** (ni `src/test`, ni config de lint/formatter). No afirmar que `mvn test` verifica algo. La verificación real es compilar + probar endpoints con Postman.

## Arquitectura

- Maven multi-módulo. `sportbooking` raíz es solo aggregator (`packaging=pom`); `app` es el único jar ejecutable.
- Un módulo por dominio: `user`, `auth`, `venue`, `court`, `time-block`, `court-schedule`, `user-venue-role`, `reservation`. Cada uno es una librería jar con estructura fija: `entity → dto → exception → repository → service → controller → mapper`.
- Package raíz `com.example.sportbooking.<dominio>`. Todos comparten package base y `app` está en la cima, así que `@SpringBootApplication` (`app/.../SportbookingApplication.java`) hace component scan de TODOS los módulos. Un módulo nuevo debe usar `com.example.sportbooking.<dominio>` (sin guiones: `timeblock`, `courtschedule`, `uservenuerole`).
- `app/pom.xml` depende de los 8 módulos. Por eso los beans transversales viven ahí y no en un módulo común:
  - `app/.../exception/GlobalExceptionHandler.java`
  - `app/.../security/VenueAuthorization.java`
  Moverlos exigiría un módulo compartido nuevo o crearía dependencia circular. No duplicar.
- Dependencias entre módulos espejan las FK de la base: `court/time-block → venue`, `court-schedule → court + time-block`, `user-venue-role → user + venue`, `reservation → court + time-block + user`. Relaciones `@ManyToOne` + `@JoinColumn`, no ids sueltos.

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

- JWT stateless; `SecurityConfiguration` en `auth-module`. `/auth/**` y preflight `OPTIONS /**` públicos, todo lo demás autenticado.
- Autorización de recursos vía `@PreAuthorize` con SpEL que llama al bean `@venueAuthorization` **por nombre** (no por import). No crear dependencia directa de `app`.
- `@EnableMethodSecurity` en `SecurityConfiguration` es obligatorio; sin él, `@PreAuthorize` se ignora en silencio.
- `hasRole('ADMIN')` es bypass global y se compone aparte del chequeo de venue en cada `@PreAuthorize`.
- CORS solo permite `http://localhost:4200` (Angular). El `CorsConfigurationSource` se engancha dentro de `SecurityConfiguration`; no declarar un `CorsFilter` suelto.

## Base de datos

- PostgreSQL local, schema `platform`. Config única en `app/src/main/resources/application.properties`: puerto 8081, `ddl-auto=update`, credenciales y `jwt.secret` hardcodeados (deuda conocida).
- `ddl-auto=update` aplica `ALTER TABLE` al arrancar y **falla en silencio** si hay datos que violan un constraint nuevo. Scripts manuales en `db/*.sql` cuando hace falta.
- Varias FK del dump original no existen a nivel de base (ej. `fk_reserved_by_user_id`; `fk_user_id`/`fk_role_id` en `user_venue_role`) aunque se mapeen con `@ManyToOne`.
- No hay seed de datos: probar la autorización requiere un `ADMIN` en `user_global_role` creado a mano.

## Flujo de trabajo

- `bitacora-tecnica-sportbooking.md` es la fuente de verdad de qué cambió y por qué. Todo cambio de código debe agregar/actualizar una entrada numerada: síntoma → causa raíz → fix → **archivos modificados** (ruta completa) → **concepto para profundizar**. Los archivos listados deben coincidir 1 a 1 con lo realmente tocado.
- Los cambios se aplican **directo sobre los archivos del repo** (no se entregan listados de archivos ni zips); el usuario testea después con `mvn compile` + Postman.
- Priorizar claridad y explicación sobre atajos; el objetivo es defender el proyecto en entrevistas.

## Gotchas

- El `pom.xml` raíz fija **Java 17** (`java.version`, source/target). Aunque el stack objetivo mencione Java 21, el build real es 17; no usar features de 21 sin actualizar la config.
- Solo hay un `application.properties`, en `app` (no por módulo).
- No existe `UserController`: `User` se expone vía `auth-module` y `UserResponseDTO` anidado en otros módulos.
- `User.password_hash` tiene `@JsonIgnore` y `User.globalRoles` ↔ `UserGlobalRole.user` usan `@JsonManagedReference`/`@JsonBackReference` como defensa; la regla igual es no exponer entities (usar DTOs).
