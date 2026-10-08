# Quickstart — Validación de la suite de login y registro

**Feature**: `001-auth-tests` | **Fecha**: 2026-10-08

Guía para ejecutar y validar la suite de pruebas una vez implementada. No contiene código
de implementación; el detalle de datos y contrato está en
[data-model.md](./data-model.md) y [contracts/auth-endpoints.md](./contracts/auth-endpoints.md).

## Prerequisites

- JDK 17 y el wrapper de Maven incluido en el repo (`mvnw.cmd`).
- **No** se requiere PostgreSQL local, ni `application-local.properties`, ni usuarios/ADMIN
  creados a mano: la integración usa H2 en memoria (perfil `test`).
- Primera corrida con acceso a Internet para descargar `spring-boot-starter-test` y `h2`
  (luego quedan en el repositorio local de Maven).

## Comandos de validación

Ejecutar desde la raíz del repositorio:

```powershell
# Suite completa (todas las pruebas de todos los módulos) — puerta de calidad oficial
.\mvnw.cmd test

# Solo las unitarias de reglas de registro (módulo user)
.\mvnw.cmd -pl user-module test

# Solo las unitarias del controller de auth
.\mvnw.cmd -pl auth-module test

# Solo la integración HTTP (módulo app; levanta H2)
.\mvnw.cmd -pl app test
```

## Escenarios que la suite debe probar

Todos corresponden a `spec.md`; el contrato exacto de respuestas está en
[contracts/auth-endpoints.md](./contracts/auth-endpoints.md).

### Registro (User Story 1)

1. Alta con datos válidos → `200` con `username`; el usuario existe en la base.
2. Alta sin `email` / sin `password` / con valores en blanco → `400`.
3. Alta con email ya registrado → `400` ("Ya existe un usuario registrado con ese email").
4. Alta con username ya en uso → `400` ("Ese nombre de usuario ya está en uso").
5. `password_hash` persistido ≠ texto plano y `passwordEncoder.matches(...)` es `true`.
6. Ninguna respuesta de registro incluye `password`/`password_hash`.

### Login (User Story 2)

1. Login con credenciales válidas → `200` con `token` no vacío.
2. Login con contraseña incorrecta → `401` ("Credenciales inválidas").
3. Login con usuario inexistente → `401` ("Credenciales inválidas").
4. Ningún fallo de autenticación responde `5xx` (nunca error interno).
5. El `token` emitido habilita `GET /auth/me` (`200`); sin token, `/auth/me` → `401`.

## Resultado esperado

- `.\mvnw.cmd test` termina con `BUILD SUCCESS` y **0 fallos / 0 errores**.
- La suite completa corre en menos de 2 minutos (SC-003).
- Correrla dos veces seguidas da el mismo resultado, en cualquier orden de métodos
  (FR-012).
- Una regresión real (p. ej. aceptar un email duplicado o emitir token con contraseña
  incorrecta) debe hacer fallar al menos un test (SC-002).

## Cómo verificar que es la puerta de calidad

Para ejercitar el escenario SC-002 de forma controlada, romper temporalmente una regla en
el código de producción (por ejemplo, omitir el chequeo de email duplicado en
`UserService`), correr `.\mvnw.cmd test` y confirmar que la suite falla; luego revertir el
cambio. No se debe commitear el cambio de prueba.

## Troubleshooting

| Síntoma | Causa probable | Acción |
|---------|----------------|--------|
| Falla al construir el contexto de `app` por conexión a PostgreSQL | El perfil `test` no está activo o no se sobreescribió el datasource | Verificar `@ActiveProfiles("test")` y `application-test.properties` (research R5/R7). |
| `WeakKeyException` al generar el JWT | `jwt.secret` de test menor a 32 bytes | Usar el valor de test documentado en research R5. |
| `Table "platform.user" not found` en H2 | El schema `platform` no se creó | Confirmar el `INIT=CREATE SCHEMA IF NOT EXISTS platform` en la URL (research R5). |
| Un test de integración afecta a otro | Datos compartidos entre métodos | Usar datos con `UUID` y limpieza en `@BeforeEach` (research R6). |
