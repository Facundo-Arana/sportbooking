# Bitácora Técnica — Proyecto Sportbooking

> Registro de problemas encontrados, causas raíz y soluciones aplicadas durante el desarrollo del proyecto. Pensado como insumo para preparar entrevistas técnicas: cada entrada es un caso real que se puede explicar de punta a punta (síntoma → diagnóstico → causa → fix → concepto general detrás).

---

## Convenciones de este documento

- **Estado**: 🔴 Resuelto / 🟡 Pendiente / 🔵 Solo documentado (concepto, no bug)
- **Concepto para profundizar**: el tema general que vale la pena estudiar más allá del caso puntual, de cara a entrevista.

---

## 1. Arquitectura y build

*(Este punto ya lo tenés desarrollado en el Word — referencia rápida acá)*

- Proyecto Maven **multi-módulo**: `sportbooking` (padre, `packaging=pom`, aggregator) → `app`, `auth-module`, `user-module`, `venue-module`.
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
- **Concepto para profundizar**: validación de negocio en la capa de servicio vs. constraints de integridad a nivel de base de datos (defensa en profundidad — ver punto 7).

### 2.3 — 🔴 `AuthController.isValidFields()`: riesgo de NullPointerException
- **Síntoma**: si el JSON de registro llegaba sin el campo `email` o `password`, explotaba con NPE (500) en vez de una respuesta de validación prolija.
- **Causa raíz**: se llamaba `.isEmpty()`/`.isBlank()` directo sobre un valor que podía ser `null`.
- **Fix**: chequeo explícito de `null` antes de evaluar el string.

### 2.4 — 🔴 `AuthController.register()`: código de estado HTTP incorrecto
- **Síntoma**: errores de validación de campos devolvían `401 UNAUTHORIZED`.
- **Fix**: se cambió a `400 BAD_REQUEST` (401 es para fallos de autenticación, no de validación de entrada).
- **Concepto para profundizar**: semántica correcta de códigos HTTP 4xx (400 vs 401 vs 403 — este último volvió a aparecer más adelante, ver 3.1).

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
- **`POST /venues`**, **`GET /venues`**, **`GET /venues/{id}`**, **`PUT /venues/{id}`**, **`DELETE /venues/{id}`** — CRUD básico de sedes (ver punto 5). Todos protegidos por la regla global `.anyRequest().authenticated()`, sin necesidad de agregar seguridad específica.

---

## 5. Módulo `venue-module` — CRUD de sedes

- **Objetivo**: primer módulo de dominio nuevo (fuera de auth/user), CRUD completo sobre la tabla `platform.venue` que ya existía en la base pero sin entidades Java.
- **Capas**: mismo patrón que `user-module` — `entity` → `repository` → `service` → `controller`, package `com.example.sportbooking.venue`.
- **Decisión: `VenueNotFoundException` propia en vez de `RuntimeException` genérica.** `UserService` usa `RuntimeException` a secas para los casos de duplicado, lo cual funciona pero no permite que el controller distinga "dato inválido" de "recurso inexistente" sin parsear el mensaje. Para `venue` se separó en dos tipos: `IllegalArgumentException` (entrada inválida → 400) y `VenueNotFoundException`, una `RuntimeException` custom (recurso no existe → 404). Es un paso intermedio hacia el `@ControllerAdvice` centralizado que sigue pendiente (ver punto 7).
- **Decisión: `created_at` seteado a mano en el constructor de alta.** La columna tiene `DEFAULT CURRENT_TIMESTAMP` en Postgres, pero si el campo del entity queda en `null`, Hibernate manda un `INSERT` con `created_at = NULL` explícito y pisa el default de la base. Mismo criterio que ya se usaba en `User`.
- **Seguridad heredada sin configuración extra**: como `SecurityConfiguration` ya tiene `.anyRequest().authenticated()` como regla por defecto, los endpoints `/venues/**` quedaron protegidos automáticamente — no hubo que tocar el filtro ni agregar reglas nuevas. Confirmado con Postman: sin token da 401/403, con token válido responde normal.
- **Testeado end-to-end**: alta, listado, detalle, edición parcial (solo pisa los campos no-nulos del DTO) y baja, más los casos de error (`name` vacío → 400, id inexistente → 404, sin token → 401/403). Todos los casos dieron el resultado esperado.
- **Concepto para profundizar**: excepciones de negocio custom (`extends RuntimeException`) como forma de comunicar semántica entre capas sin acoplar el service a `HttpStatus` — el service no sabe nada de HTTP, solo el controller traduce la excepción a un código de respuesta.

---

## 6. Incidente de tooling: Eclipse generaba una clase `main` en un módulo librería

