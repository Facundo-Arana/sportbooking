# Tasks

> Convención: cada tarea dice cómo se verifica. Las marcadas **(MANUAL)** las ejecuta el dueño a mano (no el agente).

## 1. Infra Flyway

- [x] 1.1 Agregar `flyway-core` y `flyway-database-postgresql` (scope runtime) a `app/pom.xml`. Verificación: `.\mvnw.cmd -q -pl app -am dependency:tree` lista `flyway-core` y `flyway-database-postgresql`.
- [x] 1.2 En `app/src/main/resources/application.properties`, pasar `spring.jpa.hibernate.ddl-auto` a `validate` y dejar Flyway habilitado (`spring.flyway.enabled=true`). Verificación: arrancando contra una base migrada, Hibernate no emite `create`/`alter` (el log de `ddl-auto` desaparece) y el contexto levanta.

## 2. Baseline V1 endurecido (`app/src/main/resources/db/migration/V1__baseline.sql`)

- [x] 2.1 Escribir el `V1` con el esquema limpio derivado de `db/schema-reference.sql`: schema `platform`, tablas con `IDENTITY` (sin `bigserial` ni secuencias explícitas), tabla `app_user`, y FKs con nombres descriptivos `fk_*`. Verificación: sobre una base vacía, `mvnw -pl app -am spring-boot:run` crea el schema completo y la integración arranca con `validate` en verde.
- [x] 2.2 Agregar `NOT NULL` en FKs y columnas obligatorias (`court.fk_venue_id/name`, `time_block.*`, `court_schedule.fk_*`, `reservation.date/fk_*/status/payment_status`, `role.name`, tablas de unión, `created_at`/`is_active`/`is_enabled`). Verificación: test de integración que intenta persistir una entidad con FK/columna obligatoria nula y afirma rechazo de la base.
- [x] 2.3 Agregar `CHECK`: `status IN ('PENDING','CONFIRMED','CANCELLED')`, `payment_status IN ('PENDING','PAID','REFUNDED')`, `day_of_week BETWEEN 0 AND 6`, `start_time < end_time`. Verificación: tests de integración insertan un valor fuera de dominio por cada `CHECK` y afirman rechazo; un valor válido persiste.
- [x] 2.4 Agregar el índice único parcial anti-doble-reserva: `UNIQUE (fk_court_id, date, fk_time_block_id) WHERE status <> 'CANCELLED'`. Verificación: test de integración que inserta dos reservas activas iguales (una falla) y comprueba que una reserva `CANCELLED` no bloquea una nueva sobre la misma franja.

## 3. Alineación de entities y DTOs

- [x] 3.1 Renombrar la tabla a `app_user` en `User` (`@Table`). Verificación: `.\mvnw.cmd -q -DskipTests compile` en verde y la integración de `user-identity` (login + `/auth/me`) sigue pasando.
- [x] 3.2 Eliminar `updated_at` de `User`, `UserDTO` y `UserResponseDTO`. Verificación: `.\mvnw.cmd -q -DskipTests compile` en verde y la respuesta de `/auth/me` ya no incluye `updated_at`.
- [x] 3.3 Alinear las entities con `@Column(nullable = false)` en las FKs y columnas obligatorias (documentación del modelo). Verificación: `validate` arranca sin errores y `.\mvnw.cmd -q -DskipTests compile` en verde.

## 4. Perfil de test sobre Flyway

- [x] 4.1 En `app/src/test/resources/application-test.properties`, quitar `create-drop` y usar `validate` (Flyway arma el esquema en el container). Verificación: `AbstractIntegrationTest` levanta el contexto con el esquema migrado (no generado) y `.\mvnw.cmd -pl app test` en verde.
- [x] 4.2 Ajustar `IntegrationTestFixture` (limpieza/siembra) al esquema versionado. Verificación: la suite de integración existente (`user-identity`, `user-directory`, `court-availability`, `reservation-booking`, `api-cors`) pasa sin cambios de contrato.
- [x] 4.3 Test de integración de concurrencia: dos altas sobre la misma cancha, fecha y franja reservable → a lo sumo una reserva activa persiste y la otra recibe 409. Verificación: el test pasa de forma determinista.

## 5. Limpieza y documentación

- [x] 5.1 Eliminar `db/schema-reference.sql` (su rol lo cumple el `V1`) y actualizar `AGENTS.md` para apuntar a `app/src/main/resources/db/migration/`. Verificación: no queda ninguna referencia a `db/schema-reference.sql` y el `V1` es la única fuente del DDL.
- [x] 5.2 Agregar la entrada numerada en `bitacora-tecnica-sportbooking.md` (síntoma → causa raíz → fix → archivos modificados → concepto) y marcar resueltas las deudas #2 y #3. Verificación: la entrada existe y los archivos listados coinciden 1 a 1 con el diff.
- [x] 5.3 **(MANUAL)** Recrear la base de dev: `DROP SCHEMA platform CASCADE;` y arrancar con perfil `dev`. Verificación: Flyway crea el schema y `DevDataSeeder` repuebla (ADMIN, sede, canchas, franjas, calendario, reservas) sin error.

## 6. Verificación de integración

- [x] 6.1 Correr `.\mvnw.cmd verify` de punta a punta. Verificación: `BUILD SUCCESS` con unit + slice + integración (Testcontainers, con Flyway) en verde.
- [x] 6.2 **(MANUAL)** En Swagger/Postman, recorrer login → disponibilidad → reservar → cancelar y confirmar que un segundo intento sobre la misma franja responde 409. Verificación: el comportamiento manual coincide con los tests.

## Workflow follow-up

- Archivar el change una vez satisfechos los requisitos de revisión del proyecto.
- Si en el futuro se permiten rangos horarios libres (no franjas discretas), reevaluar el `EXCLUDE USING gist` + `btree_gist` sobre un rango denormalizado (ver design, D4).
