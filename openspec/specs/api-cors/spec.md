# api-cors Specification

## Purpose

Define the cross-origin contract that lets a browser-based SPA call the API, including the preflight requests required by the HTTP methods the API exposes.

## Requirements

### Requirement: Origenes permitidos configurables

El sistema SHALL aceptar requests cross-origin unicamente desde el o los origenes configurados para el frontend, y SHALL rechazar (sin cabeceras CORS de autorizacion) los requests desde cualquier otro origen.

#### Scenario: Origen del SPA autorizado
- **WHEN** el frontend corre en un origen configurado y hace un request al API
- **THEN** la respuesta incluye las cabeceras CORS que permiten al browser leer el cuerpo

#### Scenario: Origen no autorizado
- **WHEN** un origen no configurado hace un request cross-origin al API
- **THEN** la respuesta no autoriza al browser a leer el cuerpo (sin `Access-Control-Allow-Origin` para ese origen)

### Requirement: Metodos HTTP permitidos completos

El sistema SHALL autorizar en CORS todos los metodos que el API expone, incluyendo `PATCH`, de modo que el preflight de un request que use esos metodos sea aceptado.

#### Scenario: Preflight exitoso para gestion de reserva
- **WHEN** el browser envia un preflight `OPTIONS` para un request `PATCH` a `/reservations/{id}/cancel` (o `/status`, `/payment`) desde el origen autorizado
- **THEN** el sistema responde el preflight autorizando el metodo `PATCH` y los headers requeridos, sin exigir autenticacion

#### Scenario: Request real PATCH permitido
- **WHEN** el browser, tras un preflight exitoso, envia el `PATCH` a un endpoint de gestion desde el origen autorizado
- **THEN** el request llega al endpoint (la autorizacion de negocio se evalua aparte)
