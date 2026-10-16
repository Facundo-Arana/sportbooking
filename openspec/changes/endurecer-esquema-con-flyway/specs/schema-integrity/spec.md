# Spec Delta

## Purpose

Garantizar a nivel de base las invariantes que hasta ahora solo validaba la capa de servicio, de modo que un dato invalido no pueda persistirse aunque la validacion de aplicacion se omita o falle bajo concurrencia.

## ADDED Requirements

### Requirement: Columnas obligatorias no nulas

Las claves foraneas y las columnas de negocio obligatorias SHALL ser `NOT NULL`. La base SHALL rechazar la persistencia de un registro que deje nula una de esas columnas.

#### Scenario: Alta sin clave foranea obligatoria

- **WHEN** se intenta persistir un registro cuya relacion obligatoria viene nula (por ejemplo una cancha sin sede)
- **THEN** la base rechaza el `INSERT` y el registro no se persiste

#### Scenario: Alta con columna de negocio obligatoria nula

- **WHEN** se intenta persistir un registro con una columna obligatoria nula (por ejemplo una reserva sin `date` o sin `status`)
- **THEN** la base rechaza el `INSERT` y el registro no se persiste

### Requirement: Dominios controlados por CHECK

Las columnas con un conjunto cerrado de valores validos SHALL estar restringidas con `CHECK`: `status` y `payment_status` de reserva, `day_of_week` en el rango 0 a 6, y la relacion `start_time < end_time` de una franja. La base SHALL rechazar valores fuera de dominio.

#### Scenario: Estado de reserva invalido

- **WHEN** se intenta persistir una reserva con un `status` o `payment_status` fuera del conjunto permitido
- **THEN** la base rechaza el `INSERT` y el registro no se persiste

#### Scenario: Dia de la semana fuera de rango

- **WHEN** se intenta persistir un `court_schedule` con `day_of_week` fuera del rango 0 a 6
- **THEN** la base rechaza el `INSERT` y el registro no se persiste

#### Scenario: Rango horario invertido

- **WHEN** se intenta persistir una franja cuyo `start_time` no es anterior a su `end_time`
- **THEN** la base rechaza el `INSERT` y el registro no se persiste

### Requirement: Sin solapamiento de reservas activas

Dos reservas activas SHALL NOT ocupar la misma cancha y la misma franja en la misma fecha. La garantia SHALL sostenerse a nivel de base, incluso si dos altas concurrentes superan la validacion de aplicacion.

#### Scenario: Alta concurrente sobre la misma franja

- **WHEN** dos altas concurrentes sobre la misma cancha, fecha y franja activa llegan a la base
- **THEN** a lo sumo una de las dos reservas se persiste y la otra es rechazada

#### Scenario: Liberar y volver a reservar

- **WHEN** una reserva de esa franja pasa a estado cancelado y luego se crea una nueva reserva para esa cancha, fecha y franja
- **THEN** la nueva reserva se persiste porque ya no hay solapamiento de reservas activas
