# Tasks

## 1. CORS para el SPA

- [x] 1.1 Externalizar `allowedOrigins` y `allowedMethods` a propiedades (`app.cors.allowed-origins`, default `http://localhost:5173`) y agregar `PATCH` en `auth-module/.../config/CorsConfigurations.java`. Verificar con `.\mvnw.cmd -q -DskipTests compile` y un preflight real: `OPTIONS` a `/reservations/1/cancel` desde el origen del SPA responde autorizando `PATCH` (via Swagger/navegador o `curl`).
- [x] 1.2 Agregar la entrada numerada en `bitacora-tecnica-sportbooking.md` (sintoma/causa/fix/archivos/concepto) y confirmar que los archivos listados coinciden 1 a 1 con los tocados.

## 2. OpenAPI / Swagger UI

- [x] 2.1 Agregar `springdoc-openapi-starter-webmvc-ui` (v2.x) en `app/pom.xml` y `permitAll` para `/swagger-ui/**` y `/v3/api-docs/**` en `auth-module/.../config/SecurityConfiguration.java`. Verificar `mvn compile`, arrancar la app y que `/swagger-ui/index.html` responda 200 con el listado de endpoints.
- [x] 2.2 Configurar el esquema bearer JWT en OpenAPI y verificar desde Swagger que "Authorize" con un token permite llamar a `/auth/me` y devuelve 200.
- [x] 2.3 Agregar la entrada de bitacora correspondiente, con archivos 1 a 1.

## 3. Identidad del usuario

- [x] 3.1 Crear el DTO de identidad y modificar `AuthController.login` y `/auth/me` para devolver `id`, `username`, `email`, `name` y `authorities`. Verificar que login y `/auth/me` devuelven el usuario completo, y que `/auth/me` sin token responde 401.
- [x] 3.2 Agregar la entrada de bitacora correspondiente, con archivos 1 a 1.

## 4. Alta de reserva segura

- [x] 4.1 Quitar `reserved_by_user_id` de `ReservationDTO` y derivar el propietario del usuario autenticado en `ReservationService.create` (via principal + `UserRepository`). Verificar que un `POST /reservations` sin ese campo crea la reserva con `reservedByUser` = autenticado, y que enviarlo en el body no cambia el propietario.
- [x] 4.2 Agregar la entrada de bitacora (marcando el cambio breaking de la colleccion Postman) con archivos 1 a 1.

## 5. Directorio de usuarios y roles

- [x] 5.1 Crear `UserController` en `user-module` con `GET /users?q=` restringido a `hasRole('ADMIN')`. Verificar 200 como ADMIN (sin `password_hash`, con filtro aplicado), 403 como usuario sin ADMIN y 401 sin token.
- [x] 5.2 Crear `RoleController` en `user-module` con `GET /roles` (autenticado). Verificar 200 con roles y 401 sin token.
- [x] 5.3 Actualizar `AGENTS.md` (cambia la convencion "no existe UserController") y agregar la entrada de bitacora, con archivos 1 a 1.

## 6. Regla: la reserva respeta el calendario

- [x] 6.1 Agregar `court-schedule-module` como dependencia de `reservation-module` en su `pom.xml`. Verificar `.\mvnw.cmd -pl reservation-module -am compile`.
- [x] 6.2 Implementar la consulta de franja reservable en `court-schedule-module` y usarla en `ReservationService.create`/`update`: rechazar (400) una franja fuera del calendario habilitado del dia. Verificar que una reserva en franja valida sigue funcionando y que una fuera del calendario responde 400.
- [x] 6.3 Agregar la entrada de bitacora correspondiente, con archivos 1 a 1.

## 7. Endpoint de disponibilidad

- [x] 7.1 Crear el DTO de disponibilidad y `GET /courts/{id}/availability?date=YYYY-MM-DD` (en `reservation-module`), devolviendo `slots` con `status` `free`/`occupied` ordenados por `start_time`. Verificar: franja con reserva activa = `occupied`; reserva `CANCELLED` libera la franja; cancha inexistente = 404.
- [x] 7.2 Agregar la entrada de bitacora correspondiente, con archivos 1 a 1.

## 8. Seed de desarrollo

- [x] 8.1 Crear `DevDataSeeder` (`@Profile("dev")`, idempotente) en `app-module`: ADMIN (password hasheado), venue, canchas, time-blocks, court-schedules y reservas de ejemplo. Verificar arrancando con perfil `dev` que los datos aparecen y que un segundo arranque no los duplica.
- [x] 8.2 Documentar el comando/perfil de seed en `AGENTS.md` y agregar la entrada de bitacora correspondiente, con archivos 1 a 1.

## 9. Verificacion de integracion end-to-end

- [x] 9.1 Recorrer con Swagger el flujo cliente completo (login -> sedes -> canchas -> disponibilidad -> reservar -> cancelar) y el flujo admin (listar usuarios y roles, asignar `user_venue_role`), verificando que cada paso responde segun las specs sin errores 500.

## Workflow follow-up

- Archivar el change una vez satisfechos los requisitos de revision del proyecto.
- Verificar que las specs principales quedaron creadas a partir de los deltas.
