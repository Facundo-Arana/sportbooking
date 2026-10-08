# Contrato HTTP: Autenticación (`/auth`)

**Feature**: `001-auth-tests` | **Fecha**: 2026-10-08

Estos son los contratos **existentes** que la suite de pruebas fija como referencia. La
feature no cambia firmas, bodies ni códigos: solo los verifica. Fuente:
`auth-module/src/main/java/com/example/sportbooking/auth/controller/AuthController.java`.

Base URL local: `http://localhost:8081`.

---

## POST /auth/register

Público. Registra un usuario con rol global vacío.

### Request

`Content-Type: application/json`

```json
{
  "username": "juan",
  "email": "juan@example.com",
  "password": "S3cret!",
  "name": "Juan"
}
```

Campos: `username`, `email`, `password` (obligatorios), `name` (opcional).

### Responses

| Status | Condición | Body |
|--------|-----------|------|
| `200 OK` | Alta válida y sin colisión | `{ "message": "Usuario registrado con éxito", "username": "juan" }` |
| `400 Bad Request` | `email` y/o `password` nulos o en blanco | `{ "error": "email y password son obligatorios" }` |
| `400 Bad Request` | `email` ya registrado | `{ "error": "Ya existe un usuario registrado con ese email" }` |
| `400 Bad Request` | `username` ya en uso | `{ "error": "Ese nombre de usuario ya está en uso" }` |
| `409 Conflict` | Violación de constraint UNIQUE en carrera (fallback) | `{ "error": "El dato ya existe o viola una restricción de la base" }` |

**Invariante**: la respuesta nunca contiene `password` ni `password_hash`.

### Escenarios cubiertos por la suite

- Alta válida → `200` y `username` en el body; usuario presente en el repositorio.
- Falta `email`, falta `password`, o vienen en blanco → `400`.
- Email repetido → `400`.
- Username repetido → `400`.
- Contraseña persistida ≠ texto plano y `passwordEncoder.matches` es verdadero.

---

## POST /auth/login

Público. Autentica por **email** + contraseña y emite un JWT.

### Request

`Content-Type: application/json`

```json
{
  "email": "juan@example.com",
  "password": "S3cret!"
}
```

### Responses

| Status | Condición | Body |
|--------|-----------|------|
| `200 OK` | Credenciales válidas | `{ "token": "<jwt>" }` |
| `401 Unauthorized` | Contraseña incorrecta | `{ "error": "Credenciales inválidas" }` |
| `401 Unauthorized` | Usuario inexistente | `{ "error": "Credenciales inválidas" }` |
| `401 Unauthorized` | Usuario deshabilitado/bloqueado u otro `AuthenticationException` | `{ "error": "Credenciales inválidas" }` |

**Invariante**: ningún fallo de autenticación produce `500` (FR-009). La distinción
usuario-inexistente vs. contraseña-incorrecta no se revela (mismo mensaje).

### Escenarios cubiertos por la suite

- Login válido → `200` y `token` no vacío.
- Contraseña incorrecta → `401`.
- Usuario inexistente → `401`.
- Se verifica que el status nunca es `5xx` en los fallos.

---

## GET /auth/me

Protegido. Requiere `Authorization: Bearer <token>` válido. Devuelve la identidad cargada
por el `JwtFilter` en el `SecurityContext`.

### Responses

| Status | Condición | Body |
|--------|-----------|------|
| `200 OK` | Token válido | `{ "email": "<email>", "authorities": [ ... ] }` |
| `401 Unauthorized` | Sin header / token inválido o mal formado | (respuesta 401 de Spring Security) |

### Uso en la suite

Se usa para probar de punta a punta que el token emitido por `/auth/login` es utilizable a
través de la cadena de filtros de seguridad. Cubre el principio IV (seguridad stateless
con `/auth/**` público y el resto autenticado).

---

## Formato de error

Todos los errores de negocio se serializan como `{ "error": "<mensaje>" }` y los traduce
`GlobalExceptionHandler` (`app-module`), nunca un controller con `try/catch`. La suite
verifica el mapeo canónico: `IllegalArgumentException` → 400,
`AuthenticationException` → 401, `DataIntegrityViolationException` → 409.
