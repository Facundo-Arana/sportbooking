# user-identity Specification

## Purpose

Permitir que un cliente autenticado conozca su propia identidad (perfil y roles) a partir del token, sin depender de buscar su usuario por otro medio.

## Requirements

### Requirement: Perfil del usuario autenticado

El sistema SHALL exponer un endpoint autenticado que devuelve la identidad completa del usuario asociado al token: `id`, `username`, `email`, `name` y sus authorities (roles globales).

#### Scenario: Consulta del propio perfil
- **WHEN** un usuario autenticado consulta su perfil con un `Authorization: Bearer <token>` valido
- **THEN** la respuesta incluye `id`, `username`, `email`, `name` y la lista de authorities del usuario

#### Scenario: Token ausente o invalido
- **WHEN** se consulta el perfil sin token o con uno invalido/expirado
- **THEN** el sistema responde 401 y no devuelve datos del usuario

### Requirement: Identidad en la respuesta de login

La respuesta del login SHALL incluir, ademas del token, la identidad del usuario autenticado (`id`, `username`, `email`, `name` y authorities), para que el frontend pueda resolver el usuario en un solo request.

#### Scenario: Login exitoso devuelve token y usuario
- **WHEN** un usuario se autentica con credenciales validas
- **THEN** la respuesta incluye el token y los datos de identidad del usuario (incluido su `id`)

#### Scenario: Login fallido
- **WHEN** un usuario se autentica con credenciales invalidas
- **THEN** el sistema responde 401 sin token ni datos de usuario
