# Tasks

> Convención: cada tarea dice cómo se verifica. Las marcadas **(MANUAL)** las ejecuta el dueño a mano (no el agente).

## 1. Prerrequisitos de entorno (Docker en Windows)

- [ ] 1.1 **(MANUAL)** Instalar WSL2 con `wsl --install` (requiere permisos de admin y reinicio). Verificación: `wsl --status` responde sin error.
- [ ] 1.2 **(MANUAL)** Instalar Docker Desktop con backend WSL2 e iniciarlo. Verificación: `docker run --rm hello-world` imprime el mensaje de éxito.
- [ ] 1.3 **(MANUAL)** Confirmar que el daemon responde. Verificación: `docker info` devuelve `ServerVersion` sin error.
- [ ] 1.4 Documentar los prerrequisitos de entorno (WSL2 + Docker Desktop) y el comando `docker info` en `AGENTS.md`. Verificación: la sección de testing lista el prerrequisito y el comando de diagnóstico.

## 2. Bootstrap de infraestructura de test (Maven + Testcontainers)

- [ ] 2.1 Agregar `spring-boot-starter-test` con scope `test` al `pom.xml` raíz (heredable por todos los módulos). Verificación: `.\mvnw.cmd -q -DskipTests compile` sigue en verde y `.\mvnw.cmd -q -pl app dependency:tree` incluye JUnit 5 y Mockito.
- [ ] 2.2 Agregar en `app/pom.xml` las dependencias de test `spring-boot-testcontainers`, `org.testcontainers:junit-jupiter` y `org.testcontainers:postgresql` (scope `test`). Verificación: `.\mvnw.cmd -q -pl app -am -DskipTests compile` en verde y el `dependency:tree` de `app` lista `testcontainers`.
- [ ] 2.3 Crear `app/src/test/resources/application-test.properties` con `spring.jpa.hibernate.ddl-auto=create-drop`, `hibernate.default_schema=platform`, `hibernate.hbm2ddl.create_namespaces=true` y un `jwt.secret` de test. Verificación: un test trivial con `@ActiveProfiles("test")` levanta el contexto sin fallar por datasource.
- [ ] 2.4 Crear `AbstractIntegrationTest` en `app/src/test` con el patrón singleton de `PostgreSQLContainer` + `@ServiceConnection` + `@SpringBootTest`. Verificación: un test `contextLoads` pasa levantando el container (no el Postgres de dev).
- [ ] 2.5 Documentar en `AGENTS.md` el comando de test (`.\mvnw.cmd test`) y la separación por capas (unit por módulo, slice, integración en `app`). Verificación: la sección existe y describe dónde vive cada tipo de test.
- [ ] 2.6 Guardar el DDL real como `db/schema-reference.sql` (fuera del classpath, sin `@Sql` ni `spring.sql.init`) y anotarlo en `AGENTS.md` como insumo para una futura adopción de Flyway. Verificación: el archivo existe y ningún `spring.sql.init`/`@Sql` lo referencia.

## 3. Tests unitarios de services (Mockito, sin Spring)

- [ ] 3.1 Tests de `ReservationService`: ownership derivado del principal, franja fuera de calendario → 400, solapamiento → 409, `venue_id` inmutable, update parcial y validación de `status`/`payment_status`. Verificación: `.\mvnw.cmd -pl reservation-module test` en verde.
- [ ] 3.2 Tests de `CourtScheduleService`: `isReservable`/`findReservableSlots` con y sin calendario habilitado. Verificación: `.\mvnw.cmd -pl court-schedule-module test` en verde.
- [ ] 3.3 Tests del resto de services CRUD (`Venue`, `Court`, `TimeBlock`, `UserVenueRole`, `User`, `Role`): alta con campo obligatorio faltante → 400, update parcial, not-found → 404. Verificación: `.\mvnw.cmd test` en cada módulo tocado en verde.

## 4. Tests de controllers (slice) y seguridad

- [ ] 4.1 Crear la factory `@WithSecurityContext` que arma un `CustomUserDetails` desde un `User` de prueba. Verificación: un test con esa anotación ve las authorities y `VenueAuthorization.currentUser()` no devuelve `null`.
- [ ] 4.2 Tests de slice (`@WebMvcTest`) de controllers representativos (`Venue`, `Reservation`, `Availability`, `User`, `Role`) con la matriz 401 (sin token) / 403 (sin permiso) / 200 (con permiso). Verificación: los tres códigos se cubren y pasan.
- [ ] 4.3 Tests de `GlobalExceptionHandler`: `IllegalArgumentException`→400, `*NotFoundException`→404, `IllegalStateException`→409. Verificación: cada excepción mapea al HTTP esperado.

## 5. Tests de integración (escenarios de las specs)

- [ ] 5.1 Crear el fixture de datos de test (ADMIN, cliente, venue, canchas, franjas, calendario) con limpieza entre tests. Verificación: un test de humo comprueba que el fixture deja el estado esperado.
- [ ] 5.2 Integración de identidad y directorio: login y `/auth/me` devuelven perfil + authorities; `GET /users?q=` solo ADMIN; `GET /roles` autenticado. Verificación: escenarios de `user-identity` y `user-directory` en verde.
- [ ] 5.3 Integración de disponibilidad: `free`/`occupied`, reserva `CANCELLED` libera la franja, cancha inexistente → 404, orden por `start_time`. Verificación: escenarios de `court-availability` en verde.
- [ ] 5.4 Integración de reserva: ownership desde el token, franja fuera de calendario → 400, franja ocupada → 409, reprogramación inválida. Verificación: escenarios de `reservation-booking` en verde.
- [ ] 5.5 Integración de CORS: el preflight `OPTIONS` autoriza `PATCH`. Verificación: escenario de `api-cors` en verde.

## 6. Verificación de integración y documentación del change

- [ ] 6.1 Correr `.\mvnw.cmd verify` de punta a punta y confirmar que todo pasa sin levantar Docker a mano. Verificación: `BUILD SUCCESS` con los tests ejecutados.
- [ ] 6.2 Agregar la entrada numerada en `bitacora-tecnica-sportbooking.md` (síntoma → causa raíz → fix → archivos modificados → concepto) y confirmar que los archivos listados coinciden 1 a 1 con los tocados. Verificación: entrada presente y coherente con el diff.
- [ ] 6.3 **(MANUAL)** Recorrer en Swagger/Postman un flujo equivalente a la suite (login → disponibilidad → reservar → cancelar) y comparar con lo que afirman los tests. Verificación: el comportamiento manual coincide con los tests.

## Workflow follow-up

- Archivar el change una vez satisfechos los requisitos de revisión del proyecto.
- Retomar la deuda #3 (Flyway/Liquibase) usando `db/schema-reference.sql` como base del `V1__baseline.sql`.
