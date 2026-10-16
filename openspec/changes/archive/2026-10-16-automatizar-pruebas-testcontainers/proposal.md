# Proposal

## Why

Hoy la única verificación del backend es manual (`mvn compile` + Postman/Swagger): cada funcionalidad nueva se prueba a mano y no hay red de seguridad contra regresiones. El proyecto ya tiene specs con escenarios redactados (contrato) y la bitácora lista "testing automatizado" como deuda #1, pero nada los ejecuta. Sumado a eso, el esquema de base que llegó desordenado por haberse construido en etapas encaja mal con la idea de tests reproducibles. Se quiere cerrar esa deuda con JUnit 5 + Testcontainers (Postgres real en Docker, efímero), aprovechando para reaprender Docker.

## What Changes

- **Infra de test**: `spring-boot-starter-test` (JUnit 5 + Mockito + MockMvc) heredable por todos los módulos.
- **Testcontainers**: Postgres efímero por corrida para los tests de integración, con `@ServiceConnection` autoconfigurando el DataSource (no toca el Postgres de dev).
- **Tests unitarios** de services (Mockito, sin Spring) y **tests de slice** de controllers + seguridad (`@WebMvcTest`).
- **Tests de integración** que ejecutan los escenarios ya redactados en las specs existentes (`user-identity`, `user-directory`, `court-availability`, `reservation-booking`, `api-cors`).
- **Esquema de test** generado por Hibernate (`create-drop`) sobre el schema `platform`. El DDL actual se archiva como referencia (sin usarlo en el build) para una futura adopción de Flyway.
- **Documentación**: prerrequisitos de entorno (Docker Desktop + WSL2), comando de test y convención de tests en `AGENTS.md` y una entrada nueva en la bitácora.

No cambia el comportamiento del producto; solo se agrega verificación automatizada. Por eso este change **opta por no tocar specs** (`skip_specs: true`): no hay capacidad de negocio nueva ni modificada.

## Capabilities

### New Capabilities

<!-- Ninguna: es infraestructura de test, no introduce comportamiento de negocio. -->

### Modified Capabilities

<!-- Ninguna: los escenarios existentes se ejecutan, no cambian. El change marca skip_specs: true. -->

## Impact

- `pom.xml` raíz: dependencias de test en scope `test` (no afecta el artefacto de producción).
- `app`: `spring-boot-testcontainers`, `org.testcontainers:*`, tests de integración y `application-test.properties`.
- Módulos de dominio: tests unitarios junto a cada service.
- `AGENTS.md` y `bitacora-tecnica-sportbooking.md`: documentación del flujo de test.
- **No** modifica el runtime de la app, el Postgres de dev ni el esquema de producción.
- Prerrequisito nuevo en la máquina de desarrollo: Docker Desktop + WSL2.
