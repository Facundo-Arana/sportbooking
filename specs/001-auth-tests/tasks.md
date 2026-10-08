---

description: "Task list for feature: Pruebas automatizadas de login y registro de usuarios"
---

# Tasks: Pruebas automatizadas de login y registro de usuarios

**Input**: Design documents from `/specs/001-auth-tests/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Esta feature **ES** una suite de pruebas: cada tarea de implementación produce código de test. No existe capa de producción nueva; solo se agregan dependencias de scope `test`, configuración de test y los documentos de gobierno.

**Organization**: Las tareas se agrupan por historia de usuario para que cada una sea implementable y verificable de forma independiente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede correr en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia a la que pertenece (US1, US2, US3)
- Todas las rutas son relativas a la raíz del repositorio.

## Path Conventions

Monolito modular Maven: cada módulo tiene su `src/main` y su `src/test`. Las unitarias viven en el módulo dueño de la lógica (`user-module`, `auth-module`); la integración vive en `app` (único módulo que ve todos los controllers, `GlobalExceptionHandler` y `SecurityConfiguration`).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Dependencias y configuración mínima de test, sin tocar código de producción.

- [ ] T001 [P] Agregar `org.springframework.boot:spring-boot-starter-test` con `<scope>test</scope>` en `pom.xml` (raíz), para que `user-module`, `auth-module` y `app` hereden JUnit 5 (Jupiter), Mockito, AssertJ, Spring Test y MockMvc.
- [ ] T002 [P] Agregar `com.h2database:h2` con `<scope>test</scope>` en `app/pom.xml` (solo el módulo que levanta el contexto de integración).
- [ ] T003 [P] Crear `app/src/test/resources/application-test.properties` con: `spring.datasource.url=jdbc:h2:mem:sportbooking;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS platform`, `spring.datasource.username=sa`, `spring.datasource.password=`, `spring.datasource.driver-class-name=org.h2.Driver`, `spring.jpa.hibernate.ddl-auto=create-drop`, `spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect` y `jwt.secret` ficticio de test ≥32 bytes (ej. `test-secret-jwt-hs256-000000000000000000000000`).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Confirmar que el build resuelve las dependencias nuevas antes de escribir pruebas.

**⚠️ CRITICAL**: Ninguna tarea de historia puede darse por buena sin esta verificación previa.

- [ ] T004 Ejecutar `.\mvnw.cmd -q test` desde la raíz y confirmar que descarga las dependencias nuevas y compila los 9 módulos sin errores (aunque todavía no existan clases de test), dejando el build reproducible.

**Checkpoint**: Dependencias y perfil de test listos; comienza la implementación de las historias.

---

## Phase 3: User Story 1 - Registrar usuario de forma segura y verificable (Priority: P1) 🎯 MVP

**Goal**: Fijar con pruebas las reglas de `POST /auth/register` (alta válida, rechazos por datos inválidos/duplicados y contraseña nunca en texto plano).

**Independent Test**: Correr solo las pruebas de registro y ver: un alta válida persiste el usuario; falta de `email`/`password` (o en blanco), email duplicado y username duplicado se rechazan con `400`; el `password_hash` persistido no coincide con el texto plano.

### Implementation for User Story 1

- [ ] T005 [P] [US1] Crear `user-module/src/test/java/com/example/sportbooking/user/service/UserServiceTest.java` (JUnit 5 + Mockito puro, sin contexto Spring: `@Mock UserRepository`, `@Mock PasswordEncoder`, `@InjectMocks UserService`). Casos: (a) alta válida -> `save` invocado y entidad devuelta con `created_at` no nulo; (b) `findByEmail` presente -> `IllegalArgumentException` con mensaje "Ya existe un usuario registrado con ese email" y `save` nunca invocado; (c) `findByUsername` presente -> `IllegalArgumentException` con mensaje "Ese nombre de usuario ya está en uso"; (d) el `password_hash` construido es el resultado de `passwordEncoder.encode(textoPlano)`, distinto del texto plano enviado (FR-001, FR-003, FR-004, FR-005).
- [ ] T006 [P] [US1] Crear `auth-module/src/test/java/com/example/sportbooking/auth/controller/AuthControllerTest.java` (Mockito + `MockMvcBuilders.standaloneSetup`, sin seguridad ni contexto; mocks de `AuthenticationManager`, `JwtUtil`, `CustomUserDetailsService`, `UserService`). Casos de registro: `POST /auth/register` sin `email`, sin `password` y con ambos en blanco -> `400` con campo `error`; alta válida (mock `UserService.registerUser` devuelve un `User`) -> `200` con `message` y `username`; el body de respuesta nunca contiene `password` ni `password_hash` (FR-002, FR-011, invariante de no filtración).
- [ ] T007 [P] [US1] Crear `app/src/test/java/com/example/sportbooking/auth/AuthFlowIntegrationTest.java` (`@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`; `userRepository.deleteAll()` en `@BeforeEach`; datos únicos con `UUID`). Casos de registro por HTTP real (cadena de filtros + `GlobalExceptionHandler` + persistencia H2): alta válida -> `200` y usuario presente en el repositorio consultado con `UserRepository.findByEmail`; email duplicado -> `400`; username duplicado -> `400`; la respuesta de registro no incluye `password_hash` (FR-001, FR-003, FR-004, FR-014, FR-015).

**Checkpoint**: User Story 1 funcional y testeable de forma aislada (MVP).

---

## Phase 4: User Story 2 - Iniciar sesión con credenciales válidas e inválidas (Priority: P2)

**Goal**: Fijar con pruebas el comportamiento de `POST /auth/login` (token con credenciales válidas, `401` con cualquier credencial inválida, nunca `500`) y la usabilidad del token en el endpoint protegido.

**Independent Test**: Correr solo las pruebas de login y ver: credenciales correctas devuelven un token; contraseña incorrecta o usuario inexistente se rechazan con `401`; ningún fallo produce `5xx`; el token habilita `GET /auth/me` y sin token este responde `401`.

### Implementation for User Story 2

- [ ] T008 [P] [US2] Ampliar `auth-module/src/test/java/com/example/sportbooking/auth/controller/AuthControllerTest.java` con los casos de login (misma clase que T006). Con `authenticationManager.authenticate` que no lanza y `jwtUtil.generateToken` devolviendo un string -> `200` con `token` no vacío; con `AuthenticationException` (BadCredentials/Disabled/Locked) -> `401` con "Credenciales inválidas"; verificar que el status nunca es `5xx` (FR-006, FR-007, FR-008, FR-009).
- [ ] T009 [P] [US2] Ampliar `app/src/test/java/com/example/sportbooking/auth/AuthFlowIntegrationTest.java` (misma clase que T007) con los casos de login de punta a punta: registrar usuario y login correcto -> `200` + `token` no vacío; contraseña incorrecta -> `401`; email inexistente -> `401`; afirmar que el status no es `5xx` en los fallos; `GET /auth/me` con `Authorization: Bearer <token>` -> `200` y con email en el body, y sin token -> `401` (FR-006, FR-007, FR-008, FR-009; Principio IV).

**Checkpoint**: User Stories 1 y 2 funcionan y se verifican de forma independiente.

---

## Phase 5: User Story 3 - Suite reproducible como puerta de calidad (Priority: P3)

**Goal**: Convertir `.\mvnw.cmd test` en la puerta de calidad oficial y actualizar la documentación de gobierno que afirmaba que no había tests.

**Independent Test**: En un checkout limpio, `.\mvnw.cmd test` ejecuta login y registro y reporta resultado sin pasos manuales; una regresión en login/registro hace fallar al menos una prueba.

### Implementation for User Story 3

- [ ] T010 [US3] Actualizar `.specify/memory/constitution.md`: enmendar el principio VI (quitar la afirmación de que el proyecto no tiene tests y que ningún documento puede afirmar que `mvn test` verifica algo; establecer `.\mvnw.cmd test` como puerta de calidad obligatoria), subir `Version` a `1.1.0` (bump MINOR), actualizar `Last Amended` y el bloque `Sync Impact Report` (SC-007).
- [ ] T011 [US3] Actualizar `AGENTS.md`: reemplazar `**No hay tests**... La verificación real es compilar + Postman` por la descripción de la suite y `.\mvnw.cmd test` como verificación oficial, y ajustar la sección "Flujo de trabajo" en consecuencia (SC-007).
- [ ] T012 [US3] Actualizar `bitacora-tecnica-sportbooking.md`: corregir "Estado actual" (quitar "Sin tests automatizados") y el pendiente 1 de "Pendientes / deuda técnica", y agregar el punto `27` con la secuencia síntoma → causa raíz → fix (suite unitaria + integración con H2) → archivos modificados (ruta completa) → concepto para profundizar (pirámide de tests, aislamiento con datos únicos, base embebida) (SC-007, Principio VI).
- [ ] T013 [US3] Ejecutar `.\mvnw.cmd test` completo desde la raíz y confirmar `BUILD SUCCESS` con 0 fallos / 0 errores y duración < 2 minutos (SC-001, SC-003); correrlo dos veces seguidas y confirmar el mismo resultado (FR-012).
- [ ] T014 [US3] Validar detección de regresión (SC-002, sección "Cómo verificar que es la puerta de calidad" de quickstart): romper temporalmente el chequeo de email duplicado en `user-module/src/main/java/com/example/sportbooking/user/service/UserService.java`, correr `.\mvnw.cmd test`, confirmar que al menos una prueba falla, revertir el cambio y confirmar verde. No commitear el cambio temporal.

**Checkpoint**: Las tres historias están cubiertas y `mvn test` es la puerta oficial documentada.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validaciones transversales y de no-regresión de otros módulos.

- [ ] T015 [P] Ejecutar los comandos focalizados del quickstart y confirmar que cada módulo pasa por separado: `.\mvnw.cmd -pl user-module test`, `.\mvnw.cmd -pl auth-module test`, `.\mvnw.cmd -pl app test`.
- [ ] T016 [P] Verificar con `git status` / `git diff` que solo se tocaron `pom.xml`, `app/pom.xml`, archivos bajo `src/test`, `application-test.properties` y los 3 documentos de gobierno; confirmar que ningún módulo no relacionado vio alterado su comportamiento observable (FR-011, SC-006).
- [ ] T017 [P] Confirmar que `application-test.properties` no contiene secretos reales (el `jwt.secret` es ficticio) y que `application-local.properties` sigue fuera de Git (`.gitignore`).
- [ ] T018 Recorrer el checklist de "Resultado esperado" de `specs/001-auth-tests/quickstart.md` y dejar constancia de la validación final (0 fallos, determinismo en dos corridas, < 2 minutos).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias; puede empezar ya.
- **Foundational (Phase 2)**: depende de Setup; **BLOQUEA** todas las historias.
- **User Stories (Phase 3+)**: dependen de Foundational. Las historias pueden ir en paralelo (si hay capacidad) o en prioridad P1 → P2 → P3.
- **Polish (Phase 6)**: depende de todas las historias deseadas.

### User Story Dependencies

- **US1 (P1)**: arranca tras Foundational; sin dependencias de otras historias.
- **US2 (P2)**: arranca tras Foundational; reutiliza las clases creadas en US1 (T008 depende de T006; T009 depende de T007), por eso en la práctica se implementa después de US1.
- **US3 (P3)**: arranca tras Foundational en lo documental, pero T013/T014 dependen de que US1 y US2 estén completas.

### Within Each User Story

- Los archivos de test de reglas (`UserServiceTest`) antes que los de integración.
- La integración depende de que exista `application-test.properties` (T003).
- Registrar la clase base (T007) antes de ampliarla (T009); crear `AuthControllerTest` (T006) antes de ampliarlo (T008).

### Parallel Opportunities

- Setup: T001, T002 y T003 son archivos distintos → en paralelo.
- US1: T005, T006 y T007 son archivos distintos → en paralelo.
- US2: T008 y T009 son archivos distintos (y sus dependencias T006/T007 ya cerraron) → en paralelo.
- Polish: T015, T016 y T017 → en paralelo.

---

## Parallel Example: User Story 1

```text
# Lanzar en paralelo las tres clases de prueba de registro:
Task: "Crear UserServiceTest.java (unitarias de registro) en user-module/.../UserServiceTest.java"
Task: "Crear AuthControllerTest.java (400 de registro) en auth-module/.../AuthControllerTest.java"
Task: "Crear AuthFlowIntegrationTest.java (registro HTTP con H2) en app/.../AuthFlowIntegrationTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Phase 1 (Setup) y Phase 2 (Foundational).
2. Completar Phase 3 (US1): registro.
3. **STOP and VALIDATE**: `.\mvnw.cmd -pl user-module test`, `-pl auth-module test` y `-pl app test` en verde para los casos de registro.
4. Demo lista (MVP).

### Incremental Delivery

1. Setup + Foundational → build con dependencias de test.
2. US1 → registro cubierto → validar (MVP).
3. US2 → login + `/auth/me` cubiertos → validar.
4. US3 → `mvn test` documentado como puerta oficial.
5. Polish → verificaciones transversales y de no-regresión.

---

## Notes

- [P] = archivos distintos, sin dependencias pendientes.
- [US1]/[US2]/[US3] mapean la tarea a la historia de la spec para trazabilidad.
- Esta feature no modifica código de producción: solo dependencias `test`, configuración de test, clases de test y los 3 documentos de gobierno.
- Verificar que las pruebas fallan antes de considerarlas válidas (para romper/auth el ciclo en T014).
