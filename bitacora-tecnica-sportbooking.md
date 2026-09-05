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
- 🟡 Modelo de datos completo del dominio de reservas (`reservation`, `user_venue_role`) ya existe en la base de datos, pero todavía no tiene entidades/repos/services/controllers en el código Java. `venue`, `court`, `time_block` y `court_schedule` ya quedaron resueltos (ver puntos 5, 9, 10 y 12); `reservation` es el próximo (ver punto 12).
- 🟡 **Remodelado eventual de la base de datos**: se identificó que la validación de negocio (unicidad, nullability) va por delante de los constraints reales de la tabla. Cuando se ataque el punto de constraints, evaluar si conviene una revisión más amplia del esquema (`platform.*`) en vez de ir parcheando tabla por tabla.
- 🟡 **[Prioritario, ver punto 14]** Loop infinito de serialización JSON por la relación bidireccional `User.globalRoles` ↔ `UserGlobalRole.user` — detectado al serializar `Reservation.reservedByUser`. Bloqueante para testear `reservation-module`.
- 🟡 **[Prioritario, ver punto 15]** Migrar todos los controllers de devolver entities JPA directo a Response DTOs específicos — pendiente general que dejó de ser "algún día" y pasó a prioritario, porque el bug de arriba es consecuencia directa de no tenerlo resuelto.

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

### Próximo paso sugerido (cumplido, ver punto 12)
`court_schedule` (depende de `court` + `time_block`, ambos ya resueltos) es el siguiente módulo de dominio. Va a ser el primer caso con **dos** relaciones `@ManyToOne` simultáneas (`fk_court_id` + `fk_time_block_id`) más un campo propio sin FK (`day_of_week`), y el primer lugar natural para reutilizar tanto `CourtNotFoundException` como algo de `time-block-module` en el mismo service.

---

## 12. Módulo `court-schedule-module` — horarios habilitados por cancha

- **Estado**: 🟢 Resuelto y testeado end-to-end en Postman por el usuario ("anda todo ok"), incluyendo los casos de validación cruzada nueva (ver más abajo).
- **Estructura**: mismo patrón que `court-module`/`time-block-module` — `entity` → `dto` → `exception` → `repository` → `service` → `controller`, package `com.example.sportbooking.courtschedule`.
- **Primer módulo con dos relaciones `@ManyToOne` simultáneas hacia módulos distintos**: `CourtSchedule.court` (`fk_court_id` → `court-module`) y `CourtSchedule.timeBlock` (`fk_time_block_id` → `time-block-module`). El `pom.xml` del módulo depende de ambos; reutiliza `CourtRepository`/`CourtNotFoundException` de uno y `TimeBlockRepository`/`TimeBlockNotFoundException` del otro en el mismo `CourtScheduleService`, sin necesidad de crear excepciones nuevas para esos dos casos.
- **`day_of_week` como `Short`, no `Integer`**: la columna es `int2` (smallint) en Postgres. Se eligió el tipo Java que mapea 1 a 1 con el tipo real de la columna, mismo criterio que se usó con `LocalTime` en `time-block-module` (usar el tipo que representa exactamente lo que hay en la base, no el "default cómodo"). Convención adoptada: `0` = domingo ... `6` = sábado (la misma que usa `EXTRACT(DOW)` de Postgres), validada en el service (`validateDayOfWeek`, rango 0–6 → 400 si no cumple).
- **Validación cruzada nueva: mismo `venue` en `court` y `time_block`** — cuarta categoría de validación en el proyecto (sumada a "campo obligatorio faltante", "referencia inexistente" y "consistencia entre dos campos del mismo recurso" de `time-block-module`). Acá la consistencia es entre **dos relaciones**, no dos campos simples: no tiene sentido un `CourtSchedule` que combine una cancha de un venue con una franja horaria de otro. Extraída a `validateSameVenue(Court, TimeBlock)`, reutilizada en `create()` y `update()`.
- **Re-validación sobre el estado final de la entity en el update** (mismo criterio que `validateTimeRange` en `time-block-module`): si el `PUT` parcial manda solo `court_id` (o solo `time_block_id`), `validateSameVenue()` se re-chequea después de aplicar el cambio sobre la entity persistida, porque el otro lado de la relación puede venir del estado previamente guardado.
- **`is_enabled` con `Boolean` wrapper, seteado a mano en el alta**: mismo patrón que `Court.is_active` — se necesita distinguir "no vino en el body" (`null`) de "vino en `false` a propósito" en el update parcial, y no se confía en el `DEFAULT true` de la columna (Hibernate manda el valor explícito del campo, `null` incluido, si no se setea a mano en el constructor).
- **Filtro por cancha**: `CourtScheduleRepository.findByCourt_Id(Long courtId)`, expuesto como `GET /court-schedules?courtId=X` (opcional) — pensado para la pantalla "grilla semanal de esta cancha" del front.
- **Seguridad heredada sin configuración extra**: igual que los módulos anteriores, `/court-schedules/**` queda protegido automáticamente por `.anyRequest().authenticated()`.
- **Testeado**: confirmado por el usuario ("anda todo ok") sobre el checklist completo (alta correcta, los 4 casos de campo faltante, rango de `day_of_week`, referencias inexistentes con 404, el caso nuevo de venues distintos con 400, filtro por `courtId`, update parcial sin pisar campos no enviados, la re-validación de "mismo venue" tras un `PUT` parcial, delete + 404 posterior, y sin token).
- **Incidente de proceso (no de código)**: el primer `.zip` que el usuario subió para revisión venía con la estructura de carpetas desarmada (intermedios faltantes); no correspondía a un error de generación de este lado — se confirmó comparando contra la copia de trabajo, contenido idéntico. Reenviado correctamente en el segundo intento. No amerita cambio de proceso, ya que el punto 11 (entrega de solo archivos nuevos/modificados respetando la estructura real) ya cubre el caso.
- **Concepto para profundizar** ⭐: validación de consistencia **entre relaciones** (no entre campos simples) como extensión natural del patrón cross-field validation del punto 10 — mismo problema conceptual (revalidar contra el estado final, no contra el payload parcial), aplicado a un grafo de objetos en vez de dos primitivos.

