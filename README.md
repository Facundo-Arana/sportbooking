# Sportbooking — backend

API REST para gestionar reservas de canchas (Spring Boot 3.4.7 + Java 17 + PostgreSQL).
Este documento explica cómo levantar el backend y su base de datos con Docker, sin instalar
Java, Maven ni PostgreSQL en el host.

## Requisitos

- Docker Desktop o Docker Engine.
- Docker Compose v2 (`docker compose version`).
- Para correr los tests de integración, el daemon de Docker debe estar activo (usan Testcontainers).

No hace falta crear ningún archivo para empezar: el compose trae defaults de desarrollo.
El primer build descarga Maven y las dependencias, así que puede tardar varios minutos.

## Quickstart

Desde la raíz del repositorio:

```bash
docker compose up --build
```

Cuando ambos servicios estén `healthy`, la API queda disponible en:

- API: <http://localhost:8081>
- Swagger UI: <http://localhost:8081/swagger-ui/index.html>
- OpenAPI JSON: <http://localhost:8081/v3/api-docs>

Para correrlo en segundo plano y ver el estado:

```bash
docker compose up --build -d
docker compose ps
```

El servicio `db` debe aparecer `healthy` y `app` también. Si algo falla:

```bash
docker compose logs -f app
docker compose logs -f db
```

### Credenciales de demo

El perfil `dev` siembra un usuario administrador y datos de ejemplo:

| Campo | Valor |
| --- | --- |
| Email | `admin@sportbooking.dev` |
| Password | `admin123` |

Ejemplo de login:

```bash
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@sportbooking.dev","password":"admin123"}'
```

La respuesta incluye `token`; con él se llama `GET /auth/me`:

```bash
curl http://localhost:8081/auth/me -H "Authorization: Bearer <token>"
```

## Variables de entorno

Todas son opcionales: si no existe `.env` ni variables en el entorno, se usan los defaults
de desarrollo de `docker-compose.yml`. Para sobreescribirlas, copiá `.env.example` a `.env`
(archivo no versionado) y editá los valores.

| Variable | Default de desarrollo | Uso |
| --- | --- | --- |
| `POSTGRES_DB` | `sportbooking` | Nombre de la base creada por Postgres |
| `POSTGRES_USER` | `sportbooking` | Usuario de Postgres |
| `POSTGRES_PASSWORD` | `sportbooking` | Password de Postgres |
| `DB_USERNAME` | `sportbooking` | Usuario que usa la aplicación (debe coincidir con `POSTGRES_USER`) |
| `DB_PASSWORD` | `sportbooking` | Password que usa la aplicación (debe coincidir con `POSTGRES_PASSWORD`) |
| `JWT_SECRET` | valor de desarrollo de 46 caracteres | Firma de los JWT (mínimo 32 caracteres) |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` habilita el seed de datos de ejemplo |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes permitidos por CORS |
| `APP_DEV_ADMIN_EMAIL` | `admin@sportbooking.dev` | Email del admin sembrado por `dev` |
| `APP_DEV_ADMIN_PASSWORD` | `admin123` | Password del admin sembrado por `dev` |
| `APP_PORT` | `8081` | Puerto publicado en el host |

> Los defaults son públicos a propósito y **solo sirven para desarrollo**. No uses este
> compose ni estas credenciales en un entorno compartido o productivo.

### Cambiar las credenciales del admin

El seed es idempotente: si el volumen ya tiene datos, no vuelve a sembrar. Por eso, para que
las credenciales nuevas tomen efecto hay que recrear la base (ver [Resetear datos](#resetear-datos)).

## Datos: persistencia y reseteo

PostgreSQL guarda sus datos en el volumen nombrado `sportbooking_pgdata`.

- `docker compose down` detiene y elimina los contenedores, pero **conserva** los datos.
- `docker compose restart` reinicia los servicios sin perder datos.

### Resetear datos

```bash
docker compose down -v
docker compose up --build
```

`down -v` elimina el volumen. En el siguiente arranque Flyway vuelve a aplicar el baseline
y el perfil `dev` recrea el admin, la sede, las canchas, las franjas, el calendario y las
reservas de ejemplo.

## Tests

```bash
# Windows (PowerShell)
.\mvnw.cmd test

# Linux / macOS
./mvnw test
```

- Los tests **unit** y de **slice** no necesitan Docker.
- Los tests de **integración** usan Testcontainers y levantan un PostgreSQL efímero:
  requieren el daemon de Docker corriendo. No tocan la base del compose.

## Desarrollo sin Docker

El flujo local sigue funcionando: JDK 17 + Maven Wrapper + un PostgreSQL accesible. Los
secretos locales van en `application-local.properties` (no versionado, en la raíz):

```properties
DB_URL=jdbc:postgresql://localhost:5432/postgres
DB_USERNAME=postgres
DB_PASSWORD=tu_password
JWT_SECRET=un_secreto_de_al_menos_32_caracteres
```

Luego:

```bash
.\mvnw.cmd -pl app -am spring-boot:run -Dspring-boot.run.profiles=dev
```

## Estructura del entorno Docker

- `Dockerfile`: build multi-stage. La etapa `build` compila el aggregator con JDK 17 y el
  Maven Wrapper; la etapa `runtime` usa JRE 17 Alpine, corre con usuario no root y expone 8081.
- `docker-compose.yml`: `db` (PostgreSQL con healthcheck) y `app` (espera a que `db` esté
  healthy antes de arrancar y expone su propio healthcheck en `/v3/api-docs`).
- `.dockerignore`: deja fuera del contexto de build `.git`, `target/`, `.env`,
  `application-local.properties`, artefactos de IDE y `openspec/`.
