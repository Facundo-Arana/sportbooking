# Design

## Context

Ver `proposal.md - Why`. Restricciones que condicionan el diseno:

- Monolito modular: cada dominio es un jar; las dependencias entre modulos espejan las FK. `reservation-module` hoy depende de `court` + `time-block` + `user`, y **no** de `court-schedule` ni de `auth`.
- Existe un patron establecido de reutilizacion cruzada de repositories entre modulos (`ReservationService` usa `CourtRepository`, `TimeBlockRepository`, `UserRepository`) y de resolver el usuario autenticado en `app-module` (`VenueAuthorization`) mediante `CustomUserDetails`.
- Sin `UserController`: `User` se expone via `auth-module`. Sin specs previas en OpenSpec: todo el delta es `ADDED`.
- Java 17, Spring Boot 3, `ddl-auto=update` (sin migrador). Sin tests automatizados; verificacion = `mvn compile` + Postman/Swagger.

## Goals / Non-Goals

**Goals:**
- Cerrar los huecos de contrato que impiden que un SPA (React + Vite) opere el API sin duplicar reglas de negocio.
- Exponer la disponibilidad como read model y alinear el alta de reserva con el calendario de la cancha.
- Dejar un banco de pruebas visual (Swagger) y datos de desarrollo para iterar el frontend.

**Non-Goals:**
- Paginacion, refresh token/logout, validacion con `@Valid` y contrato de errores unificado (diferidos).
- Endpoint de disponibilidad a nivel venue (grid de todas las canchas en una llamada): se hace el primitivo por cancha.
- Cambios de esquema de base de datos.

## Decisions

### D1. Identidad: componer un DTO en `auth-module`, no crear `UserController` para esto

`/auth/me` y `login` devuelven un DTO de identidad que compone `UserResponseDTO` (ya existe, sin `password_hash`) + `authorities`. Se resuelve en `AuthController` reutilizando `CustomUserDetails`/`UserService`.
- **Por que**: un solo request para que el front sepa quien es; evita crear un endpoint de "usuario actual" en `user-module` solo para esto.
- **Alternativa descartada**: el front llama a `/users/{id}` tras el login (segunda llamada y necesita el id que justamente le falta).

### D2. Propiedad de la reserva: derivar del principal, sin exponer `reserved_by_user_id` en el body

Se elimina `reserved_by_user_id` de `ReservationDTO` (**breaking**). El propietario se deriva del usuario autenticado en el servicio, resolviendolo por `Authentication.getName()` (que es el email, ver `CustomUserDetails.getUsername()`) + `UserRepository`.
- **Por que**: cierra el alta "a nombre de otro" y evita acoplar `reservation-module` a `auth-module` (solo usa Spring Security core + `UserRepository`, que ya depende). El email como clave es consistente con `CustomUserDetailsService.loadUserByUsername`.
- **Alternativa descartada**: importar `CustomUserDetails` en `reservation-module` (acoplaria el dominio a auth); inyectar el id desde el controller (mismo problema de dependencia y mantiene el campo en el body).
- **Staff reservando para un cliente**: sigue modelado por `reserved_for_user_id` / `reserved_for_name`; `reservedByUser` es siempre quien crea.

### D3. Disponibilidad y regla de calendario viven en `reservation-module`; `reservation-module` pasa a depender de `court-schedule-module`

`reservation-module` gana `court-schedule-module` como dependencia por `pom.xml` (hoy no la tiene) y ahi vive la query de franjas reservables. `court-schedule-module` NO depende de `reservation` (sin ciclo: `reservation -> court-schedule -> court + time-block`).
- **Por que**: la disponibilidad es "calendario menos reservas"; solo `reservation-module` conoce las reservas. Poner la regla de calendario en el mismo modulo que la usa (alta, reprogramacion, disponibilidad) evita duplicarla. La related repo-cross-module reuse ya es patron del proyecto.
- **Alternativa descartada**: endpoint en `court-schedule-module` (obligaria a `court-schedule -> reservation`, invirtiendo el sentido natural y creando riesgo de ciclo si luego `reservation -> court-schedule`).

