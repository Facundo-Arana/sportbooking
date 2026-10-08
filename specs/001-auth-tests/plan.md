# Implementation Plan: Pruebas automatizadas de login y registro de usuarios

**Branch**: `001-auth-tests` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-auth-tests/spec.md`

## Summary

Incorporar una suite de pruebas automatizadas para el registro (`POST /auth/register`) y
el login (`POST /auth/login`) sin tocar la lógica de producción ni el comportamiento de
otros módulos. La suite combina pruebas unitarias con dependencias simuladas (reglas de
`UserService` en `user-module`; mapeo de status del `AuthController` en `auth-module`) y
una prueba de integración HTTP en `app-module` que recorre el flujo completo (filtros de
Spring Security + `GlobalExceptionHandler` + persistencia) sobre una base H2 en memoria.
`mvn test` pasa a ser la puerta de calidad oficial; se actualizan la constitución
(principio VI) y `AGENTS.md`.

## Technical Context

**Language/Version**: Java 17 (fijado en el `pom.xml` raíz; el build real es 17).

**Primary Dependencies**: Spring Boot 3.4.7 (Web, Data JPA, Security), JJWT 0.11.5,
MapStruct 1.5.5.Final, Lombok 1.18.30. **Pruebas (nuevas)**: `spring-boot-starter-test`
(JUnit 5 / Jupiter, Mockito, AssertJ, Spring Test / MockMvc) y `com.h2database:h2`
(scope `test`, solo en `app`).

**Storage**: Producción = PostgreSQL local (schema `platform`, `ddl-auto=update`).
Pruebas = H2 en memoria en modo PostgreSQL (`ddl-auto=create-drop`), sin depender de la
instancia local (FR-015).

**Testing**: JUnit 5 + Mockito (unitarias, sin contexto Spring) y
`@SpringBootTest` + `@AutoConfigureMockMvc` (integración HTTP). Comando oficial:
`.\mvnw.cmd test` desde la raíz (FR-010).

**Target Platform**: JVM / entorno de desarrollo Windows (PowerShell), sin servicios
externos requeridos por la suite.

**Project Type**: Monolito modular Maven, `app` = único jar ejecutable. Pruebas ubicadas
en el `src/test` de cada módulo directamente afectado.

**Performance Goals**: La suite completa de login/registro en menos de 2 minutos en una
máquina de desarrollo estándar (SC-003). H2 en memoria la mantiene por debajo de ese
umbral.

**Constraints**: No alterar el comportamiento observable de módulos no relacionados
(FR-011); no cambiar firmas ni el contrato de los endpoints; Java 17; no introducir
módulos nuevos ni mover beans transversales; no añadir un `try/catch` en controllers.

**Scale/Scope**: 3 clases de prueba (~20 métodos), 1 archivo de propiedades de test,
2 `pom.xml` ajustados con dependencias de scope `test`, 3 documentos actualizados
(constitución, `AGENTS.md`, bitácora).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Evaluación | Evidencia / diseño |
|-----------|------------|--------------------|
| I. Modularidad por dominio | PASS | Las pruebas viven en el `src/test` de módulos existentes (`user`, `auth`, `app`). No se crean módulos ni se mueven beans. |
| II. DTOs en el borde | PASS | La integración verifica que las respuestas son DTO/Map (p. ej. registro devuelve `username`, login devuelve `token`) y que `password_hash` nunca viaja. No se exponen entities. |
| III. Errores en un único lugar | PASS | Los tests afirman 400 (registro inválido/duplicado) y 401 (credenciales) generados por `GlobalExceptionHandler`, sin añadir `try/catch` en controllers. |
| IV. Seguridad por recurso y por campo | PASS | La integración ejerce la cadena de filtros: `/auth/**` público y endpoint protegido (`/auth/me`) que exige token. No se debilita la config de seguridad. |
| V. Contrato de persistencia explícito | PASS | No se modifican entities ni el esquema de producción; en test se usa `create-drop` sobre H2. No se agrega DDL manual. |
| VI. Documentación viva y verificación empírica | PASS (con enmienda planificada) | La feature **enmienda** el principio VI: deja de afirmar "no hay tests" y establece `mvn test` como puerta de calidad. Requiere bump MINOR → `1.1.0` y actualizar `AGENTS.md` + bitácora (SC-007). Sin violaciones que justificar en Complexity Tracking. |

**Resultado del gate**: PASS. Única acción de gobierno: enmienda MINOR de la constitución
(principio VI) y sincronización de `AGENTS.md`/bitácora, ambas explícitamente dentro del
alcance de la spec.

## Project Structure

### Documentation (this feature)

```text
specs/001-auth-tests/
├── plan.md              # Este archivo
├── research.md          # Fase 0
├── data-model.md        # Fase 1
├── quickstart.md        # Fase 1
├── contracts/
│   └── auth-endpoints.md  # Contrato de /auth/register, /auth/login, /auth/me
└── checklists/
    └── requirements.md  # Ya existente
```

### Source Code (repository root)

```text
user-module/
└── src/test/java/com/example/sportbooking/user/service/
    └── UserServiceTest.java                 # Unitarias: alta, duplicados, hashing

auth-module/
└── src/test/java/com/example/sportbooking/auth/controller/
    └── AuthControllerTest.java              # Unitarias: 400/401/200 del controller

app/
├── src/test/java/com/example/sportbooking/auth/
│   └── AuthFlowIntegrationTest.java         # Integración HTTP con MockMvc + H2
└── src/test/resources/
    └── application-test.properties          # Datasource H2, dialecto, jwt.secret de test

pom.xml                                       # + spring-boot-starter-test (scope test)
app/pom.xml                                   # + h2 (scope test)

# Documentación de gobierno actualizada por la feature:
.specify/memory/constitution.md               # principio VI + versión 1.1.0
AGENTS.md                                     # deja de decir "no hay tests"
bitacora-tecnica-sportbooking.md              # nueva entrada numerada
```

**Structure Decision**: estrategia "test junto al código" (convención Maven estándar:
`src/test/java` por módulo). Las unitarias quedan en el módulo dueño de la lógica
(`user`, `auth`) porque no requieren contexto Spring y así prueban la regla aislada. La
integración queda en `app` porque es el único módulo que ve todos los `@RestController`,
el `GlobalExceptionHandler` y la `SecurityConfiguration`, y puede levantar el contexto
completo. No se usa el directorio `src/test` de la raíz (aggregator `pom` sin código).

## Complexity Tracking

> No hay violaciones de la constitución que justificar. La única acción de gobierno es
> una enmienda MINOR planificada del principio VI (documentación/verificación), dentro
> del alcance explícito de la spec (SC-007).

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |

## Re-evaluación post-diseño (Fase 1)

Tras generar `research.md`, `data-model.md`, `contracts/` y `quickstart.md`, el gate se
mantiene **PASS** sin cambios:

- El diseño de datos confirma que no se crean ni modifican entidades y que la suite solo
  observa el modelo existente (Principios II y V).
- Los contratos confirman que no se altera el contrato observable de ningún endpoint
  (FR-011) y que los errores siguen centralizados (Principio III).
- La prueba sobre `/auth/me` refuerza la cobertura de seguridad sin tocar la config
  (Principio IV).
- La única acción de gobierno sigue siendo la enmienda MINOR del Principio VI, ya
  contabilizada; no aparece ninguna violación nueva que requiera `Complexity Tracking`.
