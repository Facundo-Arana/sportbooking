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

## 9. Módulo `court-module` — CRUD de canchas

- **Estructura**: idéntica a `venue-module` — `entity` → `dto` → `exception` → `repository` → `service` → `controller`, package `com.example.sportbooking.court`.
- **Dependencia nueva entre módulos**: `court-module` depende de `venue-module` en su `pom.xml` (mismo mecanismo que `auth-module` → `user-module`). Hace falta porque `Court` tiene una relación real hacia `Venue` (`fk_venue_id`), no solo un id suelto.
- **Relación `@ManyToOne` en vez de guardar el id a mano**: `Court.venue` es un objeto `Venue` con `@JoinColumn(name = "fk_venue_id")`, igual patrón que `UserGlobalRole.user` / `UserGlobalRole.role` en `user-module`. Esto le da a Hibernate el JOIN gratis (por ejemplo, `court.getVenue().getName()` sin query manual) — usar un `Long venueId` suelto en la entity hubiera funcionado pero perdés esa navegación.
- **Reutilización de `VenueNotFoundException` en vez de crear una excepción nueva**: cuando `CourtDTO.venue_id` no corresponde a ningún venue existente, el service tira la misma `VenueNotFoundException` del `venue-module` (404), no una nueva. La distinción real es conceptual — "el recurso referenciado no existe" — no depende de qué módulo la lanza. `CourtNotFoundException` sigue existiendo aparte para el caso "la cancha en sí no existe" (`GET/PUT/DELETE /courts/{id}`).
- **Filtro por venue con query derivado**: `CourtRepository.findByVenue_Id(Long venueId)` — el `_` navega la relación (`Court.venue.id`) sin escribir `@Query`. Expuesto como `GET /courts?venueId=X` (parámetro opcional). Se agregó pensando en la pantalla "canchas de esta sede" del front y porque `court_schedule`/`reservation` (próximos módulos) también van a necesitar filtrar canchas por venue.
- **DTO con `Boolean` (wrapper) en `indoor` e `is_active`, no `boolean` primitivo**: en el update parcial (`PUT /courts/{id}`) hace falta distinguir "el cliente no mandó el campo" (`null`, no tocar) de "el cliente mandó `false` a propósito". Con `boolean` primitivo esa distinción es imposible (siempre vale `false` por default). Mismo problema que no se dio en `VenueDTO` porque ahí todos los campos ya eran `String` (naturalmente nullable).
- **Seguridad heredada sin configuración extra**: igual que `venue-module`, `/courts/**` queda protegido automáticamente por `.anyRequest().authenticated()`. No hizo falta tocar `SecurityConfiguration`.
- **Pendiente de testear con Postman** (no se hizo en este chat, hacerlo antes de dar el módulo por cerrado):
  - Alta con `venue_id` válido → 201.
  - Alta con `venue_id` inexistente → 404 (no 400 — confirmar que se distingue bien de "nombre vacío").
  - Alta sin `venue_id` o sin `name` → 400.
  - `GET /courts?venueId=X` → solo trae canchas de esa sede.
  - Update parcial: mandar solo `{"indoor": false}` y confirmar que el resto de los campos no se pisa.
  - Sin token → 401/403 (heredado, no debería hacer falta nada especial).
- **Concepto para profundizar**: cuándo conviene una excepción "compartida" entre módulos (como `VenueNotFoundException` acá) vs. una por módulo — el trade-off es acoplamiento (court-module ya depende de venue-module de todos modos, así que no suma acoplamiento nuevo) vs. claridad de mensajes de error específicos por dominio.

### Próximo paso sugerido (cumplido, ver punto 10)
`court_schedule` (depende de `court` + `time_block`) es el siguiente candidato natural: ya vas a tener el patrón de "reutilizar `CourtNotFoundException` desde otro módulo" fresco, y ahí sí vas a necesitar `time_block` como módulo nuevo antes.

---

## 10. Módulo `time-block-module` — CRUD de franjas horarias

