# Tasks

## 1. Credenciales configurables del seed

- [x] 1.1 Agregar `app.dev.admin.email` y `app.dev.admin.password` a `app/src/main/resources/application.properties` con los defaults actuales (`admin@sportbooking.dev` / `admin123`) y comentario de que son solo desarrollo; verificar con `Select-String -Path app/src/main/resources/application.properties -Pattern 'app.dev.admin'` que ambas claves existen con esos valores.
- [x] 1.2 Inyectar ambas propiedades en `DevDataSeeder` con `@Value` y usarlas al construir el `User` admin en lugar de los literales; verificar que `.\mvnw.cmd -pl app -am compile` termina sin errores y que no quedan los literales `admin@sportbooking.dev` / `admin123` hardcodeados en el seeder (`Select-String` sin coincidencias en líneas de código).
- [x] 1.3 Confirmar que las credenciales del admin siguen saliendo del perfil `dev` y que el seeder conserva su idempotencia; verificar que el constructor solo agrega parámetros anotados y que el chequeo temprano de venues no cambió (lectura del método `run`).

## 2. Artefactos de runtime y documentación

- [x] 2.1 Crear `Dockerfile` multi-stage (JDK 17 para build, JRE 17 Alpine para runtime, usuario no root, `EXPOSE 8081`, `ENTRYPOINT java -jar`); verificar con `docker build -t sportbooking-backend:local .` que la imagen se construye y con `docker image inspect sportbooking-backend:local` que el entrypoint y el puerto son los esperados.
- [x] 2.2 Crear `.dockerignore` excluyendo `.git`, `**/target`, `.env`, `application-local.properties`, artefactos de IDE y `openspec/`; verificar con `docker run --rm sportbooking-backend:local sh -c "test ! -e /app/.env && test ! -e /app/application-local.properties && echo OK"` que esos archivos no están en la imagen.
- [x] 2.3 Crear `docker-compose.yml` con `db` (PostgreSQL, volumen `sportbooking_pgdata`, healthcheck `pg_isready`), `app` (`depends_on` con `condition: service_healthy`, healthcheck a `/v3/api-docs`, puerto `${APP_PORT:-8081}:8081`) y los defaults de desarrollo de `POSTGRES_*`, `DB_*`, `JWT_SECRET`, `SPRING_PROFILES_ACTIVE=dev`, `APP_CORS_ALLOWED_ORIGINS` y `APP_DEV_ADMIN_*`; verificar con `docker compose config --quiet` y `docker compose config` que ambos servicios resuelven las variables con los valores esperados.
- [x] 2.4 Crear `.env.example` con todas las variables documentadas y los mismos defaults del compose; verificar que cada variable usada en `docker-compose.yml` aparece en `.env.example` (comparación de nombres) y que `git check-ignore -v .env` confirma que `.env` está ignorado.
- [x] 2.5 Agregar `.env` a `.gitignore`; verificar con `git check-ignore -v .env` que la regla aplica y con `git status --short` que no se versiona ningún archivo con secretos.
- [x] 2.6 Crear `README.md` con requisitos (Docker + Compose v2), quickstart `docker compose up --build`, URLs (`http://localhost:8081`, `/swagger-ui/index.html`), credenciales de demo, tabla de variables, reseteo con `down -v`, cómo correr tests y advertencia de defaults solo-dev; verificar que los nombres de servicios, variables, URLs y comandos coinciden con `docker-compose.yml` y que los pasos se ejecutan tal cual en la verificación de la sección 3.

## 3. Verificación local end-to-end

- [x] 3.1 Ejecutar el quickstart del README en un entorno limpio (`docker compose up --build -d`); verificar que `docker compose ps` muestra `db` healthy y `app` healthy, que `GET http://localhost:8081/v3/api-docs` responde 200 y que `POST /auth/login` con el admin de demo devuelve token y que `GET /auth/me` con ese token devuelve la identidad.
- [x] 3.2 Verificar el orden de arranque desde cero: bajar el entorno y borrar contenedores, volver a levantar y confirmar en logs/`docker compose ps` que la app no arranca hasta que `db` está healthy y que no hay errores de conexión ni de Flyway.
- [x] 3.3 Verificar persistencia y reseteo: crear un recurso vía API (por ejemplo una reserva o una cancha), reiniciar con `docker compose restart` y confirmar que persiste; luego `docker compose down -v`, levantar de nuevo y confirmar que Flyway re-aplica el baseline y el seed recrea admin/sede/canchas/franjas/calendario/reservas.
- [x] 3.4 Verificar sobreescritura de credenciales: crear un `.env` con `APP_DEV_ADMIN_EMAIL`, `APP_DEV_ADMIN_PASSWORD`, `POSTGRES_PASSWORD` y `JWT_SECRET` distintos, ejecutar `docker compose down -v` y `docker compose up -d`; confirmar que el login funciona con las credenciales nuevas, que las viejas fallan y que la app conecta a Postgres con la password nueva.
- [x] 3.5 Verificar que el CORS sigue operativo en el contenedor: preflight `OPTIONS` desde `http://localhost:5173` a un endpoint que use `PATCH` devuelve las cabeceras CORS permitidas, y desde un origen no configurado no las devuelve.

## 4. Regresión y cierre

- [x] 4.1 Ejecutar `.\mvnw.cmd test` (requiere Docker/Testcontainers) y confirmar que la suite sigue verde; si el daemon no está disponible, registrar el bloqueo y no marcar la tarea como completa.
- [x] 4.2 Agregar la entrada numerada correspondiente en `bitacora-tecnica-sportbooking.md` con síntoma, causa raíz, fix, archivos modificados (rutas completas) y concepto para profundizar; verificar que la lista de archivos coincide con `git diff --name-only` más los archivos nuevos.
- [x] 4.3 Revisar `git status` y confirmar que no se versionan `.env`, `application-local.properties` ni artefactos de build (`target/`), y que solo se agregan los archivos previstos por este cambio.

## Workflow follow-up

- Archivar el cambio cuando la revisión esté aprobada.
- Verificar el resultado archivado y que las specs principales reflejen la nueva capacidad.