### Próximo paso sugerido (cumplido, ver punto 13)
`reservation` es el último recurso del dominio de reservas sin módulo Java (depende de `court` + `time_block`, mismas dos relaciones que `court_schedule`, más `fk_reserved_by_user_id`/`fk_reserved_for_user_id` hacia `user-module`) — primer caso con **tres** módulos externos referenciados desde un mismo service.

---

## 13. Módulo `reservation-module` — CRUD de reservas

- **Estado**: 🔴 Implementado y **testeado end-to-end en Postman — OK**. El checklist de abajo se corrió completo tras resolver los puntos 14 y 16 (loop de serialización + Response DTOs); las respuestas ya devuelven `ReservationResponseDTO` en vez de la entity completa, confirmado a mano en Postman.
- **Estructura**: mismo patrón que los módulos anteriores — `entity` → `dto` → `exception` → `repository` → `service` → `controller`, package `com.example.sportbooking.reservation`.
- **Primer módulo con tres relaciones `@ManyToOne` hacia módulos externos distintos en el mismo service**: `court` (`court-module`), `timeBlock` (`time-block-module`), `reservedByUser`/`reservedForUser` (`user-module`, dos relaciones al mismo tipo `User`). El `pom.xml` de `reservation-module` depende de los tres.
- **`UserNotFoundException` nueva en `user-module`**: primer módulo que necesita resolver "¿existe el usuario con este id?" como una referencia externa (antes `user-module` solo validaba duplicados de email/username en el alta, nunca resolvía por id desde afuera). Mismo criterio que `CourtNotFoundException`/`TimeBlockNotFoundException`.
- **`reserved_by_user_id` no se actualiza en el `PUT`**: decisión de diseño — identifica quién originó la reserva, es un dato histórico que no debería cambiar después del alta (a diferencia de `court_id`/`time_block_id`, que sí tiene sentido reprogramar).
- **Validación nueva — exclusión mutua entre dos campos opcionales**: `reserved_for_user_id` y `reserved_for_name` no pueden venir cargados los dos a la vez (400). Es una **quinta categoría** de validación en el proyecto, distinta de las cuatro anteriores (campo obligatorio faltante, referencia inexistente, consistencia entre dos campos del mismo recurso, consistencia entre dos relaciones): acá la regla es "como mucho uno de los dos", no "los dos tienen que coincidir". Si no viene ninguno, se asume que la reserva es para quien la hizo (`reservedByUser`).
- **Validación nueva — no permitir doble reserva (regla de negocio central de un booking system)**: no puede haber dos reservas activas (`status != CANCELLED`) para la misma cancha + franja horaria + fecha. Se resuelve con una query derivada (`existsByCourt_IdAndTimeBlock_IdAndDateAndStatusNot`) en el alta, y su variante con `...AndIdNot` en el update (para no chocar contra la propia reserva al reprogramar sin cambiar nada).
  - **Primer uso de `IllegalStateException`** en el proyecto (hasta ahora todo era `IllegalArgumentException` para 400 o las `NotFoundException` para 404): el dato de entrada es válido en sí mismo, lo que falla es el *estado actual del sistema*. Mapeado en el controller a **409 CONFLICT** — primer uso de ese código HTTP en el proyecto.
  - **Limitación conocida, documentada a propósito**: esta validación no protege contra una condición de carrera real (dos requests simultáneos para el mismo turno) — mismo problema de fondo que ya señalaba el punto 7 para email/username. Para eso haría falta un `UNIQUE` constraint en Postgres sobre `(fk_court_id, fk_time_block_id, date)` con alguna condición sobre `status`, o una transacción con lock explícito. Queda en el radar del "remodelado eventual de la base de datos" (punto 7).
