# Spec Delta

## Purpose

Definir el esquema de la base como una secuencia de migraciones versionadas e inmutables, de modo que el esquema evolucione de forma reproducible y auditable en vez de que la aplicacion lo altere segun el modelo de entidades.

## ADDED Requirements

### Requirement: Esquema gobernado por migraciones versionadas

El esquema SHALL definirse exclusivamente mediante migraciones versionadas aplicadas en orden. La aplicacion NO SHALL modificar el esquema en runtime; al arrancar SHALL validar que el esquema coincide con el modelo y SHALL fallar si no coincide.

#### Scenario: Arranque con esquema al dia

- **WHEN** la aplicacion arranca contra una base cuyo esquema ya refleja todas las migraciones
- **THEN** arranca sin alterar ninguna tabla ni constraint y registra cada migracion como aplicada

#### Scenario: Migraciones pendientes

- **WHEN** la aplicacion arranca contra una base a la que le faltan migraciones
- **THEN** aplica las migraciones pendientes en orden antes de quedar operativa

#### Scenario: Esquema desalineado impide el arranque

- **WHEN** el modelo de entidades no coincide con el esquema resultante de las migraciones
- **THEN** la aplicacion falla al arrancar en vez de ajustar la base en silencio

#### Scenario: Migracion aplicada es inmutable

- **WHEN** se modifica el contenido de una migracion ya aplicada
- **THEN** la aplicacion detecta la discrepancia y falla al arrancar

### Requirement: Baseline unico y autocontenido

El esquema SHALL poder recrearse desde cero a partir del baseline, sin depender del estado historico de una base ya construida. El baseline SHALL declarar tablas, claves foraneas y constraints de forma autocontenida y sin artefactos huerfanos.

#### Scenario: Recrear el esquema en una base vacia

- **WHEN** se levanta la base desde cero contra una instancia vacia
- **THEN** el esquema resultante contiene todas las tablas, FKs y constraints que la aplicacion espera

#### Scenario: Sin artefactos huerfanos

- **WHEN** se inspecciona el esquema creado por el baseline
- **THEN** no hay secuencias huerfanas ni dos mecanismos distintos de generacion de identificadores para una misma tabla
