# Phase 0 — Research: Pruebas automatizadas de login y registro

**Feature**: `001-auth-tests` | **Fecha**: 2026-10-08

Este documento resuelve las decisiones técnicas necesarias para planificar la suite. No
quedan `NEEDS CLARIFICATION`: la spec fijó el "qué" (unitarias + integración con base
embebida, `mvn test` como puerta) y aquí se decide el "cómo".

---

## R1. Framework de pruebas

**Decision**: `spring-boot-starter-test` en scope `test`, que aporta JUnit 5 (Jupiter),
Mockito, AssertJ, Spring Test y MockMvc. `com.h2database:h2` en scope `test` para la
integración.

**Rationale**: es el starter oficial del stack (Spring Boot 3.4.7 ya es el parent) y trae
todo lo necesario sin fijar versiones a mano (las administra el parent). Evita agregar
frameworks fuera del ecosistema y mantiene el build reproducible.

**Alternatives considered**:
- JUnit + Mockito sueltos con versiones explícitas: más control, pero ruido y riesgo de
  incompatibilidad con el parent; rechazado por innecesario.
- TestNG / Spock: fuera del ecosistema Spring Boot estándar; rechazado.

## R2. Dónde se declara cada dependencia

**Decision**: `spring-boot-starter-test` se agrega en el `pom.xml` **raíz** con
`<scope>test</scope>` (todos los módulos lo heredan, como ya ocurre con web/JPA/security).
`h2` se agrega **solo en `app/pom.xml`** con `<scope>test</scope>`, porque solo `app`
levanta el contexto de integración.

**Rationale**: el proyecto ya concentra las dependencias en el raíz (los módulos declaran
solo dependencias entre módulos), así que respetar esa convención requiere una única
edición para el starter. H2 no lo necesitan `user`/`auth` para sus unitarias, por lo que
se acota al módulo que realmente lo usa (mínimo impacto, FR-011).

**Alternatives considered**:
- Declarar `starter-test` en cada uno de los tres módulos: repetitivo y rompe la
  convención de centralizar en el raíz.
- H2 en el raíz: lo expondría a módulos que no lo usan; se prefirió acotarlo.

## R3. Tipos de prueba y responsabilidad de cada una

**Decision**:
1. **Unitaria — `UserServiceTest` (`user-module`)**: Mockito puro, sin contexto Spring.
   `UserRepository` y `PasswordEncoder` simulados. Verifica reglas de negocio: alta válida,
   email duplicado e username duplicado lanzan `IllegalArgumentException`, y que la
   contraseña persistida es el resultado del encoder (no el texto plano).
2. **Unitaria — `AuthControllerTest` (`auth-module`)**: Mockito + `MockMvcBuilders
   .standaloneSetup`, sin seguridad ni contexto. Verifica mapeo de status del controller:
   400 (campos ausentes/blancos), 401 (fallo de autenticación), 200 + token (login OK).
3. **Integración — `AuthFlowIntegrationTest` (`app-module`)**: `@SpringBootTest` +
   `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`. Recorre HTTP real por la cadena de
   filtros de Spring Security, `GlobalExceptionHandler` y persistencia H2.

**Rationale**: cubre FR-014 (unitaria + integración) manteniendo las unitarias rápidas y
aisladas (una falla señala la regla exacta) y concentrando en una sola prueba de
integración la seguridad, el mapeo de errores y la persistencia, que solo tienen sentido
con el contexto completo.

**Alternatives considered**:
- Solo integración: más lenta y con diagnóstico menos preciso; no cumple "unitarias con
  dependencias simuladas".
- `@WebMvcTest` para el controller: carga seguridad/filtros y obliga a importar
  configuraciones; `standaloneSetup` aísla mejor la unidad bajo prueba. La integración ya
  cubre la seguridad con el contexto real.

## R4. Integración: MockMvc vs. servidor embebido aleatorio

**Decision**: `MockMvc` con `@SpringBootTest` + `@AutoConfigureMockMvc`.

**Rationale**: Spring Boot registra los `Filter` del contexto (incluida la cadena de
Spring Security y el `JwtFilter`) en el `MockMvc`, así que el request atraviesa filtros,
`DispatcherServlet`, controllers, `GlobalExceptionHandler` y JPA. Es determinista, rápido
(no abre puerto) y suficiente para FR-014. El endpoint protegido `/auth/me` permite además
comprobar de punta a punta que el token emitido es usable.