- **Fix de seguridad en `user-module`, motivado por este módulo**: se agregó `@JsonIgnore` en `User.password_hash`. Hasta ahora `User` nunca viajaba completo en una respuesta JSON (registro devuelve solo `username`, login un token, `/me` arma un `Map` a mano con `email`+`authorities`); `reservation-module` es el primer caso que serializa un `User` anidado de verdad (`Reservation.reservedByUser`/`reservedForUser`), así que sin el `@JsonIgnore` el hash de la contraseña se filtraba en la respuesta de `/reservations`.
- **`status`/`payment_status` como conjunto cerrado de strings validado en el service** (`VALID_STATUSES`, `VALID_PAYMENT_STATUSES`), no un `enum` de Java todavía — la columna es `varchar` sin `CHECK` constraint en la base. En el alta se fuerzan a mano en `"PENDING"` (el DTO no los lee en `create()`); en el update sí se pueden cambiar, validados contra la lista permitida.
- **Filtros combinables en `GET /reservations`**: `?courtId=X`, `?date=YYYY-MM-DD`, o ambos juntos — pensado para pantallas típicas de un booking system ("agenda de esta cancha", "reservas de hoy", "agenda de esta cancha para hoy").
- **Seguridad heredada sin configuración extra**: igual que los módulos anteriores, `/reservations/**` queda protegido automáticamente por `.anyRequest().authenticated()`.
- **Checklist de Postman corrido — resultado OK**, incluyendo la verificación de que las respuestas usan `ReservationResponseDTO` (sin `password_hash`, sin `globalRoles`, sin loop de serialización):
  - Alta con todos los datos válidos → 201.
  - Alta sin `date`/`court_id`/`time_block_id`/`reserved_by_user_id` → 400 (cuatro casos).
  - Alta con `reserved_for_user_id` **y** `reserved_for_name` cargados a la vez → 400 (exclusión mutua).
  - Alta con `court_id`/`time_block_id`/`reserved_by_user_id`/`reserved_for_user_id` inexistente → 404 en cada caso.
  - Dos altas para la misma cancha + franja + fecha → la segunda debe dar **409**, no 400 ni 500.
  - Cancelar una reserva (`PUT` con `status: "CANCELLED"`) y volver a reservar el mismo turno → debe permitirlo (la cancelada no bloquea).
  - `GET /reservations?courtId=X`, `?date=YYYY-MM-DD` y con ambos combinados.
  - Update parcial: mandar solo `{"status": "CONFIRMED"}` y confirmar que no se pisan los demás campos.
  - `status`/`payment_status` con un valor fuera de la lista permitida → 400.
  - Delete + `GET` posterior → 404.
  - Sin token → 401/403 (heredado).
- **Concepto para profundizar**: distinción entre las tres categorías de error HTTP que ahora conviven en el proyecto — **400** (dato de entrada inválido), **404** (recurso referenciado no existe) y **409** (el request es válido pero choca con el estado actual del sistema) — y por qué mapear cada una a una excepción de Java distinta (`IllegalArgumentException`, `XxxNotFoundException`, `IllegalStateException`) en vez de una sola genérica hace que el controller pueda traducir sin parsear mensajes.

---

## 14. 🔴 Loop infinito de serialización JSON por relación bidireccional `User` ↔ `UserGlobalRole`

