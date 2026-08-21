# Bitácora Técnica — Proyecto Sportbooking

> Registro de problemas encontrados, causas raíz y soluciones aplicadas durante el desarrollo del proyecto. Pensado como insumo para preparar entrevistas técnicas: cada entrada es un caso real que se puede explicar de punta a punta (síntoma → diagnóstico → causa → fix → concepto general detrás).

---

## Convenciones de este documento

- **Estado**: 🔴 Resuelto / 🟡 Pendiente / 🔵 Solo documentado (concepto, no bug)
- **Concepto para profundizar**: el tema general que vale la pena estudiar más allá del caso puntual, de cara a entrevista.

---

## 1. Arquitectura y build

*(Este punto ya lo tenés desarrollado en el Word — referencia rápida acá)*

- Proyecto Maven **multi-módulo**: `sportbooking` (padre, `packaging=pom`, aggregator) → `app`, `auth-module`, `user-module`.
- Renombrado de `platform` → `sportbooking` (groupId/artifactId/paquetes Java) a medida que se definió el dominio real del proyecto (gestión de reservas deportivas).
- **Concepto para profundizar**: diferencia entre módulo `pom` (aggregator) vs `jar` (deployable); `dependencyManagement` vs `dependencies` en proyectos multi-módulo; cómo el `@SpringBootApplication` hace component scan de todo lo que esté bajo su mismo paquete raíz, aunque esté repartido en módulos Maven distintos.

---

## 2. Bugs corregidos en la capa de autenticación

### 2.1 — 🔴 `UserService.registerUser()`: falla silenciosa al guardar
- **Síntoma**: el registro podía "funcionar" (200 OK) aunque el `save()` fallara internamente.
- **Causa raíz**: el `catch` de la excepción solo hacía `printStackTrace()` y el método igual devolvía el `User` como si se hubiera persistido.
- **Fix**: se dejó propagar la excepción; el controller ahora la captura y responde con el error real.
- **Concepto para profundizar**: manejo de excepciones — por qué "atrapar y loguear sin relanzar" es casi siempre peor que dejar propagar, y el patrón de traducir excepciones técnicas (`DataIntegrityViolationException`, etc.) a respuestas HTTP significativas.

### 2.2 — 🔴 `UserService`: no validaba username duplicado
- **Síntoma**: se validaba email duplicado pero no username, pese a existir `findByUsername()` en el repo.
- **Fix**: se agregó la validación simétrica de username antes de guardar.
- **Concepto para profundizar**: validación de negocio en la capa de servicio vs. constraints de integridad a nivel de base de datos (defensa en profundidad — ver punto 5).

### 2.3 — 🔴 `AuthController.isValidFields()`: riesgo de NullPointerException
- **Síntoma**: si el JSON de registro llegaba sin el campo `email` o `password`, explotaba con NPE (500) en vez de una respuesta de validación prolija.
- **Causa raíz**: se llamaba `.isEmpty()`/`.isBlank()` directo sobre un valor que podía ser `null`.
- **Fix**: chequeo explícito de `null` antes de evaluar el string.

### 2.4 — 🔴 `AuthController.register()`: código de estado HTTP incorrecto
- **Síntoma**: errores de validación de campos devolvían `401 UNAUTHORIZED`.
- **Fix**: se cambió a `400 BAD_REQUEST` (401 es para fallos de autenticación, no de validación de entrada).
- **Concepto para profundizar**: semántica correcta de códigos HTTP 4xx (400 vs 401 vs 403 — este último volvió a aparecer más adelante, ver 2.7).

### 2.5 — 🔴 `AuthController.login()`: solo capturaba `BadCredentialsException`
- **Síntoma**: cualquier otra subclase de `AuthenticationException` (cuenta deshabilitada, bloqueada, etc.) se colaba sin manejar → error 500 genérico en vez de un 401 prolijo.
- **Fix**: se generalizó el catch a `AuthenticationException`.

### 2.6 — 🔴 `JwtFilter`: import incorrecto de `IOException`
- **Síntoma**: el código importaba `io.jsonwebtoken.io.IOException` (una excepción de la librería JJWT, que en realidad extiende `RuntimeException`) en vez de `java.io.IOException`.
- **Por qué compilaba igual**: al ser una excepción *unchecked*, Java permite declararla en el `throws` sin violar las reglas de sobreescritura de métodos — pero semánticamente el import era completamente equivocado y confuso.
- **Fix**: corregido el import; se simplificó el manejo de excepciones que antes usaba `java.io.IOException` calificado a mano para esquivar el choque de nombres.
- **Concepto para profundizar**: reglas de *method overriding* en Java respecto a checked vs. unchecked exceptions en la cláusula `throws`.

### 2.7 — 🔴 `AuthRequest`: sin constructor vacío
- **Síntoma potencial**: riesgo de fallo al deserializar el body JSON de `/auth/login` con Jackson.
- **Fix**: se agregó constructor sin argumentos.

### 2.8 — 🔴 Constructor de `AuthController` con parámetro muerto
- **Síntoma**: recibía un `PasswordEncoder` que nunca se usaba ni se asignaba.
- **Fix**: eliminado el parámetro y su import.

---

## 3. Incidentes de runtime detectados en pruebas con Postman

