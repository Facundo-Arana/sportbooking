# user-directory Specification

## Purpose

Dar al personal autorizado la capacidad de descubrir los usuarios y el catalogo de roles del sistema, insumo necesario para gestionar membresias de sede (`user_venue_role`).

## Requirements

### Requirement: Listado de usuarios

El sistema SHALL exponer un endpoint autenticado que devuelve el listado de usuarios en su forma de respuesta publica (`id`, `username`, `email`, `name`), sin exponer datos sensibles como el hash de password. El endpoint SHALL aceptar un filtro opcional por texto (username o email).

#### Scenario: Listado completo
- **WHEN** un ADMIN autenticado consulta el listado de usuarios
- **THEN** la respuesta incluye los usuarios con `id`, `username`, `email` y `name`, y sin `password_hash`

#### Scenario: Filtro por texto
- **WHEN** un ADMIN consulta el listado con un filtro que coincide (total o parcialmente) con username o email
- **THEN** la respuesta incluye solo los usuarios que coinciden

#### Scenario: Acceso restringido
- **WHEN** un usuario autenticado sin rol ADMIN intenta consultar el listado de usuarios
- **THEN** el sistema responde 403

### Requirement: Catalogo de roles

El sistema SHALL exponer un endpoint autenticado que devuelve el catalogo de roles disponibles, con su `id` y `name`, para que el frontend pueda ofrecer las opciones al asignar una membresia.

#### Scenario: Listado de roles
- **WHEN** un usuario autenticado consulta el catalogo de roles
- **THEN** la respuesta incluye los roles con `id` y `name`

#### Scenario: Sin autenticacion
- **WHEN** se consulta el catalogo de roles sin token valido
- **THEN** el sistema responde 401