- **Síntoma**: al hacer `POST /reservations`, la respuesta JSON de `reservedByUser.globalRoles[0].user.globalRoles[0].user...` se repite indefinidamente (en la práctica termina en `StackOverflowError`).
- **Causa raíz**: `User.globalRoles` (`@OneToMany`, mappedBy `user`) y `UserGlobalRole.user` (`@ManyToOne`) son una **relación bidireccional** sin cortar para serialización. Jackson recorre el grafo de objetos completo al serializar un `User`: `User → globalRoles → UserGlobalRole → user → globalRoles → ...`, sin ningún punto de corte. Mientras `User` nunca viajaba completo en una respuesta (ver punto 13, ítem del `@JsonIgnore`), el problema no se manifestaba — apareció recién ahora que `Reservation.reservedByUser` serializa un `User` de verdad.
- **No es un problema de `court`/`timeBlock`/`venue`**: esas relaciones son unidireccionales desde el lado que se serializa (ninguna entity tiene una colección de vuelta que cierre el ciclo), por eso no generan el mismo loop.
- **Fix aplicado — parche rápido**: `@JsonManagedReference` en `User.globalRoles` + `@JsonBackReference` en `UserGlobalRole.user`. Corta el ciclo sin tocar arquitectura.
  - **Archivos modificados**:
    - `user-module/src/main/java/com/example/sportbooking/user/entity/User.java`
    - `user-module/src/main/java/com/example/sportbooking/user/entity/UserGlobalRole.java`
  - **Limitación conocida (a propósito, no resuelta acá)**: es un parche a nivel de infraestructura de serialización, no resuelve que se sigan devolviendo entities JPA completas desde los controllers. El fix de fondo (mapear a DTOs de respuesta específicos por endpoint, ver punto 7) queda pendiente como punto 15.2.
  - **Deuda técnica introducida**: el mismo problema va a reaparecer con `UserVenueRole` (misma bidireccionalidad `User` ↔ `UserVenueRole`, módulo todavía sin construir) si no se aplica la misma anotación ahí cuando se cree.
  - **Verificado**: compila (`mvn compile`) y probado end-to-end en Postman — ver punto 13 y punto 16 (el fix de fondo terminó siendo el Response DTO, este parche quedó como defensa en profundidad).
- **Por qué importaba resolverlo**: bloqueaba en la práctica el testeo end-to-end de `reservation-module` (punto 13) — cualquier `POST`/`GET` que trajera un `reservedByUser` fallaba.
- **Concepto para profundizar** ⭐: relaciones bidireccionales en JPA y el problema de **serialización cíclica** que generan por default en cualquier librería que recorra el grafo de objetos (Jackson, pero el mismo problema aplica a otras). Es el mismo tipo de bug que el punto 3.2 (`LazyInitializationException`) en el sentido de que es invisible hasta que un flujo nuevo *usa* la relación de una forma que los flujos anteriores no usaban — vale la pena tenerlo fresco para explicar en entrevista el patrón general: "una relación bidireccional en el modelo de persistencia no debería exponerse tal cual en el modelo de la API".

---

## 15. Pendientes inmediatos para el próximo chat (prioridad, en este orden)

1. ✅ ~~Resolver el loop de serialización `User` ↔ `UserGlobalRole`~~ — resuelto, ver punto 14. Confirmado con `mvn compile` en el entorno local.
2. ✅ ~~Migrar los controllers a Response DTOs~~ — resuelto, ver punto 16. Confirmado con `mvn compile` y con testeo real en Postman: las respuestas ya no exponen `password_hash`/`globalRoles`.
3. ✅ ~~Checklist de testeo de `reservation-module` en Postman~~ — corrido completo, OK (ver punto 13).
4. 🟡 **`reservation-module` no valida que `court` y `timeBlock` sean del mismo venue** — ver punto 17, deferido a propósito.
5. Retomar el resto de los pendientes de la sección 7 (CORS para Angular, credenciales hardcodeadas, `UNIQUE`/`NOT NULL` en base, `@ControllerAdvice` centralizado, testing automatizado, remodelado del esquema) cuando corresponda — ninguno es bloqueante para lo anterior.

---

## 16. 🔴 Migración de los controllers a Response DTOs (fix de fondo del punto 14)

