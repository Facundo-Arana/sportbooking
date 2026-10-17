# local-container-runtime Specification

## Purpose

Permitir que una persona nueva clone el repositorio y ejecute el backend con su base de datos mediante Docker, sin instalar Java, Maven ni PostgreSQL en el host y sin editar archivos para empezar.

## Requirements

### Requirement: Arranque reproducible en un comando

El sistema SHALL levantarse junto con su base de datos mediante un único comando de Docker Compose, aplicando las migraciones y los datos de ejemplo del perfil de desarrollo sin pasos manuales.

#### Scenario: Primera ejecución desde un clon limpio

- **WHEN** una persona con Docker y Compose instalados ejecuta el comando de arranque documentado en un clon limpio
- **THEN** la base y la aplicación quedan operativas y la API responde en `http://localhost:8081`

#### Scenario: Datos de ejemplo disponibles

- **WHEN** la aplicación arranca por primera vez con la base vacía
- **THEN** Flyway aplica las migraciones y el seed de desarrollo crea el usuario administrador, la sede, las canchas, las franjas, el calendario y las reservas de ejemplo

#### Scenario: Reejecución sin duplicar datos

- **WHEN** se vuelve a levantar el entorno sobre el mismo volumen de datos
- **THEN** la aplicación arranca sin duplicar el seed ni volver a aplicar migraciones ya registradas

### Requirement: Credenciales configurables con defaults de desarrollo

El sistema SHALL resolver las credenciales de base de datos, el secreto de firma JWT y las credenciales del administrador sembrado desde variables de entorno o un archivo `.env`, y SHALL proveer valores por defecto utilizables solo en desarrollo. Ninguna credencial SHALL quedar embebida en la imagen.

#### Scenario: Arranque sin configuración previa

- **WHEN** se levanta el entorno sin crear `.env` ni definir variables
- **THEN** la aplicación arranca con los valores de desarrollo documentados y el login del administrador funciona

#### Scenario: Secreto JWT nunca vacío

- **WHEN** el entorno arranca sin `JWT_SECRET` definido
- **THEN** se usa el valor de desarrollo de longitud suficiente y la emisión de tokens no falla por secreto vacío

#### Scenario: Sobreescritura por entorno

- **WHEN** se define una variable o un `.env` con otros valores antes de levantar el entorno
- **THEN** la base y la aplicación usan esos valores, y el administrador sembrado en una base nueva usa las credenciales configuradas

#### Scenario: La imagen no contiene secretos locales

- **WHEN** se inspecciona la imagen construida
- **THEN** no incluye `application-local.properties`, `.env` ni credenciales fuera de los defaults de desarrollo

### Requirement: Orden de arranque y disponibilidad

El sistema SHALL iniciar la aplicación solo cuando la base de datos esté lista para aceptar conexiones, y SHALL exponer un estado verificable para diagnosticar el arranque.

#### Scenario: Base lista antes que la aplicación

- **WHEN** se levanta el entorno desde cero
- **THEN** la aplicación espera a que la base supere su healthcheck antes de arrancar y no falla por conexión rechazada

#### Scenario: Estado observable del entorno

- **WHEN** una persona consulta el estado de los servicios
- **THEN** puede distinguir si la base está sana, si la aplicación terminó de arrancar y qué servicio falló en caso contrario

### Requirement: Persistencia y reseteo de datos

El sistema SHALL persistir los datos de PostgreSQL en un volumen nombrado y SHALL permitir recrear el entorno desde cero eliminando ese volumen.

#### Scenario: Datos persistentes entre reinicios

- **WHEN** se detiene y vuelve a levantar el entorno sin eliminar el volumen
- **THEN** los datos creados previamente siguen disponibles

#### Scenario: Reseteo completo

- **WHEN** se elimina el volumen y se vuelve a levantar el entorno
- **THEN** Flyway recrea el esquema desde el baseline y el seed vuelve a crear los datos de ejemplo

### Requirement: Onboarding documentado

El repositorio SHALL incluir una guía con los requisitos, el comando de arranque, las URLs de acceso, las credenciales de demo, cómo sobreescribir variables y cómo resetear los datos.

#### Scenario: Persona nueva sigue la guía

- **WHEN** una persona nueva lee la guía del repositorio y ejecuta los pasos indicados
- **THEN** puede acceder a la API y a Swagger, autenticarse con el administrador de demo y entender cómo cambiar credenciales o resetear datos sin leer el código