### D4. Endpoint de disponibilidad: `GET /courts/{id}/availability?date=YYYY-MM-DD`

Respuesta con `courtId`, `date`, `day_of_week` y `slots: [{ timeBlock, status: "free"|"occupied" }]`, ordenado por `start_time`.
- **Por que**: contrato legible para el front (calendario de una cancha); `LocalDate` ya se bindea en ISO-8601 sin `@DateTimeFormat` (patron de `ReservationController.findAll`).
- **Alternativa descartada (diferida)**: endpoint por venue que devuelva el grid de varias canchas; se compone desde el primitivo por cancha.

### D5. Directorio: `UserController` + `RoleController` nuevos en `user-module`

`GET /users?q=` (filtro por username/email, `@PreAuthorize("hasRole('ADMIN')")`) y `GET /roles` (autenticado). `RoleRepository`/`RoleResponseDTO` ya existen.
- **Por que**: es el lugar correcto para gestion de usuarios; el front no puede armar una membresia sin descubrir usuarios.
- **Trade-off aceptado**: rompe la convencion documentada "no existe UserController". Se actualizan `AGENTS.md` y la bitacora en la implementacion.

### D6. CORS desde propiedad, con `PATCH`

`allowedOrigins` y `allowedMethods` se externalizan a propiedades (`app.cors.allowed-origins`, default `http://localhost:5173`); se agrega `PATCH`.
- **Por que**: hoy el origen esta hardcodeado a Angular y falta `PATCH` (bloqueante para los `PATCH /reservations/{id}/...`).
- **Alternativa descartada**: seguir hardcodeando; obligaria a tocar codigo por entorno.

### D7. OpenAPI con springdoc, paths publicos

Dependencia `springdoc-openapi-starter-webmvc-ui` (v2.x) en `app`; `SecurityConfiguration` agrega `permitAll` para `/swagger-ui/**` y `/v3/api-docs/**`; se configura el bearer JWT para "Authorize".
- **Por que**: banco de pruebas visual de todo el contrato y buena carta de presentacion.
- **Trade-off**: expone el contrato sin autenticacion; aceptable en dev/portfolio (en prod se restringe).

### D8. Seed de desarrollo via `@Profile("dev")`

`DevDataSeeder` (`ApplicationRunner`) en `app-module`, activo solo con perfil `dev`, idempotente (no siembra si ya hay datos). Crea un `ADMIN` (password hasheado con el `PasswordEncoder`), un venue, canchas, time-blocks, court-schedules y un par de reservas.
- **Por que**: `data.sql` pelea con `ddl-auto`/orden; un runner usa los repositories y el encoder, y es seguro por perfil. Reemplaza el "insertar ADMIN a mano".

## Risks / Trade-offs

- **Breaking en `/auth/me`, login y body de reserva** -> el front aun no existe; se documentan los nuevos shapes en Swagger y en la bitacora.
- **`reservation -> court-schedule` agrega acoplamiento** -> unidireccional y sin ciclo; espeja una relacion de negocio real (la reserva depende del calendario).
- **Chequeo de superposicion sigue siendo check-then-act** (sin constraint en base) -> deuda ya conocida (punto 26 de la bitacora); el 409 cubre el caso comun. No se aborda aca.
- **Swagger publico** -> solo dev/portfolio; si molesta, se mueve a un perfil.
- **Seed con password conocida** -> es dev, no secreto; no versionar secretos reales.

## Migration Plan

- Sin migracion de base (no hay cambio de esquema); `ddl-auto=update` no altera columnas.
- Despliegue: `mvn compile` y correr con perfil `dev` para el seed. Rollback: revertir codigo (sin estado que deshacer).
- Postman: el alta de reserva deja de llevar `reserved_by_user_id`; el token sigue tomando el principal. Ajustar collecciones existentes.

## Open Questions

- Forma final del DTO de identidad: `UserResponseDTO` anidado dentro de la respuesta de auth vs. aplanar campos. Se resuelve en la implementacion sin afectar specs.
