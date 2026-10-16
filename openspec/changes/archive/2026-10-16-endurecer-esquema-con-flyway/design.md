# Design

## Context

Ver `proposal.md`. Estado que condiciona el enfoque:

- `ddl-auto=update` (única config versionada, en `app/src/main/resources/application.properties`) es hoy la única fuente del esquema. `db/schema-reference.sql` existe como insumo "fuera del classpath" para adoptar Flyway; su contenido refleja las entities normalizadas, no el dump real de dev.
- El perfil `test` (`app/src/test/resources/application-test.properties`) usa `create-drop` sobre el Postgres efímero de Testcontainers: el esquema de los tests **no** es el real.
- El chequeo de solapamiento de reservas vive solo en `ReservationService` (check-then-act) contra `(cancha, fecha, franja)` activa; `GlobalExceptionHandler` ya mapea `DataIntegrityViolationException` → 409.
- Domain values confirmados en `ReservationService`: `status` ∈ {PENDING, CONFIRMED, CANCELLED}, `payment_status` ∈ {PENDING, PAID, REFUNDED}; `day_of_week` 0–6.
- `User.updated_at` (y sus DTOs) nunca se setea; `User` mapea a la tabla `"user"` (palabra reservada, requiere comillas en SQL crudo).
- Los módulos comparten el package base y `app` es el único con `@SpringBootApplication` y con `DataSource`; los tests de integración viven allí.

## Goals / Non-Goals

**Goals:**

- Flyway como dueño único del esquema en dev y test; `ddl-auto=validate`.
- Esquema endurecido desde el baseline: `NOT NULL`, `CHECK` de dominios, invariante anti-doble-reserva real.
- Base de dev recreada limpia (sin el ruido de secuencias) y alineada con las entities.

**Non-Goals:**

- Migrar/preservar datos de la base de dev actual (se recrea; el seed repuebla).
- Cambiar el contrato HTTP de ningún endpoint (los códigos 400/409/404 no se mueven).
- Adoptar un entorno de producción real (backups, zero-downtime, perf): no aplica a un proyecto de portfolio con Postgres local.

## Decisions

### D1. Flyway sobre Liquibase

Flyway con migraciones SQL versionadas. Alternativa descartada: Liquibase (changelogs XML/YAML, más abstracción y ceremonia). El proyecto ya piensa en SQL (`db/schema-reference.sql`) y Flyway es el default de facto en Spring Boot; menor superficie para defender en entrevista.

### D2. Las migraciones viven en `app/src/main/resources/db/migration`

Único módulo ejecutable y único con `DataSource`. Alternativa descartada: un módulo `db-migrations` aparte — agrega un módulo y un `classpath` que solo `app` consume, sin beneficio.

### D3. Un único `V1__baseline.sql` ya endurecido (sin `V2` de `ALTER`)

Como la base de dev se recrea desde cero y no hay datos que preservar, el baseline se escribe directamente con el esquema limpio y endurecido. Alternativa descartada: `V1` = esquema actual + `V2+` de `ALTER` + backfill — es el patrón correcto en producción con datos, pero acá sería historia artificial (una tabla recién creada y alterada en la migración siguiente). Se documenta que en un entorno con datos reales correspondería `V2` con backfill.

### D4. Anti-doble-reserva con índice único parcial, no `EXCLUDE`

El invariante exacto del modelo es: no puede haber dos reservas activas con la misma `(fk_court_id, date, fk_time_block_id)`. Se implementa con:

```sql
CREATE UNIQUE INDEX reservation_active_slot_unique
    ON platform.reservation (fk_court_id, date, fk_time_block_id)
    WHERE status <> 'CANCELLED';
```

Es exactamente lo que el service valida (`findByCourt_IdAndDateAndStatusNot(..., CANCELLED)`), corre en la base y cierra la race.

Alternativa considerada y **descartada**: `EXCLUDE USING gist` con `btree_gist` sobre un rango horario. Un `EXCLUDE` sobre rangos requiere que el rango sea una **columna de la propia tabla `reservation`**; no puede referenciar `time_block.start_time/end_time` (otra tabla). Eso obligaría a denormalizar el rango en `reservation` (columna o trigger), acoplando la reserva al rango de la franja y abriendo drift. Además exige `CREATE EXTENSION btree_gist` (privilegio elevado; riesgo en el Postgres local y en el container). Como las reservas referencian una franja **discreta** (no un rango libre), el índice único parcial expresa el invariante sin esas concesiones. Queda como mejora futura si alguna vez se permiten rangos horarios libres.

### D5. `ddl-auto=validate` en dev y test; la integración corre Flyway

