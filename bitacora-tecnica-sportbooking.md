# Bitácora Técnica — Sportbooking

> Fuente de verdad del estado, las decisiones y la deuda del proyecto. **Versión compacta**: se recortó el narrativo histórico de bugs (queda en el historial de git). Lo que se conserva son el estado actual, las convenciones fijas, los conceptos útiles para entrevista y los pendientes.

---

## Estado actual

- Monolito modular Maven. `sportbooking` raíz = aggregator (`packaging=pom`); `app` = único jar ejecutable y depende de los 8 módulos.
- Dominios implementados, todos con estructura `entity → dto → exception → repository → service → controller → mapper`: `user`, `auth`, `venue`, `court`, `time-block`, `court-schedule`, `user-venue-role`, `reservation`.
- CRUD con alta / listado con filtros / detalle / update parcial / baja. `user-venue-role` **no tiene `PUT`**: su identidad ES el trío de FKs, "editarla" equivale a borrar y crear.
- Seguridad: JWT stateless + autorización por rol/venue (`@PreAuthorize` + bean `VenueAuthorization`).
- Respuestas de la API: Response DTOs por entity, mapeados con MapStruct **en el controller**.
- Errores centralizados en `GlobalExceptionHandler` (app-module): 400 / 404 / 409 / 401 / 500.
- **Sin tests automatizados**. Verificación = `mvn compile` + Postman.

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
- `canManageReservation`: la puede manejar quien la creó (dueño) **o** quien tiene rol en el venue de la cancha. El bypass `hasRole('ADMIN')` se agrega aparte en cada `@PreAuthorize`.
- CORS: solo `http://localhost:4200` (Angular). El `CorsConfigurationSource` se engancha dentro de `SecurityConfiguration` (`.cors()`); no declarar un `CorsFilter` suelto.
- Defensa en profundidad: `User.password_hash` con `@JsonIgnore`; `User.globalRoles` ↔ `UserGlobalRole.user` con `@JsonManagedReference`/`@JsonBackReference`. Igual, la regla es no exponer entities.

---

## Base de datos

- PostgreSQL local, schema `platform`. Config única en `app/src/main/resources/application.properties`: puerto 8081, `ddl-auto=update`, credenciales y `jwt.secret` hardcodeados (deuda conocida).
- `ddl-auto=update` aplica `ALTER TABLE` al arrancar y **falla en silencio** si hay datos que violan un constraint nuevo. No hay Flyway/Liquibase ni scripts versionados.
- Constraints reales vía anotaciones en la entity: `UNIQUE`/`NOT NULL` en `user.email`/`username`/`password_hash` y `venue.name`.
- Varias FK del dump original **no existen a nivel de base** (ej. `fk_reserved_by_user_id`; `fk_user_id`/`fk_role_id` en `user_venue_role`) aunque se mapeen con `@ManyToOne`.
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
- **Autorización por campo vs por recurso**: el dueño de una reserva puede editarla pero no debería tocar `payment_status` (mass assignment / OWASP API3).
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
- `24` — pendiente: autorización por campo en `PUT /reservations`.

---

## Pendientes / deuda técnica

1. **Verificar en local** los cambios de `venue_id` inmutable (punto 22): `mvn compile` + checklist de Postman.
2. **Punto 24 — autorización por campo en `PUT /reservations`**: el dueño no debería poder setear `payment_status` ni pasar `status` a `CONFIRMED`. Requiere decisión de diseño (endpoints por intención `PATCH .../cancel` y `.../payment`, o DTO distinto por rol).
3. Credenciales de Postgres y `jwt.secret` hardcodeados → variables de entorno o `application-local.properties` fuera de Git.
4. FKs reales faltantes a nivel de base en `reservation` y `user_venue_role`.
5. Testing automatizado (hoy no hay ninguno).
6. Remodelado más amplio del esquema de base (evaluar en conjunto, no parche por parche).
