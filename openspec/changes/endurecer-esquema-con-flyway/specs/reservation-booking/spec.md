# Spec Delta

## MODIFIED Requirements

### Requirement: Alta validada contra el calendario de la cancha

El sistema SHALL rechazar el alta de una reserva cuya franja no sea una franja reservable de la cancha en la fecha indicada, y SHALL rechazar con conflicto el alta sobre una franja ya ocupada por una reserva activa. La garantia de no solapamiento de reservas activas SHALL sostenerse a nivel de base, por lo que vale tambien ante altas concurrentes y no solo en el camino feliz de la validacion de aplicacion.

#### Scenario: Franja fuera del calendario

- **WHEN** se intenta crear una reserva en una franja que no esta en el calendario habilitado de la cancha para el dia de la fecha
- **THEN** el sistema responde 400 y no crea la reserva

#### Scenario: Franja ya ocupada

- **WHEN** se intenta crear una reserva en una franja reservable que ya tiene una reserva activa (no `CANCELLED`) para esa cancha y fecha
- **THEN** el sistema responde 409 y no crea la reserva

#### Scenario: Alta concurrente sobre la misma franja

- **WHEN** dos altas sobre la misma cancha, fecha y franja reservable se procesan en simultaneo
- **THEN** a lo sumo una reserva queda creada y la otra se rechaza con 409, sin doble reserva persistida

#### Scenario: Reprogramacion a franja invalida

- **WHEN** se reprograma una reserva existente hacia una franja fuera del calendario o ya ocupada
- **THEN** el sistema la rechaza (400 fuera del calendario, 409 ocupada) y mantiene la reserva sin cambios
