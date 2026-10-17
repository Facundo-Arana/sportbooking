# Design

## Context

- Backend Spring Boot 3.4.7 sobre Java 17 (aunque el host tenga 21), Maven multi-módulo; `app` es el único jar ejecutable (`app/target/app-0.0.1-SNAPSHOT.jar`).
- PostgreSQL es obligatorio. Flyway es dueño del esquema dentro de `platform` y Hibernate solo valida (`ddl-auto=validate`): la app falla si la base no está migrada o no está lista.
- `DevDataSeeder` corre solo con el perfil `dev`, es idempotente (si ya hay venues no siembra) y hoy crea el admin con credenciales fijas (`admin@sportbooking.dev` / `admin123`).
- La app ya resuelve `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y `JWT_SECRET` por entorno; CORS se configura con `app.cors.allowed-origins`. El puerto es 8081.
- Los secretos locales viven en `application-local.properties` (no versionado), importado de forma opcional. En contenedor no se usa: todo entra por variables de entorno.
- El wrapper es `only-script` (descarga Maven) y `.gitattributes` fuerza LF en `mvnw`, así que el build Linux es reproducible.
- Los tests de integración usan Testcontainers y ya requieren Docker en el host; no dependen del compose.

## Goals / Non-Goals

**Goals:**

- Un clon limpio pasa a API operativa con un solo `docker compose up --build`.
- Cero dependencias de host salvo Docker + Compose v2.
- Credenciales resueltas con defaults de desarrollo utilizables, sobreescribibles y nunca requeridas a mano.
- Arranque ordenado, estado visible, datos persistentes y reseteo documentado.
- Mantener intacto el flujo actual (`spring-boot:run` + Postgres local + `application-local.properties`).

**Non-Goals:**

- Despliegue a producción, TLS, reverse proxy, registro de imágenes o CI de publicación.
- Containerizar el frontend.
- Agregar Actuator, métricas u observabilidad.
- Cambiar endpoints, esquema, reglas de negocio o tests.
- Soportar secretos de Docker/Swarm; el compose es explícitamente de desarrollo local.

## Decisions

### 1. Compose con dos servicios, no una imagen todo-en-uno

`docker-compose.yml` define `db` (PostgreSQL) y `app` (el backend), en la misma red de Compose. Alternativas descartadas: embeber Postgres en la imagen de la app (imagen no idiomática, difícil de resetear y de explicar) y usar la base del host (rompe el objetivo de cero dependencias). Dos servicios permiten volumen, healthcheck y reemplazo independiente.

### 2. Dockerfile multi-stage

- Etapa `build`: `eclipse-temurin:17-jdk`, copia el aggregator completo y ejecuta `./mvnw -B -DskipTests package`.
- Etapa `runtime`: `eclipse-temurin:17-jre-alpine`, usuario no root, copia solo `app/target/app-*.jar` y lo ejecuta con `java -jar`.
- Se elige multi-stage sobre Jib/buildpacks por transparencia y porque el repo ya tiene wrapper; se elige JRE 17 (no 21) para respetar `java.version=17`.
- Optimización opcional: cache mount de BuildKit para `~/.m2`; no es requisito para que funcione.

### 3. Credenciales: defaults de desarrollo en Compose + `.env` opcional

`docker-compose.yml` usa `${VAR:-default}` con valores de desarrollo explícitos:

| Variable | Default de desarrollo |
| --- | --- |
| `POSTGRES_DB` | `sportbooking` |
| `POSTGRES_USER` / `DB_USERNAME` | `sportbooking` |
| `POSTGRES_PASSWORD` / `DB_PASSWORD` | `sportbooking` |
| `JWT_SECRET` | valor estático de desarrollo de 46 caracteres |
| `APP_DEV_ADMIN_EMAIL` | `admin@sportbooking.dev` |
| `APP_DEV_ADMIN_PASSWORD` | `admin123` |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` |
| `APP_PORT` | `8081` |
| `SPRING_PROFILES_ACTIVE` | `dev` |

Esto elimina el paso de "crear `.env` antes de arrancar" y, sobre todo, evita el `jwt.secret` vacío que hacía fallar la firma del token. `.env.example` documenta todas las variables con los mismos defaults; `.env` se agrega a `.gitignore` y Compose lo lee automáticamente si existe. Alternativas descartadas: exigir `.env` (fricción y error fácil de olvidar) y Docker secrets (innecesario para desarrollo local).

