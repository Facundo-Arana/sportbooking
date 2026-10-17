# Proposal

## Why

Hoy, para usar el backend hace falta tener JDK 17, Maven, PostgreSQL local, crear el schema/datos y configurar secretos en `application-local.properties`. Eso hace que otra persona no pueda clonar el repo y ver la API funcionando sin una guía larga y propensa a errores. Un runtime Docker reproducible elimina esas dependencias del host y reduce el onboarding a uno o dos comandos.

## What Changes

- Agregar un `Dockerfile` multi-stage (build con JDK 17, runtime con JRE 17) que empaqueta el jar ejecutable de `app`.
- Agregar `docker-compose.yml` con dos servicios: PostgreSQL y la aplicación, con healthcheck de base, orden de arranque y volumen persistente.
- Resolver las credenciales sin fricción y sin filtrar secretos:
  - Valores **solo de desarrollo** en compose/`.env.example` para `POSTGRES_*`, `DB_*`, `JWT_SECRET`, credenciales del admin sembrado y CORS.
  - Todos sobreescribibles por variables de entorno o `.env` (no versionado).
  - El `JWT_SECRET` deja de poder quedar vacío: hay un default de desarrollo suficientemente largo para firmar tokens.
  - Las credenciales del admin de `DevDataSeeder` se vuelven configurables con esos mismos defaults, sin recompilar la imagen.
- Agregar `.dockerignore` (target, `.git`, config local, artefactos de IDE).
- Agregar `README.md` con el quickstart: requisitos, comando único, URLs (API/Swagger), credenciales de demo, cómo resetear datos y cómo sobreescribir variables.
- Ignorar `.env` en Git.

No cambia el contrato HTTP, el esquema ni la lógica de negocio. El perfil `dev` sigue siendo el que siembra datos de ejemplo.

## Capabilities

### New Capabilities

- `local-container-runtime`: arranque reproducible del backend y su base con Docker, credenciales configurables con defaults seguros para desarrollo, orden de dependencias, persistencia/reseteo de datos y acceso documentado para una persona nueva.

### Modified Capabilities

<!-- Ninguna: no cambian requisitos de API, CORS, identidad, reservas ni migraciones. -->

## Impact

- **Nuevos artefactos**: `Dockerfile`, `docker-compose.yml`, `.dockerignore`, `.env.example`, `README.md`.
- **Configuración**: `.gitignore` (agregar `.env`); `app/src/main/java/com/example/sportbooking/config/DevDataSeeder.java` (credenciales del admin por propiedades con defaults actuales); propiedades de seed en `app/src/main/resources/application.properties` (defaults documentados).
- **Dependencias**: ninguna nueva de aplicación; se usa Maven Wrapper y las dependencias existentes. Docker/Compose pasan a ser requisito de onboarding.
- **Tests**: sin cambios. Los tests de integración siguen usando Testcontainers y no dependen del compose.