- **Criterio elegido**: un Response DTO por entity (no uno por endpoint), reutilizado en `create`/`findAll`/`findById`/`update` de cada controller. Si algún endpoint puntual necesita más adelante una forma de respuesta distinta, se arma un DTO a medida para ese caso sin tocar el DTO general.
- **Mecanismo de mapeo — MapStruct**: primer uso real de la dependencia en el proyecto (estaba en el `pom.xml` desde el principio, pero sin ningún `@Mapper` hasta ahora). Se agregó `lombok-mapstruct-binding` al `pom.xml` raíz (en `annotationProcessorPaths`, junto a `mapstruct-processor` y `lombok`) porque sin ese binding, Lombok y MapStruct-processor pueden generar código en "rounds" de anotaciones que no se ven entre sí — el binding fuerza a MapStruct a esperar a que Lombok termine de generar los getters/setters de una clase antes de mapearla.
- **Dónde vive el mapeo — en el controller, no en el service**: el service sigue devolviendo entities (`Venue`, `Court`, etc.); el controller inyecta el `XxxMapper` correspondiente y lo llama justo antes de armar el `ResponseEntity`. Motivo: la capa de servicio trabaja con el modelo de dominio (JPA), no con el contrato de la API REST — si mañana otro consumidor del service (un job batch, un test) necesita la entity completa, no tiene que lidiar con un DTO recortado pensado para HTTP.
- **Estructura en cascada, igual que las relaciones `@ManyToOne` entre módulos**: cada Response DTO nuevo referencia los Response DTOs de sus módulos dependientes (no ids sueltos), y cada Mapper nuevo usa (`@Mapper(uses = ...)`) los mappers de esos módulos:
  - `VenueResponseDTO` (`venue-module`) — sin dependencias, es la hoja del árbol.
  - `CourtResponseDTO` / `TimeBlockResponseDTO` — cada uno con un `VenueResponseDTO venue` anidado (`CourtMapper`/`TimeBlockMapper` usan `VenueMapper`).
  - `CourtScheduleResponseDTO` — con `CourtResponseDTO court` y `TimeBlockResponseDTO timeBlock` anidados (`CourtScheduleMapper` usa `CourtMapper` y `TimeBlockMapper`).
  - `UserResponseDTO` (`user-module`) — sin `password_hash` ni `globalRoles`: no es que se "escondan" con `@JsonIgnore`/`@JsonBackReference` como en la entity (ver punto 13 y 14), directamente no existen en la forma que viaja por la API.
  - `ReservationResponseDTO` — el más grande: `CourtResponseDTO court`, `TimeBlockResponseDTO timeBlock`, `UserResponseDTO reservedByUser`, `UserResponseDTO reservedForUser` (puede ser `null`, MapStruct genera el null-check solo). `ReservationMapper` usa `CourtMapper`, `TimeBlockMapper` y `UserMapper` (este último resuelve los dos campos `User` del mismo tipo sin declararse dos veces).
- **Por qué esto es el fix de fondo del punto 14, no otro parche**: con `ReservationResponseDTO` en uso, `reservedByUser`/`reservedForUser` nunca vuelven a ser un `User` completo — son `UserResponseDTO`, que no tiene forma de llegar a `UserGlobalRole` porque el campo `globalRoles` ni siquiera existe en el DTO. El parche `@JsonManagedReference`/`@JsonBackReference` en `User`/`UserGlobalRole` (punto 14) queda **redundante para las respuestas actuales de la API**, pero se deja igual como defensa en profundidad, por si algún endpoint futuro devuelve la entity `User` directo por error.
- **`UserController` no existe / no se tocó**: `user-module` no tiene controller propio — hoy `User` solo se expone vía `auth-module` (que ya arma sus propias respuestas curadas a mano) y, a partir de ahora, vía `UserResponseDTO` anidado en `reservation-module`. `UserMapper` se creó igual porque `ReservationMapper` lo necesita.
- **Archivos nuevos** (6 Response DTOs + 6 Mappers):
  - `venue-module/.../dto/VenueResponseDTO.java`, `venue-module/.../mapper/VenueMapper.java`
  - `court-module/.../dto/CourtResponseDTO.java`, `court-module/.../mapper/CourtMapper.java`
  - `time-block-module/.../dto/TimeBlockResponseDTO.java`, `time-block-module/.../mapper/TimeBlockMapper.java`
  - `court-schedule-module/.../dto/CourtScheduleResponseDTO.java`, `court-schedule-module/.../mapper/CourtScheduleMapper.java`
  - `user-module/.../dto/UserResponseDTO.java`, `user-module/.../mapper/UserMapper.java`
  - `reservation-module/.../dto/ReservationResponseDTO.java`, `reservation-module/.../mapper/ReservationMapper.java`