**Alternatives considered**:
- `webEnvironment=RANDOM_PORT` + `TestRestTemplate`: HTTP real sobre socket, pero más
  lento y sin ventaja material para estos escenarios; se deja como alternativa si en el
  futuro se quiere probar serialización de red real.

## R5. Base de datos de integración (H2 con schema `platform`)

**Decision**: H2 en memoria, en modo PostgreSQL, con `ddl-auto=create-drop` y creación del
schema `platform`. Propiedades clave en `application-test.properties`:

```properties
spring.datasource.url=jdbc:h2:mem:sportbooking;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS platform
spring.datasource.username=sa
spring.datasource.password=
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect
jwt.secret=test-secret-jwt-hs256-000000000000000000000000
```

**Rationale**: las entities declaran `@Table(schema="platform")`; H2 no crea schemas de
forma implícita confiable, así que se crea con `INIT` al abrir la conexión. El modo
PostgreSQL reduce diferencias de dialecto. `create-drop` garantiza estado limpio por
corrida y no toca la base de producción. El `jwt.secret` de test debe tener ≥ 32 bytes
(HS256 con `Keys.hmacShaKeyFor`) o `JwtUtil` falla; se usa un valor ficticio (no es un
secreto real).

**Alternatives considered**:
- `hibernate.hbm2ddl.create_namespaces=true`: válido, pero `INIT` es explícito y no
  depende del auto-configurador; preferido por claridad.
- Testcontainers/PostgreSQL: descartado explícitamente por FR-015 (sin PostgreSQL local).

## R6. Aislamiento y determinismo (FR-012)

**Decision**: cada test usa datos únicos (email/username con `UUID`) y la integración
limpia el estado en `@BeforeEach` (`userRepository.deleteAll()`). Las unitarias no
comparten estado (mocks por test).

**Rationale**: evita dependencia de orden entre pruebas y de datos previos. No se depende
de rollback transaccional de MockMvc para no acoplarse a detalles del thread/transacción.

**Alternatives considered**:
- `@Transactional` con rollback por test: funciona, pero puede enmascarar problemas de
  flush/constraint; se prefirió limpieza explícita para que la prueba refleje el
  comportamiento real.

## R7. Evitar el `application-local.properties` durante los tests

**Decision**: `application-test.properties` sobreescribe explícitamente datasource,
dialecto y `jwt.secret`. El perfil `test` (mayor precedencia que la config importada por
`spring.config.import`) gana aunque exista un `application-local.properties` en la máquina.

**Rationale**: la config versionada importa un archivo local opcional; sin overrides, un
desarrollador con PostgreSQL/secretos locales podría hacer que la suite dependa de ellos.
Forzando los valores en el perfil de test, la suite es autocontenida en cualquier máquina
(FR-010, FR-015, SC-004).

**Alternatives considered**:
- Desactivar el import en test (`spring.config.import=`): frágil por el orden de
  resolución; se prefirió sobreescribir valores concretos.

## R8. Actualización de gobierno (SC-007)

**Decision**: enmendar el principio VI de la constitución (dejar de afirmar "no hay
tests"; establecer `mvn test` como puerta), subir la versión a `1.1.0` (MINOR) y
actualizar `AGENTS.md` y la bitácora con una entrada numerada.

**Rationale**: la constitución exige que cualquier cambio documental se explicite, y la
spec puso esta actualización dentro del alcance. Es una expansión material de la guía de
verificación → MINOR según la política de versionado vigente.

**Alternatives considered**:
- Dejar la constitución intacta: crearía una contradicción directa con el nuevo estado
  (documentación que dice que `mvn test` no verifica nada mientras la suite existe).

---

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|--------|------------|
| H2 difiere de PostgreSQL y oculta un error real | Uso de `MODE=PostgreSQL`; el alcance es login/registro, cuyos tipos son portables. |
| El `jwt.secret` de test resulta corto y `JwtUtil` falla | Valor de test ≥ 32 bytes documentado en R5. |
| Import accidental de `application-local.properties` | Overrides explícitos del perfil `test` (R7). |
| `@AutoConfigureMockMvc` no aplicara la seguridad | Spring Boot registra `Filter` del contexto; se valida con el caso `/auth/me` sin/con token. |
| Pruebas acopladas al orden | Datos únicos + limpieza en `@BeforeEach` (R6). |
| Añadir dependencias altera módulos no relacionados | Scope `test` (no afecta runtime) y H2 acotado a `app` (R2, FR-011). |
