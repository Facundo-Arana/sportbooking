<!--
Sync Impact Report
- Version change: N/A (plantilla sin ratificar) → 1.0.0
- Modified principles: N/A (ratificación inicial)
- Added sections:
  - Core Principles (I. Modularidad por dominio, II. DTOs en el borde,
    III. Errores de negocio traducidos en un único lugar,
    IV. Seguridad por recurso y por campo,
    V. Contrato de persistencia explícito,
    VI. Documentación viva y verificación empírica)
  - Restricciones Técnicas Adicionales
  - Flujo de Desarrollo y Puertas de Calidad
  - Governance
- Removed sections: Ninguna
- Follow-up TODOs: Ninguno. La fecha de ratificación se toma como la fecha de
  creación de esta constitución (2026-10-07).
-->

# Constitución de Sportbooking

## Principios Fundamentales

### I. Modularidad por dominio (Maven multi-módulo)

El sistema DEBE organizarse como un monolito modular Maven. Cada dominio DEBE
ser un módulo jar independiente (`user`, `auth`, `venue`, `court`, `time-block`,
`court-schedule`, `user-venue-role`, `reservation`) y DEBE respetar la estructura
interna fija `entity → dto → exception → repository → service → controller →
mapper`. El módulo raíz `sportbooking` es un mero aggregator (`packaging=pom`) y
`app` es el único jar ejecutable.

Las dependencias entre módulos DEBEN espejar las claves foráneas de la base:
`court`/`time-block → venue`, `court-schedule → court + time-block`,
`user-venue-role → user + venue`, `reservation → court + time-block + user`. Los
módulos DEBEN declarar esas dependencias por `pom.xml` y reutilizar entidades,
repositorios y excepciones del módulo referenciado en lugar de duplicarlos.

Todo módulo nuevo DEBE usar el package base `com.example.sportbooking.<dominio>`
(sin guiones: `timeblock`, `courtschedule`, `uservenuerole`). Los beans
transversales (`GlobalExceptionHandler`, `VenueAuthorization`) DEBEN vivir en
`app-module`, porque es el único que depende de todos los demás; sacarlos crearía
una dependencia circular.

**Rationale**: el component scan depende de un package base compartido y la
dirección de dependencias reproduce las FK reales, lo que mantiene el grafo
acoplado y sin ciclos.

### II. DTOs en el borde: el modelo de API no es el modelo de persistencia

Los controllers NUNCA DEBEN devolver entidades JPA. DEBEN devolver Response DTOs
mapeados con MapStruct (`*Mapper.toResponseDTO`). El mapeo DEBE ocurrir en el
controller, no en el service; el service DEBE devolver entidades.

**Rationale**: mapear en el borde evita filtrar campos sensibles
(`password_hash`), desacopla el contrato HTTP del schema de base y evita romper
la API al cambiar una entidad. MapStruct genera el mapeo en compilación, por lo
que un error de tipos se detecta al compilar.

### III. Errores de negocio traducidos en un único lugar

Todas las excepciones DEBEN traducirse a HTTP exclusivamente en
`GlobalExceptionHandler` (`app-module`): `*NotFoundException` → 404,
`IllegalArgumentException` → 400, `IllegalStateException` → 409,
`DataIntegrityViolationException` → 409. Los services NUNCA DEBEN conocer HTTP y
los controllers NUNCA DEBEN envolver la lógica en `try/catch`; cada service DEBE
lanzar la excepción de negocio correcta.

**Rationale**: un único punto de traducción mantiene consistente el contrato de
errores y desacopla la capa de negocio del protocolo de transporte.

### IV. Seguridad por recurso y por campo

La seguridad DEBE ser stateless con JWT. Solo `/auth/**` y el preflight
`OPTIONS /**` son públicos; todo lo demás requiere autenticación. La autorización
de recursos DEBE implementarse con `@PreAuthorize` y SpEL que invoca al bean
`@venueAuthorization` por nombre, sin crear dependencia directa de `app`.
`@EnableMethodSecurity` es obligatorio. El bypass `hasRole('ADMIN')` DEBE
componerse aparte en cada `@PreAuthorize`. CORS DEBE permitir solo
`http://localhost:4200` y engancharse vía `HttpSecurity.cors()`; NUNCA DEBE
declararse un `CorsFilter` suelto.

Cuando una acción cambia una dimensión contra la que se autoriza (venue) o un
campo reservado al personal (`status`, `payment_status`), el diseño DEBE hacer
estructuralmente imposible el caso: el campo DEBE ser inmutable vía `PUT` y la
acción DEBE exponerse como endpoint por intención (`PATCH .../cancel`, `.../status`,
`.../payment`).

**Rationale**: la autorización previa a la ejecución no protege contra campos
mutables en el mismo request; volver inmutable el campo o mover la acción a un
endpoint dedicado elimina el mass assignment por construcción (OWASP API3).

### V. Contrato de persistencia explícito

