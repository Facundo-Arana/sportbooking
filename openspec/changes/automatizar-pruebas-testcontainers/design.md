# Design

## Context

Ver `proposal.md - Why`. Restricciones que condicionan el diseño:

- Monolito modular Maven: cada dominio es un jar; `app` es el único jar ejecutable y el único con `@SpringBootApplication`, así que solo ahí corre un `@SpringBootTest` de contexto completo. Los tests unitarios van junto a su clase en cada módulo.
- El `pom.xml` raíz es el parent y declara un bloque `<dependencies>` que **todos los módulos heredan**: poner `spring-boot-starter-test` ahí (scope `test`) lo deja disponible en todo el build sin repetirlo.
- Sin `src/test` y sin ninguna dependencia de test declarada hoy.
- Postgres **nativo** en dev, con usuarios y datos, schema `platform`. No hay script de esquema en el repo ni migrador.
- El DDL "real" (`platform`) es inconsistente por haber crecido por etapas: mezcla `bigserial` + secuencias explícitas redundantes (`court_id_seq` se crea a mano y `bigserial` intenta crearla de nuevo → falla en base nueva) y tablas con `IDENTITY` cuyas secuencias quedan huérfanas. Las entities usan **todas** `GenerationType.IDENTITY`, y las FK de nombre hash (`fk2pik…`) confirman que el esquema lo generó Hibernate con `ddl-auto=update`.
- Seguridad: JWT stateless; `JwtFilter` resuelve el usuario por email (`CustomUserDetails.getUsername()`) y arma un principal `CustomUserDetails`. `@PreAuthorize` llama al bean `@venueAuthorization` **por nombre** dentro de un string SpEL. `VenueAuthorization.currentUser()` exige que el principal sea `CustomUserDetails` (si no, devuelve `null` y todos los `canManage*` dan `false`).
- Java 17 en el build (el JDK local es 21, compatible).

## Goals / Non-Goals

**Goals:**
- Correr tests automatizados con un Postgres real y efímero (Testcontainers), sin tocar el Postgres de dev ni requerir una base preparada a mano.
- Cubrir la lógica de negocio (services, unit) y el contrato HTTP + seguridad (controllers, slice) y los escenarios de las specs (integración end-to-end).
- Dejar el esquema de test prolijo y reproducible, sin heredar el desorden del DDL actual.
- Aprender/sentar Docker + Testcontainers como base reutilizable.

