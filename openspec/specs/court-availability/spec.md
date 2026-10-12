# court-availability Specification

## Purpose

Definir que franjas de una cancha son reservables (a partir de su calendario) y calcular, para una fecha dada, cuales estan libres y cuales ocupadas, para que el frontend reciba la disponibilidad ya resuelta.

## Requirements

### Requirement: Franja reservable segun calendario

Una franja horaria SHALL considerarse reservable para una cancha en una fecha dada si y solo si existe un `court_schedule` de esa cancha, con `is_enabled` verdadero, cuyo `day_of_week` coincide con el dia de la semana de la fecha y cuyo `time_block` es la franja. Una franja fuera del calendario NO SHALL ser reservable.

#### Scenario: Franja habilitada del dia
- **WHEN** la cancha tiene un `court_schedule` habilitado que cubre la franja el dia de la semana de la fecha consultada
- **THEN** esa franja es reservable en esa fecha

#### Scenario: Franja deshabilitada
- **WHEN** el `court_schedule` de esa cancha y franja tiene `is_enabled` falso
- **THEN** esa franja NO es reservable

#### Scenario: Dia sin calendario
- **WHEN** no existe ningun `court_schedule` de la cancha para el dia de la semana de la fecha consultada
- **THEN** ninguna franja es reservable ese dia

### Requirement: Calculo de disponibilidad por cancha y fecha

El sistema SHALL exponer un endpoint autenticado que, para una cancha y una fecha, devuelve el listado de franjas reservables de ese dia indicando para cada una si esta `free` u `occupied`.

#### Scenario: Franja libre y franja ocupada
- **WHEN** se consulta la disponibilidad de una cancha y fecha con al menos una franja reservable
- **THEN** la respuesta lista cada franja reservable del dia con su estado, marcando `occupied` las que tienen una reserva activa y `free` las que no

#### Scenario: Una reserva cancelada libera la franja
- **WHEN** una franja tiene una reserva cuyo `status` es `CANCELLED` y ninguna otra reserva activa
- **THEN** esa franja aparece como `free`

#### Scenario: Cancha inexistente
- **WHEN** se consulta la disponibilidad de una cancha que no existe
- **THEN** el sistema responde 404

### Requirement: Orden estable de las franjas

El listado de disponibilidad SHALL devolver las franjas ordenadas de forma determinista por su hora de inicio, para que el frontend pueda renderizar el calendario sin reordenar.

#### Scenario: Orden por hora de inicio
- **WHEN** se consulta la disponibilidad de una cancha y fecha con varias franjas reservables
- **THEN** la respuesta las presenta ordenadas ascendentemente por el `start_time` del `time_block`