La app deja de mutar el esquema. Los tests de integración dejan de usar `create-drop` y levantan Flyway contra el Postgres efímero: ahora validan el **esquema real**. Spring Boot ordena Flyway antes de inicializar JPA, así que `validate` ve el esquema ya migrado.

### D6. Renombrar `"user"` → `app_user`

`user` es palabra reservada y obliga a comillas en todo SQL crudo (`db/schema-reference.sql` ya las usa). Se renombra a `app_user` en `@Table` y en la migración. Las consultas JPQL (`User`) y los derived queries (`findByEmail`, etc.) no cambian: usan el nombre de la entity, no el de la tabla.

### D7. Eliminar `User.updated_at`

No se setea nunca (no hay `@PreUpdate`) y no hay requisito que lo use. Se elimina de la columna, de `User`, y de `UserDTO` / `UserResponseDTO`. Alternativa descartada: mantenerlo con `@PreUpdate` — agrega comportamiento no pedido y una fuente de verdad más.

### D8. `db/schema-reference.sql` se retira

Existía como insumo para el `V1`. Una vez que el `V1` es real, mantener ese archivo duplica la fuente de verdad. Se elimina y `AGENTS.md` / bitácora apuntan a `app/src/main/resources/db/migration/`.

### D9. `NOT NULL` y `CHECK` — alcance

`NOT NULL` en FKs y columnas de negocio obligatorias (FKs de las tablas de unión, `court.fk_venue_id/name`, `time_block.fk_venue_id/block_group/label/start_time/end_time`, `court_schedule.fk_court_id/fk_time_block_id/day_of_week/is_enabled`, `reservation.date/fk_court_id/fk_time_block_id/fk_reserved_by_user_id/status/payment_status`, `role.name`, y los `created_at`/`is_active` que los constructores ya setean). `CHECK`: `status IN (...)`, `payment_status IN (...)`, `day_of_week BETWEEN 0 AND 6`, `start_time < end_time`. Las entities se alinean con `@Column(nullable = false)` como documentación (Hibernate `validate` no lo exige, pero evita divergencia futura).

## Risks / Trade-offs

- **`validate` falla si el `V1` no coincide 1:1 con la expectativa de Hibernate** (tipos `int2`/`int4`/`time`/`timestamp`, schema `platform`) → mitigación: el `V1` se deriva de `db/schema-reference.sql` (que ya nace de las entities) y la verificación es arrancar contexto + correr la integración; cualquier desalineación aparece al instante.
- **`NOT NULL`/`CHECK` nuevos chocan con datos viejos de dev** → mitigación: la base se recrea desde cero por decisión D3; no hay backfill que fallar.
- **Test de concurrencia propenso a flakiness** → mitigación: el escenario afirma la invariante (a lo sumo una reserva activa persiste; la otra da 409 vía `DataIntegrityViolationException`), no un timing específico; y se puede complementar con un test directo al índice único.
- **`app_user` vs `user` en SQL suelto no encontrado** → mitigación: grep de `"user"` en `.sql`/JPQL nativo antes de cerrar; los repositorios usan JPQL/derived queries (nombre de entity) y no se tocan.
- **Dependencia nueva (Flyway) y orden de beans** → mitigación: solo `app` la declara; Spring Boot ya garantiza el orden Flyway → JPA sin configuración extra.

## Migration Plan

1. Agregar `flyway-core` y `flyway-database-postgresql` a `app/pom.xml`.
2. Crear `app/src/main/resources/db/migration/V1__baseline.sql` (schema `platform`, esquema limpio + endurecido + `app_user`).
3. `application.properties`: `ddl-auto=validate`; dejar que Flyway corra (habilitado por defecto con la dependencia).
4. `application-test.properties`: `ddl-auto=validate` y quitar `create-drop` (Flyway arma el esquema en el container).
5. Alinear entities (`app_user`, `nullable = false`) y quitar `updated_at` de `User`/`UserDTO`/`UserResponseDTO`.
6. En dev: `DROP SCHEMA platform CASCADE;` (o borrar la base) y arrancar con perfil `dev`; Flyway crea `platform` y `DevDataSeeder` repuebla.
7. Eliminar `db/schema-reference.sql`; actualizar `AGENTS.md` y bitácora.

**Rollback**: revertir `application.properties` a `ddl-auto=update`, borrar `db/migration/V1__baseline.sql` y las dependencias Flyway; volver a `create-drop` en test. La base se reconstruye con Hibernate como antes (se pierde el endurecimiento, no los datos de dev, que son descartables).

## Open Questions

Ninguna bloqueante: el alcance de `NOT NULL`/`CHECK` y el mecanismo anti-doble-reserva quedan fijados arriba (D4, D9).