**Non-Goals:**
- Unificar dev y test: el Postgres de dev queda nativo, intacto (decisión explícita del dueño).
- Adoptar Flyway/Liquibase en dev (deuda #3): se archiva el DDL como insumo para más adelante.
- Reemplazar la verificación manual por completo; se complementa.
- Tests de carga, performance o UI (frontend). Cobertura del 100%.

## Decisions

### D1. Testcontainers **solo para tests**; el Postgres de dev no se toca

Se levanta un `PostgreSQLContainer` efímero durante `mvn test`; dev sigue con su Postgres nativo.
- **Por qué**: evita migrar datos existentes, cambia cero el workflow diario y elimina el riesgo de perder la base de dev. Es la decisión del dueño ("no complicar el desarrollo").
- **Alternativa descartada**: mover el Postgres de dev a `docker-compose` y unificar dev+test. Más aprendizaje de Docker, pero suma migración de datos y fricción diaria; no es necesaria para tener tests reproducibles.

### D2. Esquema de test por `create-drop` de Hibernate, no por el DDL actual

En el perfil `test`: `spring.jpa.hibernate.ddl-auto=create-drop` + `hibernate.default_schema=platform` + `hibernate.hbm2ddl.create_namespaces=true`.
- **Por qué**: las entities son todas `IDENTITY` y el esquema real ya lo generó Hibernate, así que `create-drop` reconstruye un esquema **funcionalmente equivalente** (tablas, FK, UNIQUE salen de las anotaciones) sin heredar la basura de secuencias del dump, y con **cero mantenimiento** (cambia una entity, el esquema se regenera). El DDL compartido, tal como está, no corre en una base nueva (secuencias duplicadas).
- **Alternativa descartada**: usar el DDL como `schema.sql`/migración de test. Más fiel byte a byte, pero exige limpiar el dump primero (unificar `bigserial` vs `IDENTITY`, borrar secuencias huérfanas) y mantenerlo en cada cambio de modelo. Se pospone a la adopción real de Flyway.
- **Nota**: el DDL se guarda como referencia (`db/schema-reference.sql`, fuera del classpath del build) para la deuda #3.

### D3. `@ServiceConnection` para autoconfigurar el DataSource

El container se declara con `@Container @ServiceConnection`; Spring Boot 3.4 (parent) administra la versión de Testcontainers.
- **Por qué**: provee `ConnectionDetails` con precedencia sobre `application.properties`, así los placeholders de `${DB_*}` y el `spring.datasource.*` de dev no interfieren ni hay que hardcodear URL/usuario/password en el test.
- **Alternativa descartada**: `@DynamicPropertySource` manual. Más código y más frágil ante cambios.

### D4. Patrón *singleton container* + perfil `test`

El container arranca una vez por JVM (patrón singleton) y se reusa entre clases de test; los tests de integración usan `@ActiveProfiles("test")`.
- **Por qué**: levantar un container por clase es lento; uno por corrida es el equilibrio costo/aislamiento estándar. `DevDataSeeder` es `@Profile("dev")`, así que **no** corre en test: los tests siembran su propio fixture.
- **Alternativa descartada**: un container por clase (`@Testcontainers` en cada clase). Más lento y sin beneficio real acá.

### D5. Estrategia de autenticación en los tests: real en integración, factory en slice

Los tests de integración hacen **login real** contra `/auth` y usan el JWT devuelto (ejercita `JwtFilter` + `CustomUserDetails` de verdad). Los tests de slice usan una **factory `@WithSecurityContext`** que arma un `CustomUserDetails` a partir de un `User` de prueba.
- **Por qué**: `@WithMockUser` produce un principal que **no** es `CustomUserDetails`, así que `VenueAuthorization.currentUser()` devuelve `null` y todos los `canManage*` dan `false` → tests de autorización con 403 "falso". La factory resuelve eso en slice; el login real da máxima fidelidad en integración.
- **Alternativa descartada**: `@WithMockUser` en todo. Falso negativo silencioso, justo en lo que más duele testear (autorización).

### D6. Ubicación de los tests

Unit y slice junto a la clase en cada módulo; integración (contexto completo + Testcontainers) en `app` (único con `@SpringBootApplication`).
- **Por qué**: Maven reactor corre los tests por módulo; el contexto completo solo existe en `app`. Los slices (`@WebMvcTest`) mockean el service y proveen el bean `@venueAuthorization`.
- **Alternativa descartada**: todo en `app`. Acopla tests de unidad a un contexto y pierde el aislamiento por módulo.

### D7. Datos de test por fixture explícito (no reusar dev seed)

Un helper/builder de datos de test (usuarios, venue, canchas, franjas, calendario) por integración, con limpieza entre tests.
- **Por qué**: el container arranca vacío; cada test controla su estado. Reusar `DevDataSeeder` acoplaría los tests al perfil dev.
- **Alternativa descartada**: `@Transactional` con rollback universal. Sirve para unit/`@DataJpaTest`, pero MockMvc que pasa por la cadena de filtros y siembra vía repositorios necesita control explícito.

## Risks / Trade-offs

- **Docker no instalado hoy (ni WSL2)** → es un prerrequisito manual (Fase 1 de tasks); hasta que exista, los tests de integración no corren. Mitigación: los unit/slice no dependen de Docker y se pueden escribir igual.
- **Fidelidad `create-drop` vs DDL real** → el esquema de test puede diferir en detalles no observables por la app (nombres de FK, defaults). Mitigación: los comportamientos testeados son de la capa de aplicación; el DDL queda archivado para Flyway.
- **`@PreAuthorize` por nombre + seguridad** → si la factory o el contexto de slice no exponen el bean `@venueAuthorization`, el contexto falla. Mitigación: incluirlo explícitamente en la configuración del slice.
- **Lentitud en la primera corrida** (pullear imagen Postgres) → Mitigación: `testcontainers.reuse.enable=true` en `~/.testcontainers.properties` y patrón singleton.
- **Windows + Docker Desktop** → timeouts de arranque ocasionales. Mitigación: imagen liviana (`postgres:16-alpine`), y documentar el comando de diagnóstico (`docker info`).

## Migration Plan

- Sin migración de datos ni de esquema de producción. El cambio es aditivo: dependencias de test, `src/test`, config de test.
- Rollback: revertir los archivos (todo es de test; no hay estado que deshacer).
- Dev: `application-local.properties` y el Postgres nativo quedan igual. Los tests no lo tocan.

## Open Questions

- Ninguna que afecte el enfoque o el desglose: la estrategia de auth (D5), la ubicación (D6) y el esquema (D2) quedan fijadas acá. El `start_time` del seed exacto de cada fixture se decide al implementar cada test.
