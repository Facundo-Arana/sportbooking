# Phase 1 — Data Model: Pruebas de login y registro

**Feature**: `001-auth-tests` | **Fecha**: 2026-10-08

Esta feature **no crea ni modifica entidades**. Documenta el modelo ya existente que la
suite observa, las invariantes que las pruebas deben verificar y cómo se materializan en
la base de integración (H2).

---

## Entidad: Usuario (`User`)

Fuente: `user-module/src/main/java/com/example/sportbooking/user/entity/User.java`.
Tabla `platform.user`.

| Campo Java | Columna | Tipo | Regla relevante para los tests |
|------------|---------|------|--------------------------------|
| `id` | `id` | `Long` (IDENTITY) | Generado; identifica al usuario persistido. |
| `username` | `username` | `String` | `unique = true`, `nullable = false`. Unicidad verificada por la suite. |
| `email` | `email` | `String` | `unique = true`, `nullable = false`. Identificador de acceso (coincidencia exacta, sensible a mayúsculas). |
| `password_hash` | `password_hash` | `String` | `nullable = false`, `@JsonIgnore`. **Nunca** debe ser texto plano ni viajar en respuestas. |
| `name` | `name` | `String` | Opcional; no interviene en login/registro. |
| `created_at` | `created_at` | `Timestamp` | Se setea en el constructor de alta (no default de columna). |
| `updated_at` | `updated_at` | `Timestamp` | No usado por el flujo de auth. |
| `globalRoles` | — | `Set<UserGlobalRole>` | Relación `@OneToMany`; el registro no asigna roles globales (assumption de la spec). |

**Constructor de alta**: `User(username, email, password_hash, name)` — recibe el hash ya
calculado y setea `created_at` a `now()`. El registro debe pasar por `PasswordEncoder`
(`BCryptPasswordEncoder`) antes de construir el `User`.

### Invariantes verificadas por la suite

1. **Alta válida**: con `username`, `email`, `password` no vacíos y sin colisión, el
   usuario queda persistido y `created_at` no es nulo.
2. **Email único**: un segundo alta con el mismo `email` se rechaza con
   `IllegalArgumentException` → HTTP 400.
3. **Username único**: un segundo alta con el mismo `username` se rechaza con
   `IllegalArgumentException` → HTTP 400.
4. **Hash de contraseña**: `password_hash` almacenado ≠ contraseña en texto plano, y
   coincide con lo que devuelve `passwordEncoder.matches(textoPlano, hash)`.
5. **No filtración**: ninguna respuesta (registro, login, endpoint protegido) incluye
   `password_hash`.

---

## Objeto de transferencia: `UserDTO` (entrada de registro)

Fuente: `user-module/.../dto/UserDTO.java`. Campos de entrada relevantes: `username`,
`email`, `password`, `name`.

- `email` y `password` son obligatorios y no pueden ser nulos ni blancos (validado en
  `AuthController.isValidFields` antes de llamar al service).
- `username` duplicado / ausente se evalúa en `UserService` contra el estado persistido.
- La comparación de email es **exacta** (sin normalizar mayúsculas), tal como funciona
  hoy; la suite lo verifica como comportamiento esperado.

---

## Objeto de transferencia: `AuthRequest` / `AuthResponse` (login)

Fuentes: `auth-module/.../dto/AuthRequest.java`, `.../dto/AuthResponse.java`.

- `AuthRequest`: `email` + `password`.
- `AuthResponse`: `token` (String). Es el único dato que devuelve un login exitoso.

---

## Credencial de acceso y token (concepto, no entidad)

- **Credencial**: par (`email`, `password`). El estado válido/no válido lo resuelve
  `AuthenticationManager` en `AuthController.login`.
- **Token**: JWT firmado con `jwt.secret`. El `subject` es el email
  (`CustomUserDetails.getUsername()` devuelve el email). Se considera válido si
  `JwtUtil.isTokenValid` no lanza y no está expirado.
- La suite comprueba que un token emitido habilita un endpoint protegido
  (`GET /auth/me`), no solo que el string exista.

---

## Estado de la base en pruebas

- **Motor**: H2 en memoria, `MODE=PostgreSQL`, schema `platform` creado por `INIT`.
- **DDL**: `spring.jpa.hibernate.ddl-auto=create-drop` → las tablas se crean al levantar
  el contexto y se descartan al cerrarlo.
- **Aislamiento**: `userRepository.deleteAll()` en `@BeforeEach` + datos con `UUID` por
  método, para que cada test sea independiente del orden (FR-012).
- **Sin seed**: no se requieren usuarios ni `ADMIN` creados a mano (SC-004); los datos se
  crean dentro de cada test.

---

## Trazabilidad requisito → verificación

| Requisito | Tipo de prueba |
|-----------|----------------|
| FR-001 alta válida | Integración + unitaria `UserServiceTest` |
| FR-002 email/password ausentes o blancos | Integración + unitaria `AuthControllerTest` (400) |
| FR-003 email duplicado | Integración + unitaria `UserServiceTest` (400) |
| FR-004 username duplicado | Integración + unitaria `UserServiceTest` (400) |
| FR-005 contraseña no en texto plano | unitaria `UserServiceTest` + integración (consulta al repositorio) |
| FR-006 login exitoso con token | Integración + unitaria `AuthControllerTest` |
| FR-007 contraseña incorrecta | Integración + unitaria `AuthControllerTest` (401) |
| FR-008 usuario inexistente | Integración + unitaria `AuthControllerTest` (401) |
| FR-009 nunca 500 en fallo de auth | Integración (afirma 401, no 500) |
| FR-012 determinismo/orden | Diseño de datos (UUID + limpieza) |
| FR-015 base embebida | Configuración H2 del perfil `test` |
