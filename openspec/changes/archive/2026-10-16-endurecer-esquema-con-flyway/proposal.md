# Proposal

## Why

Hoy `ddl-auto=update` es dueño único del esquema: crea constraints con nombres generados, altera la base en runtime y **falla en silencio** ante datos que violan un constraint nuevo. El esquema creció por etapas y arrastra ruido (mezcla `IDENTITY`/`bigserial`, secuencias huérfanas, columnas muertas) y varias invariantes de negocio solo viven en el service (check-then-act), así que no se sostienen ante concurrencia. Cerrar la deuda #3 (migrador versionado) y la #2 (endurecer el esquema) es el último bloque de deuda de datos del proyecto, y habilita a los tests de integración a validar el esquema real en vez de uno generado por Hibernate.

## What Changes

- Adoptar **Flyway** como dueño único del esquema (deuda #3): `V1__baseline.sql` derivado de `db/schema-reference.sql`; la app pasa de `ddl-auto=update` a `ddl-auto=validate` y deja de mutar el esquema en runtime.
- **BREAKING (solo dev)**: la base de dev se **recrea desde cero** (no se preservan datos); `DevDataSeeder` repuebla el estado de ejemplo.
- Endurecer el esquema (deuda #2):
  - `NOT NULL` en las FKs y en las columnas de negocio obligatorias.
  - `CHECK` sobre dominios controlados: `reservation.status`, `reservation.payment_status`, `court_schedule.day_of_week` (0–6) y `time_block.start_time < end_time`.
  - Constraint de exclusión de Postgres (`EXCLUDE USING gist` + extensión `btree_gist`) que impide **solapar reservas activas** de una misma cancha: cierra el check-then-act de doble reserva (el 409 deja de depender solo de la validación de aplicación).
  - Limpieza: renombrar la tabla `"user"` (palabra reservada) a `app_user`; eliminar `updated_at` (nunca se setea); FKs con nombres descriptivos `fk_*`; tipos `IDENTITY` consistentes y sin secuencias huérfanas.
- Alinear las entities JPA al esquema (`@Column(nullable = false)`, `@Table(name = "app_user")`) y el perfil de test para correr Flyway contra el Postgres efímero de Testcontainers.

## Capabilities

### New Capabilities

- `database-migrations`: el esquema se define y versiona con migraciones Flyway inmutables; la app solo valida el esquema al arrancar (`validate`) y no lo altera; el baseline `V1` describe el esquema limpio (sin el ruido de la base de dev).
- `schema-integrity`: invariantes garantizadas por la base — `NOT NULL` en FKs y columnas obligatorias, y `CHECK` de los dominios controlados — de modo que datos inválidos no puedan persistirse aunque la capa de servicio los omita.

### Modified Capabilities

- `reservation-booking`: el requisito de rechazar el alta sobre una franja ya ocupada pasa a estar respaldado por un constraint de exclusión a nivel base, por lo que la garantía vale también bajo concurrencia (no solo en el camino feliz de la validación de aplicación).

## Impact

- **Build**: `app/pom.xml` (dependencias `flyway-core` y `flyway-database-postgresql`; versiones gestionadas por el BOM de Spring Boot 3.4). `pom.xml` raíz solo si hay que fijar/overridear versión.
- **Config**: `app/src/main/resources/application.properties` (Flyway habilitado + `ddl-auto=validate`); `app/src/test/resources/application-test.properties` (Flyway en lugar de `create-drop`).
- **Nuevos**: `app/src/main/resources/db/migration/V1__baseline.sql` (y migraciones `V2+` si el endurecimiento se separa del baseline). `db/schema-reference.sql` se reemplaza como fuente: pasa a ser el origen documentado del `V1`.
- **Entidades** (los 8 módulos): renombre `user` → `app_user`, `@Column(nullable = false)` en FKs y columnas obligatorias; sin cambios de contrato HTTP.
- **Seed**: `DevDataSeeder` no cambia su lógica, pero corre contra el esquema versionado.
- **Tests**: la integración (Testcontainers, `app`) ahora levanta Flyway en vez de `create-drop`; unit y slice (sin base) no se ven afectados.
- **Deuda**: cierra #2 y #3 de la bitácora.