- **Estado**: 🔴 Resuelto (no es un bug de código, sino de cómo se creó el proyecto en el IDE — se documenta porque consumió tiempo de diagnóstico y tiene una causa conceptual clara).
- **Síntoma**: al crear `venue-module` (un módulo que solo debe aportar clases al classpath de `app`, sin arrancar nada por sí mismo), el Project Explorer lo mostraba decorado como `[boot] [sportbooking main]`, a diferencia de `user-module` y `auth-module` que solo mostraban `[boot]`.
- **Causa raíz**: el wizard usado para crear el módulo no fue el genérico **Maven Module**, sino uno de los wizards de **Spring Starter Project**, que por diseño siempre genera una clase anotada `@SpringBootApplication` con `main()` — pensada para módulos ejecutables, no para módulos librería dentro de un multi-módulo. Un segundo intento, usando ya el wizard correcto, igual arrastró el problema por no haber tildado **"Create a simple project (skip archetype selection)"**: sin esa casilla, Maven aplica el archetype `maven-archetype-quickstart` por defecto, que genera su propia clase `App.java` con `main()`.
- **Por qué persistía incluso después de borrar la clase**: el **Boot Dashboard** de Spring Tools mantiene su propio estado en el workspace (no en la carpeta del proyecto), asociando proyectos a "boot apps" ejecutables. Borrar el `.java` no alcanza si el proyecto quedó registrado ahí — hace falta sacarlo explícitamente de esa vista, o directamente borrar el proyecto completo (contenido en disco incluido) y recrearlo.
- **Fix definitivo**: borrar `venue-module` (tildando "Delete project contents on disk"), limpiar el caché local de Maven (`~/.m2/repository/.../venue-module`) y los *launch configs* viejos, y recrear con **New → Other → Maven → Maven Module**, tildando **"Create a simple project (skip archetype selection)"** en el paso del archetype. Con eso el módulo se genera vacío: solo `pom.xml` + carpetas `src`, sin ninguna clase.
- **Concepto para profundizar** ⭐: diferencia entre **Maven archetypes** (plantillas de proyecto que generan código de arranque, como `maven-archetype-quickstart`) y un módulo Maven "plano" sin archetype. También: qué es una **facet/nature de proyecto** en Eclipse (metadata en `.project` y en vistas específicas como el Boot Dashboard) y por qué puede sobrevivir a borrar archivos si no se limpia la metadata del workspace — es la misma idea de "estado que vive fuera del código fuente" que en otros contextos causa bugs de caché.

---

## 7. Pendientes identificados (no resueltos aún)

- 🟡 **CORS** configurado para `localhost:3000` (puerto de React) — mencionaste que el front sería Angular, que por defecto usa `4200`. Se va a romper silenciosamente en cuanto se conecte el front real.
- 🟡 **Credenciales hardcodeadas en `application.properties`** (usuario/password de Postgres, `jwt.secret`) en texto plano. Recomendación pendiente de aplicar: variables de entorno o `application-local.properties` fuera de Git, con un `.example` como referencia en el repo.
- 🟡 `spring.application.name=platform` — quedó desactualizado tras el rename a `sportbooking` (solo estético, aparece en los logs).
- 🟡 Falta constraint `UNIQUE` en `email`/`username` a nivel de base de datos — hoy la unicidad se valida solo en la capa de aplicación (`UserService`), lo cual no protege ante condiciones de carrera (dos requests simultáneos).
- 🟡 Nullability real (`NOT NULL`) no está declarada en varias columnas clave (`email`, `username`, `password_hash`, y ahora también `venue.name`).
- 🟡 No hay manejo global de errores (`@ControllerAdvice`/`@ExceptionHandler`) — cada controller maneja sus excepciones de forma ad hoc. `VenueController` ya usa un patrón más prolijo (excepción custom + catch específico) que puede servir de base cuando se centralice.
- 🟡 No hay tests (unitarios ni de integración) en ningún módulo, incluido `venue-module`.
- 🟡 Modelo de datos completo del dominio de reservas (`court`, `time_block`, `court_schedule`, `reservation`, `user_venue_role`) ya existe en la base de datos, pero todavía no tiene entidades/repos/services/controllers en el código Java — próximo bloque grande de trabajo. `venue` ya quedó resuelto como primer caso.
- 🟡 **Remodelado eventual de la base de datos**: se identificó que la validación de negocio (unicidad, nullability) va por delante de los constraints reales de la tabla. Cuando se ataque el punto de constraints, evaluar si conviene una revisión más amplia del esquema (`platform.*`) en vez de ir parcheando tabla por tabla.

---

## 8. Próximos pasos acordados

1. Agregar constraint `NOT NULL` a `venue.name` (y evaluar `UNIQUE` si el negocio lo requiere) — primer paso concreto antes de seguir con módulos nuevos.
2. Evaluar si conviene una revisión más amplia del esquema de base de datos en paralelo a los próximos módulos, en vez de ir parcheando tabla por tabla.
3. Crear el siguiente módulo por dominio (`court-module`, que depende de `venue` vía `court.fk_venue_id`), aplicando ya el flujo correcto de creación de módulo en Eclipse (Maven Module + skip archetype) documentado en el punto 6.
4. Retomar el resto de los pendientes de la sección 7 (CORS, credenciales, `@ControllerAdvice`, testing) cuando corresponda.