Los defaults son públicos a propósito y están marcados como **solo desarrollo**; cualquier entorno compartido debe sobreescribirlos. No se copia `application-local.properties` ni `.env` a la imagen.

### 4. Credenciales del admin configurables en el seeder

`DevDataSeeder` recibe `app.dev.admin.email` y `app.dev.admin.password` por `@Value`, con los valores actuales como default en `application.properties`. Así el compose puede sembrar un admin distinto vía `APP_DEV_ADMIN_EMAIL` / `APP_DEV_ADMIN_PASSWORD` sin recompilar. Solo aplica al perfil `dev`; no cambia seguridad ni datos de otros perfiles. Debido a la idempotencia del seed, un cambio de credenciales solo toma efecto sobre una base nueva: se documenta `docker compose down -v` para resetear.

### 5. Orden de arranque y healthchecks

- `db`: healthcheck `pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}` y `depends_on: condition: service_healthy` en la app, para que Flyway nunca arranque contra una base que aún no acepta conexiones.
- `app`: healthcheck con `wget -qO- http://127.0.0.1:8081/v3/api-docs` (busybox de Alpine), que confirma contexto Spring y springdoc listos. Se descarta Actuator para no agregar dependencia ni tocar `SecurityConfiguration`.
- `docker compose ps` muestra ambos estados; el README indica cómo leerlos.

### 6. Persistencia y reseteo

Volumen nombrado `sportbooking_pgdata` montado en `/var/lib/postgresql/data`. `docker compose down` conserva datos; `docker compose down -v` elimina el volumen y el próximo arranque re-aplica Flyway + seed. No se agrega migración nueva: el baseline actual ya crea `platform` y el seed reconstruye los datos.

### 7. Contexto de build y `.dockerignore`

Build desde la raíz del repo (necesita todos los módulos y el wrapper). `.dockerignore` excluye `.git`, `**/target`, `.env`, `application-local.properties`, artefactos de IDE y `openspec/`. El jar se copia con wildcard `app-*.jar` para no acoplar la versión. Usuario no root en runtime.

### 8. README como contrato de onboarding

README en la raíz con: requisitos (Docker Desktop/Engine + Compose v2), `docker compose up --build`, URLs (`http://localhost:8081`, Swagger UI), credenciales de demo, `.env.example`, reseteo, cómo correr los tests (Docker/Testcontainers) y advertencia de que los defaults no son de producción. Se descarta documentar solo en `AGENTS.md` porque el destinatario es una persona, no un agente.

## Risks / Trade-offs

- **Defaults de desarrollo públicos** → se marcan como no producción en compose, `.env.example` y README; el compose no pretende desplegarse fuera del host local.
- **Cambiar `APP_DEV_ADMIN_*` no afecta un volumen ya sembrado** → documentar `down -v`; la idempotencia se mantiene a propósito para no duplicar datos.
- **Primer build lento por descarga de Maven/dependencias** → cache mount opcional de `~/.m2` y aviso en README; builds posteriores son rápidos.
- **Puerto 8081 ocupado en el host** → `APP_PORT` configurable en `.env`.
- **Imagen Alpine/musl** → si alguna dependencia nativa fallara, el fallback es `eclipse-temurin:17-jre-jammy`; no hay dependencias nativas propias hoy.
- **Healthcheck de la app vía `/v3/api-docs`** → depende de springdoc, que ya es dependencia fija; si se quitara, habría que ajustar el check.
- **Compose v2 requerido** para `depends_on.condition` → se declara en requisitos del README.
- **Docker no está corriendo en la máquina actual** → la verificación end-to-end del compose queda a cargo del usuario al aplicar; los tests existentes ya asumen Docker disponible.

## Migration Plan

1. Agregar `Dockerfile`, `docker-compose.yml`, `.dockerignore`, `.env.example`, `README.md` y `.env` en `.gitignore`; parametrizar el seeder.
2. Verificar `.\mvnw.cmd -DskipTests package` y `docker compose up --build`; probar login del admin, Swagger y reinicio con volumen.
3. Rollback: borrar los artefactos nuevos y revertir el seeder; no hay cambios de esquema ni de contrato, y el flujo local con `application-local.properties` sigue funcionando sin tocar nada.

## Open Questions

Ninguna. La elección de imagen base y el mecanismo de healthcheck quedan resueltos en este diseño.
