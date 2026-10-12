# Proposal

## Why

El backend está pensado como API para Postman: el contrato tiene huecos que un cliente browser (React + Vite) no puede salvar solo, y el dominio central del negocio (qué franjas están disponibles para reservar) no se expone. Antes de escribir el frontend conviene cerrar esos huecos para no duplicar lógica de negocio en el cliente ni topar con bloqueos de infraestructura (CORS/preflight) en la primera request real.

## What Changes

- **CORS**: permitir el origen del dev server de Vite (`http://localhost:5173`) y agregar `PATCH` a los métodos permitidos, hoy ausente aunque `/reservations/{id}/cancel|status|payment` son `PATCH`.
- **Identidad**: `/auth/me` (y el login) pasan a devolver el perfil completo del usuario autenticado (`id`, `username`, `email`, `name`) además de sus authorities, para que el front sepa quién es sin un endpoint extra.
- **Alta de reserva segura**: `reserved_by_user_id` deja de tomarse del body y se ata al usuario autenticado (el token); se cierra el alta anónima de reservas "a nombre de otro".
- **Descubrimiento admin**: nuevos endpoints de lectura para listar usuarios y el catálogo de roles, necesarios para asignar `user_venue_role` desde un panel.
- **Disponibilidad**: nueva regla de dominio (una reserva debe caer dentro de un `court_schedule` habilitado de la cancha) y un endpoint de disponibilidad que devuelve, para una cancha y fecha, qué franjas están libres y cuáles tomadas.
- **Documentación**: OpenAPI/Swagger UI para consultar y probar el contrato visualmente.
- **Datos de desarrollo**: seed de datos de ejemplo y bootstrap del `ADMIN` para que el front no arranque contra una base vacía.

Breaking changes (**BREAKING**):
- El body de alta de reserva ya no debe incluir `reserved_by_user_id` (pasa a derivarse del token).
- El shape de `/auth/me` y del login cambia (agregan datos del usuario).

## Capabilities

### New Capabilities

- `api-cors`: acceso cross-origin del frontend (SPA) al API, incluyendo el preflight de los métodos usados (`GET/POST/PUT/PATCH/DELETE/OPTIONS`).
- `user-identity`: consulta de la identidad propia del usuario autenticado (perfil + roles) a partir del token.
- `user-directory`: descubrimiento, por parte del personal autorizado, del listado de usuarios y del catálogo de roles para gestionar membresías de sede.
- `court-availability`: definición de las franjas reservables de una cancha (a partir de `court_schedule` + `time_block`) y cálculo de disponibilidad (libre/ocupada) para una fecha.
- `reservation-booking`: alta de reserva con propiedad (`reserved_by_user`) derivada del usuario autenticado y validada contra el calendario de la cancha.

### Modified Capabilities

<!-- Sin specs previas: todas las capacidades son nuevas. -->

## Impact

- `auth-module`: `config/CorsConfigurations.java`, `dto/AuthResponse.java`, `dto/…` de identidad, `controller/AuthController.java`.
- `reservation-module`: `controller/ReservationController.java`, `service/ReservationService.java`, `dto/ReservationDTO.java`, nuevos DTOs de disponibilidad; depende de `court-schedule-module` para la nueva regla.
- `court-schedule-module`: nueva consulta por cancha + día (`repository`) para soportar la disponibilidad.
- `user-module`: nuevos endpoints/controller de lectura de usuarios y roles (`RoleRepository`, `RoleResponseDTO` ya existen).
- `app`: dependencia OpenAPI (springdoc), seed de desarrollo (`src/main/resources`), config.
- Sin cambios de base de datos (no hay `db/` versionada; `ddl-auto=update` sigue mandando).
- Fuera de alcance (diferido): paginación, refresh token/logout, validación con `@Valid` y contrato de errores unificado.