- **Archivos modificados**:
  - `pom.xml` (raíz) — agregado `lombok-mapstruct-binding` en `annotationProcessorPaths`.
  - `venue-module/.../controller/VenueController.java`, `court-module/.../controller/CourtController.java`, `time-block-module/.../controller/TimeBlockController.java`, `court-schedule-module/.../controller/CourtScheduleController.java`, `reservation-module/.../controller/ReservationController.java` — todos con el mismo cambio: constructor inyecta el `XxxMapper` además del `XxxService`, y `create`/`findAll`/`findById`/`update` mapean el resultado del service antes de devolverlo.
- **Verificado**: compila (`mvn compile`) y probado end-to-end en Postman — las respuestas de `/venues`, `/courts`, `/time-blocks`, `/court-schedules` y `/reservations` ya usan los Response DTOs, confirmado a mano contra el JSON real devuelto por `POST /reservations` (sin `password_hash`, sin `globalRoles`, sin loop).
- **Concepto para profundizar** ⭐: la diferencia entre el **modelo de persistencia** (entities JPA, con sus relaciones bidireccionales y su acoplamiento al schema de la base) y el **modelo de la API** (DTOs, con la forma que el contrato HTTP necesita) — y por qué mapear en el borde (controller) en vez de devolver entities directo no es solo "más prolijo", sino que es lo que evita que un cambio interno en una entity (agregar una relación, cambiar un tipo) rompa silenciosamente el contrato público de la API. MapStruct como alternativa a mapeo manual: genera el código de conversión en tiempo de compilación (no reflection en runtime, a diferencia de librerías como ModelMapper), así que un error de tipos entre entity y DTO se detecta compilando, no en producción.

---

## 17. 🟡 `reservation-module` no valida que `court` y `timeBlock` sean del mismo venue

- **Síntoma**: detectado probando `POST /reservations` en Postman — se pudo crear una reserva combinando una `Court` del venue "Complejo Norte" (id 2) con un `TimeBlock` del venue "Complejo Sur" (id 3). El `POST` respondió 201 sin ninguna validación cruzada.
- **Causa raíz**: es la misma categoría de validación que ya existe en `CourtScheduleService.validateSameVenue()` (ver punto 12) — dos relaciones `@ManyToOne` del mismo recurso (`court`, `timeBlock`) que deberían apuntar al mismo `venue` — pero `ReservationService` nunca la implementó. `Reservation` tiene exactamente el mismo par de relaciones que `CourtSchedule`, así que en rigor hereda el mismo problema de consistencia, solo que no se había notado hasta ahora porque el testeo de `reservation-module` estaba bloqueado por el punto 14.
- **Fix pendiente (deferido a propósito, no bloqueante)**: replicar `validateSameVenue(Court, TimeBlock)` en `ReservationService.create()` y `.update()`, con el mismo criterio que ya usa `CourtScheduleService.update()` — revalidar contra el estado FINAL de la entity después del merge parcial, no contra el DTO crudo, para cubrir el caso de un `PUT` que solo cambia `court_id` y deja un `timeBlock` heredado de otro venue.
- **Nota de diseño para cuando se resuelva**: el método queda duplicado literal entre `CourtScheduleService` y `ReservationService` si se copia tal cual. Vale la pena evaluar en ese momento si conviene extraerlo a un lugar compartido (¿un mapper o helper en `court-module` o `time-block-module`, ya que ambos dependen de esos dos módulos?) en vez de mantener dos copias de la misma lógica — variante del principio DRY aplicado a validaciones de negocio repetidas entre módulos.
- **Por qué se decidió no resolverlo ahora**: decisión explícita del desarrollador durante el testeo — no es un bug que bloquee otras tareas, es una regla de negocio a agregar cuando corresponda.
- **Concepto para profundizar**: por qué las validaciones de consistencia entre relaciones (a diferencia de las validaciones de campo simple, ver punto 2.2/2.3) tienden a aparecer tarde en el desarrollo — solo se hacen evidentes cuando dos módulos que antes se probaban por separado empiezan a combinarse en un flujo real (mismo patrón de fondo que el punto 14: un bug "invisible" hasta que un caso de uso nuevo ejercita una combinación que antes nadie probó).

---

## Convención de entrega (zips)

A partir de esta entrada: cada zip generado en el chat contiene **únicamente los archivos nuevos o modificados** en esa sesión, con su ruta completa desde la raíz del proyecto (para poder copiarlos directo sobre el proyecto local sin pisar nada más). La bitácora es la fuente de verdad de *qué* cambió — cada entrada de esta bitácora que corresponda a un cambio de código debe listar explícitamente los **archivos modificados** (como en el punto 14), así el contenido del zip de esa sesión y lo documentado acá coinciden 1 a 1.