- **Estado**: 🔴 Resuelto (compila y estructura verificada; testeo end-to-end con Postman pendiente de detalle por endpoint, ver checklist más abajo — el usuario confirmó que "todo ok" tras probarlo).
- **Estructura**: mismo patrón que `court-module` — `entity` → `dto` → `exception` → `repository` → `service` → `controller`, package `com.example.sportbooking.timeblock` (sin guión bajo en el nombre del paquete Java, a diferencia de la tabla `time_block`; convención estándar de Java).
- **Dependencia nueva entre módulos**: `time-block-module` depende de `venue-module`, mismo mecanismo y mismo motivo que `court-module` (`TimeBlock.venue` es un `@ManyToOne` hacia `Venue` vía `fk_venue_id`).
- **`LocalTime` (java.time) en vez de `java.sql.Time`** para `start_time`/`end_time`: Hibernate 6 mapea `LocalTime` nativamente a la columna `time` de Postgres, y Jackson lo serializa/deserializa como `"HH:mm:ss"` sin configuración adicional (el módulo `jackson-datatype-jsr310` ya viene transitivamente con `spring-boot-starter-web`). Se prefirió sobre `java.sql.Time` por ser la API de fecha/hora moderna de Java (paquete `java.time`, JSR-310) en vez de la heredada de JDBC.
- **Validación cruzada nueva (no existía en `venue` ni `court`)**: `end_time` debe ser posterior a `start_time`. Es una tercera categoría de validación además de "campo nulo → 400" y "referencia inexistente → 404": una regla de consistencia *entre dos campos del mismo recurso*. Se extrajo a un método privado `validateTimeRange()` reutilizado en `create()` y `update()`.
- **Re-validación sobre el estado final de la entity en el update, no sobre el DTO**: en el `PUT` parcial, si el cliente manda solo `end_time` (o solo `start_time`), la regla `end_time > start_time` se re-chequea después de aplicar los cambios sobre la entity ya persistida — no alcanza con validar los campos que vinieron sueltos en el body, porque cualquiera de los dos valores puede venir del estado previo.
- **Consecuencia en el controller**: a diferencia de `CourtController.update()` (que solo capturaba `CourtNotFoundException | VenueNotFoundException`), `TimeBlockController.update()` también captura `IllegalArgumentException`, porque ahora el `service.update()` sí puede fallar por validación de negocio y no solo por "no encontrado".
- **Sin `created_at` ni `is_active`**: a diferencia de `Venue`/`Court`, la tabla `time_block` no tiene esas columnas (ver `sportbooking.sql`), así que el constructor de alta no necesita setear nada "a mano" más allá de lo que manda el DTO.
- **Filtro por venue con query derivado**: `TimeBlockRepository.findByVenue_Id(Long venueId)`, expuesto como `GET /time-blocks?venueId=X` (opcional) — mismo patrón que `CourtRepository`. Lo va a necesitar `court_schedule` para armar combinaciones cancha+franja de una misma sede.
- **Seguridad heredada sin configuración extra**: igual que `venue`/`court`, `/time-blocks/**` queda protegido automáticamente por `.anyRequest().authenticated()`.
- **Testeado**: confirmado por el usuario ("todo ok") tras probar los endpoints. No quedó registrado en esta bitácora el detalle caso por caso (a diferencia del punto 5), a pedir la próxima vez si se necesita el desglose.
- **Concepto para profundizar**: validaciones de **consistencia entre campos** (cross-field validation) como tercera categoría junto a "campo obligatorio faltante" y "referencia a otro recurso inexistente" — y por qué, en un update parcial, hay que validar contra el estado resultante completo y no contra el payload recibido.

---

## 11. Nota de proceso: entrega de archivos en el chat

- A partir de esta sesión, ante cambios de código el asistente entrega **solo los archivos nuevos o modificados**, respetando la estructura real de carpetas dentro de `sportbooking/` (la carpeta raíz que contiene el `pom.xml` padre) — no el proyecto completo comprimido. Acordado para minimizar tokens dado el plan gratuito.

### Próximo paso sugerido
`court_schedule` (depende de `court` + `time_block`, ambos ya resueltos) es el siguiente módulo de dominio. Va a ser el primer caso con **dos** relaciones `@ManyToOne` simultáneas (`fk_court_id` + `fk_time_block_id`) más un campo propio sin FK (`day_of_week`), y el primer lugar natural para reutilizar tanto `CourtNotFoundException` como algo de `time-block-module` en el mismo service.