`created_at`, `status`, `payment_status` e `is_active` DEBEN setearse a mano en el
constructor de alta, sin confiar en defaults de columna. El update DEBE ser
parcial: solo se pisan los campos no-null del DTO y las reglas cruzadas DEBEN
re-validarse contra el estado final de la entity, no contra el payload.
`venue_id` DEBE ser inmutable en `PUT` (mismo valor = no-op, valor distinto =
400). Los nombres de campo DEBEN mantener snake_case cuando reflejan la columna.
Los tipos DEBEN ser fieles a la columna (`LocalTime` para `time`, `Short` para
`day_of_week`, `LocalDate` para fecha, `Boolean` wrapper en campos opcionales de
update parcial).

Los constraints de FK DEBEN dejarse exclusivamente a Hibernate con
`ddl-auto=update`; NUNCA DEBEN agregarse a mano FK con un nombre distinto (crea
duplicados sobre la misma columna). Queda prohibido mezclar DDL manual con
`ddl-auto`.

**Rationale**: Hibernate envía `NULL` explícito y pisa los defaults de Postgres;
el update parcial correcto exige validar el estado resultante, y `ddl-auto=update`
no reconoce nombres de constraint manuales.

### VI. Documentación viva y verificación empírica

Todo cambio importante DEBE agregar o actualizar una entrada numerada en
`bitacora-tecnica-sportbooking.md` con la secuencia síntoma → causa raíz → fix →
archivos modificados (ruta completa) → concepto para profundizar. Los archivos
listados DEBEN coincidir 1 a 1 con lo realmente tocado. Todo cambio DEBE incluir
la explicación conceptual detrás.

Hoy el proyecto no tiene tests automatizados. Por lo tanto NINGÚN documento DEBE
afirmar que `mvn test` verifica algo. La verificación real DEBE ser compilar
(`mvnw.cmd -q -DskipTests compile`) y probar los endpoints manualmente.

**Rationale**: el proyecto es de portfolio y debe defenderse en entrevistas;
claridad por sobre atajos. Afirmar cobertura inexistente destruye la credibilidad
de la documentación.

## Restricciones Técnicas Adicionales

- **Stack**: Java 17 fijado en el `pom.xml` raíz (`java.version`, source/target).
  Aunque el stack objetivo mencione Java 21, el build real es 17: no usar
  features de 21 sin actualizar la config. Spring Boot con Maven.
- **Lombok + MapStruct**: `lombok-mapstruct-binding` DEBE permanecer en
  `annotationProcessorPaths` del `pom.xml` raíz.
- **Base de datos**: PostgreSQL local, schema `platform`, `ddl-auto=update`, sin
  Flyway/Liquibase ni scripts SQL versionados. No hay seed de datos; probar la
  autorización requiere crear a mano un `ADMIN` en `user_global_role`.
- **Configuración**: un único `application.properties` versionado, en `app`
  (puerto 8081). La config versionada DEBE contener solo placeholders
  (`${DB_PASSWORD:}`, `${JWT_SECRET:}`, etc.); los secretos se resuelven por
  variable de entorno (prioridad) o por `application-local.properties` (no
  versionado, importado con `spring.config.import`). El `spring-boot-maven-plugin`
  corre con `workingDirectory` = root del repo.
- **Duplicación aceptada**: `validateSameVenue` / `validateVenueUnchanged` están
  repetidos a propósito en dos services; se extraen recién a la 3ra necesidad
  real. No abstraer antes.

## Flujo de Desarrollo y Puertas de Calidad

- El asistente DEBE modificar directamente los archivos del repo; no se entregan
  listados de archivos ni zips. El usuario testea después con `mvn compile` +
  Postman.
- Las features DEBEN especificarse antes de implementarse mediante el flujo Spec
  Kit (`/speckit.specify` → `/speckit.plan` → `/speckit.tasks` →
  `/speckit.implement`), tomando esta constitución como marco de gobierno.
- Puertas de calidad por cambio: (1) compila sin errores; (2) los endpoints
  responden con el contrato esperado en Postman; (3) no se exponen entidades; (4)
  los errores respetan el mapeo canónico; (5) la autorización corresponde al rol
  y venue; (6) la bitácora quedó actualizada.
- No hay lint ni formatter configurados; no inventar comandos de verificación que
  no existan.

## Governance

Esta constitución prevalece sobre cualquier otra práctica o convención del
proyecto. `AGENTS.md` y `bitacora-tecnica-sportbooking.md` son la guía operativa
de menor nivel y NO DEBEN contradecir estos principios.

- **Enmiendas**: cualquier cambio DEBE documentarse (qué principio cambia y por
  qué), aprobarse por el dueño del proyecto y reflejarse en la bitácora. Las
  enmiendas que remuevan o redefinan principios de forma incompatible con lo
  vigente son un bump MAJOR.
- **Versionado**: semántico `MAJOR.MINOR.PATCH`. MAJOR = remoción o redefinición
  incompatible de un principio; MINOR = nuevo principio o sección, o expansión
  material de la guía; PATCH = aclaraciones, redacción o correcciones no
  semánticas.
- **Cumplimiento**: toda feature y todo PR DEBEN verificarse contra estos
  principios. La complejidad no justificada DEBE rechazarse. Ante conflicto entre
  la constitución y una convención previa, prevalece la constitución.
- **Revisión**: la constitución DEBE revisarse cuando cambie el stack, la
  arquitectura de módulos o el flujo de trabajo; idealmente al cierre de cada
  feature.

**Version**: 1.0.0 | **Ratified**: 2026-10-07 | **Last Amended**: 2026-10-07
