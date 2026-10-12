# Spec Delta

## Purpose

Definir el alta de reserva cuando la dispara un cliente autenticado: la propiedad de la reserva se deriva del token y la franja elegida debe ser una franja reservable y libre de la cancha.

## ADDED Requirements

### Requirement: Propiedad de la reserva derivada del token

El `reserved_by_user` de una reserva SHALL ser el usuario autenticado que realiza el alta. El sistema NO SHALL aceptar la propiedad desde el cuerpo del request, y SHALL rechazar todo intento de crear una reserva a nombre de otro usuario.

#### Scenario: Alta atribuye la reserva al autenticado
- **WHEN** un usuario autenticado crea una reserva con una franja reservable y libre
- **THEN** la reserva queda asociada a ese usuario como `reservedByUser`, aunque no haya enviado ese dato en el body

#### Scenario: Body intenta fijar otro propietario
- **WHEN** un usuario autenticado envia en el body un `reserved_by_user_id` distinto de su propio `id`
- **THEN** el sistema ignora o rechaza ese valor y no crea la reserva a nombre del otro usuario

#### Scenario: Reserva para tercero por nombre
- **WHEN** el usuario autenticado quiere reservar para alguien no registrado
- **THEN** usa `reserved_for_name` (o `reserved_for_user_id` para un registrado), manteniendo `reservedByUser` como el autenticado

### Requirement: Alta validada contra el calendario de la cancha

El sistema SHALL rechazar el alta de una reserva cuya franja no sea una franja reservable de la cancha en la fecha indicada, y SHALL rechazar con conflicto el alta sobre una franja ya ocupada por una reserva activa.

#### Scenario: Franja fuera del calendario
- **WHEN** se intenta crear una reserva en una franja que no esta en el calendario habilitado de la cancha para el dia de la fecha
- **THEN** el sistema responde 400 y no crea la reserva

#### Scenario: Franja ya ocupada
- **WHEN** se intenta crear una reserva en una franja reservable que ya tiene una reserva activa (no `CANCELLED`) para esa cancha y fecha
- **THEN** el sistema responde 409 y no crea la reserva

#### Scenario: Reprogramacion a franja invalida
- **WHEN** se reprograma una reserva existente hacia una franja fuera del calendario o ya ocupada
- **THEN** el sistema la rechaza (400 fuera del calendario, 409 ocupada) y mantiene la reserva sin cambios