### 3.1 — 🔴 `403 Forbidden` inesperado en `/auth/login`
- **Síntoma**: Postman devolvía 403 en vez del 401 esperado (o el 200 esperado).
- **Causa raíz**: la request tenía un **Bearer token viejo cargado en el header `Authorization`**, guardado de una prueba anterior. El `JwtFilter` se ejecuta sobre **todas** las requests (está registrado a nivel global de la cadena de filtros), no solo sobre las rutas protegidas — la regla `permitAll()` de `/auth/**` se evalúa recién en la etapa de *autorización*, posterior al filtro.
- **Fix**: quitar el header `Authorization` de las requests a `/auth/register` y `/auth/login` en Postman.
- **Concepto para profundizar**: diferencia entre **autenticación** (¿quién sos?, corre en el filtro) y **autorización** (¿podés acceder a esto?, corre después) en la cadena de Spring Security. Un endpoint "público" no significa que los filtros de auth se salteen — significa que, al llegar a la etapa de autorización, no van a exigir estar autenticado.

### 3.2 — 🔴 `LazyInitializationException` en `GET /auth/me`
- **Síntoma**: `/auth/login` funcionaba perfecto, pero `/auth/me` (endpoint protegido nuevo, creado para probar el flujo completo con el token) tiraba `LazyInitializationException: could not initialize proxy - no Session` al intentar leer `user.getGlobalRoles()`.
- **Causa raíz** (la más rica conceptualmente de todo el proyecto hasta ahora): *Open Session In View* (OSIV, activado por defecto en Spring Boot) está implementado como un **`HandlerInterceptor` de Spring MVC**, no como un `Filter` de Servlet. Esto significa que la sesión de Hibernate se abre recién cuando el `DispatcherServlet` invoca al controller — **después** de que corrieron todos los filtros de Spring Security (incluido `JwtFilter`).
  - En `/auth/login`, `getAuthorities()` se llamaba desde *dentro* del método del controller → la sesión OSIV ya estaba abierta → funcionaba.
  - En `/auth/me`, la validación del token y la carga de authorities ocurre en el `JwtFilter`/mecanismo de autorización → **antes** de que exista sesión → fallaba.
- **Fix**: se agregó `UserRepository.findByEmailWithRoles()` con `JOIN FETCH` explícito sobre `globalRoles` y `role`, para traer todo en una sola consulta y no depender de lazy-loading en ningún punto del flujo de autenticación.
- **Concepto para profundizar** ⭐ (muy bueno para entrevista): **Servlet Filters vs. Spring MVC Interceptors** — en qué momento del ciclo de vida de una request corre cada uno, y por qué mezclar JPA lazy-loading con lógica de autenticación custom es una fuente clásica de bugs difíciles de diagnosticar. También: `JOIN FETCH` como alternativa a cambiar el `fetch type` a `EAGER` (que resolvería esto pero degradaría performance en otros casos de uso donde no hacen falta los roles).

---

## 4. Endpoints agregados

- **`GET /auth/me`** (protegido, requiere `Authorization: Bearer <token>`): devuelve email y authorities del usuario autenticado. Se creó específicamente para validar el flujo de auth de punta a punta (login → token → request autenticada), no forma parte de un requerimiento de negocio todavía.

---

## 5. Pendientes identificados (no resueltos aún)

- 🟡 **CORS** configurado para `localhost:3000` (puerto de React) — mencionaste que el front sería Angular, que por defecto usa `4200`. Se va a romper silenciosamente en cuanto se conecte el front real.
- 🟡 **Credenciales hardcodeadas en `application.properties`** (usuario/password de Postgres, `jwt.secret`) en texto plano. Recomendación pendiente de aplicar: variables de entorno o `application-local.properties` fuera de Git, con un `.example` como referencia en el repo.
- 🟡 `spring.application.name=platform` — quedó desactualizado tras el rename a `sportbooking` (solo estético, aparece en los logs).
- 🟡 Falta constraint `UNIQUE` en `email`/`username` a nivel de base de datos — hoy la unicidad se valida solo en la capa de aplicación (`UserService`), lo cual no protege ante condiciones de carrera (dos requests simultáneos).
- 🟡 Nullability real (`NOT NULL`) no está declarada en varias columnas clave (`email`, `username`, `password_hash`).
- 🟡 No hay manejo global de errores (`@ControllerAdvice`/`@ExceptionHandler`) — cada controller maneja sus excepciones de forma ad hoc.
- 🟡 No hay tests (unitarios ni de integración) en ningún módulo.
- 🟡 Modelo de datos completo del dominio de reservas (`venue`, `court`, `time_block`, `court_schedule`, `reservation`, `user_venue_role`) ya existe en la base de datos, pero todavía no tiene entidades/repos/services/controllers en el código Java — próximo bloque grande de trabajo, organizado en módulos nuevos por dominio.

---

## 6. Próximos pasos acordados

1. Validar que los fixes de esta sesión compilen y funcionen en el entorno local (Eclipse + Postgres).
2. Crear módulos nuevos por dominio (`venue-module`, `booking-module`, etc.) para las tablas ya diseñadas en la base.
3. Retomar pendientes de la sección 5 cuando corresponda (seguridad de config, CORS, constraints de BBDD, testing).
