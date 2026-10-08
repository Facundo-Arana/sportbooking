# Feature Specification: Pruebas automatizadas de login y registro de usuarios

**Feature Branch**: `001-auth-tests`

**Created**: 2026-10-08

**Status**: Draft

**Input**: User description: "Agregar test del login y registro de usuarios, no modificar otros modulos que no sean directamente afectados por el test"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar usuario de forma segura y verificable (Priority: P1)

Como dueño del proyecto, quiero una prueba automática que confirme que el registro de
usuarios acepta datos válidos, rechaza datos inválidos o duplicados y nunca guarda la
contraseña en texto plano, para poder cambiar el registro sin miedo a romperlo.

**Why this priority**: el registro es la puerta de entrada y el punto donde se manejan
datos sensibles (contraseña). Es la funcionalidad con mayor riesgo de seguridad y la
primera que un revisor/entrevistador va a querer ver cubierta.

**Independent Test**: se puede verificar ejecutando únicamente las pruebas de registro:
un alta con datos válidos devuelve éxito; un alta sin email/contraseña, con email
duplicado o con nombre de usuario duplicado es rechazada; la contraseña persistida no
coincide con el texto enviado.

**Acceptance Scenarios**:

1. **Given** datos de registro válidos y no existentes, **When** se registra el usuario, **Then** la respuesta indica éxito y el usuario queda registrado.
2. **Given** un pedido de registro sin email o sin contraseña (o en blanco), **When** se procesa, **Then** se rechaza por datos de entrada inválidos (no por credenciales).
3. **Given** un email ya registrado, **When** se intenta registrar otro usuario con ese email, **Then** se rechaza indicando que el email ya existe.
4. **Given** un nombre de usuario ya en uso, **When** se intenta registrar otro usuario con ese nombre, **Then** se rechaza indicando que el nombre está en uso.
5. **Given** un registro exitoso con contraseña en texto plano, **When** se inspecciona la contraseña almacenada, **Then** esta no coincide con el texto plano original.

---

### User Story 2 - Iniciar sesión con credenciales válidas e inválidas (Priority: P2)

Como dueño del proyecto, quiero una prueba automática que confirme que el login entrega
un token con credenciales válidas y rechaza con un error de autenticación (no un error
interno) cualquier credencial inválida, para garantizar que el flujo de acceso sea
confiable.

**Why this priority**: el login es el otro extremo del flujo de autenticación y su
correcto manejo de errores define la experiencia y la seguridad percibida.

**Independent Test**: se puede verificar ejecutando únicamente las pruebas de login: un
login con credenciales correctas devuelve un token; un login con contraseña incorrecta o
con un usuario inexistente es rechazado con un error de autenticación; ningún fallo de
autenticación produce un error interno del servidor.

**Acceptance Scenarios**:

1. **Given** un usuario registrado, **When** inicia sesión con email y contraseña correctos, **Then** recibe un token de acceso.
2. **Given** un usuario registrado, **When** inicia sesión con contraseña incorrecta, **Then** es rechazado por credenciales inválidas.
3. **Given** credenciales de un usuario que no existe, **When** intenta iniciar sesión, **Then** es rechazado por credenciales inválidas.
4. **Given** cualquier fallo de autenticación, **When** el sistema responde, **Then** la respuesta es un error de autenticación y nunca un error interno del servidor.

---

### User Story 3 - Suite reproducible como puerta de calidad (Priority: P3)

Como dueño del proyecto, quiero ejecutar las pruebas con un solo comando estándar, sin
pasos manuales ni datos preparados a mano, para poder correrlas en cada cambio y en
cualquier máquina.

**Why this priority**: una prueba que requiere preparación manual (Postman, un usuario
sembrado a mano) no sirve como red de seguridad constante; el valor real aparece cuando
es reproducible y desatendida.

**Independent Test**: en un entorno limpio, ejecutar el comando estándar del proyecto
ejecuta las pruebas de login y registro y reporta el resultado sin intervención manual.

**Acceptance Scenarios**:

1. **Given** un checkout limpio del repositorio, **When** se ejecuta el comando estándar de pruebas, **Then** las pruebas de login y registro se ejecutan y reportan resultado sin pasos manuales.
2. **Given** un cambio que rompe el comportamiento de login o registro, **When** se ejecuta el comando estándar de pruebas, **Then** al menos una prueba falla y señaliza la regresión.

---

### Edge Cases

- Registro con email o contraseña presentes pero vacíos o solo con espacios en blanco → rechazado como dato inválido.
- Registro con email válido pero sin nombre de usuario → se verifica la regla de duplicado de nombre de usuario de forma controlada, sin excepción no manejada.
- Email con distinta capitalización que uno existente → se trata como un email distinto (coincidencia exacta), que es el comportamiento actual; la suite lo verifica como tal.
- Login con credenciales válidas cuyo usuario está deshabilitado/bloqueado → se rechaza como error de autenticación, no como error interno.
- Contraseña con caracteres especiales/espacios → se registra y se puede volver a usar en login.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La suite DEBE cubrir un registro exitoso con datos válidos y verificar la confirmación de alta.
- **FR-002**: La suite DEBE cubrir el registro con email y/o contraseña ausentes o en blanco y verificar que se rechaza por datos de entrada inválidos.
- **FR-003**: La suite DEBE cubrir el registro con un email ya existente y verificar el rechazo correspondiente.
- **FR-004**: La suite DEBE cubrir el registro con un nombre de usuario ya en uso y verificar el rechazo correspondiente.
- **FR-005**: La suite DEBE verificar que la contraseña almacenada tras el registro no es el texto plano enviado.
- **FR-006**: La suite DEBE cubrir un login exitoso y verificar que la respuesta incluye un token de acceso utilizable.
- **FR-007**: La suite DEBE cubrir un login con contraseña incorrecta y verificar el rechazo por credenciales inválidas.
- **FR-008**: La suite DEBE cubrir un login con un usuario inexistente y verificar el rechazo por credenciales inválidas.
- **FR-009**: La suite DEBE verificar que todo fallo de autenticación se traduce en un error de autenticación y no en un error interno del servidor.
- **FR-010**: Las pruebas DEBEN ejecutarse con el comando estándar de compilación/pruebas del proyecto, sin pasos manuales ni datos preparados a mano.
- **FR-011**: La incorporación de las pruebas NO DEBE alterar el comportamiento observable de módulos no directamente relacionados con login y registro.
- **FR-012**: Las pruebas DEBEN ser deterministas (mismo resultado en cada ejecución) y no depender de orden entre ellas ni de estado previo.
- **FR-013**: El diseño de las pruebas DEBE respetar los principios vigentes de la constitución (DTOs en el borde, errores de negocio traducidos en un único lugar, seguridad por recurso), verificando que esas reglas se cumplen para login y registro.
- **FR-014**: La suite DEBE combinar pruebas unitarias (reglas de negocio con dependencias simuladas) con al menos una prueba de integración que recorra el flujo HTTP completo de login y registro, cubriendo seguridad, mapeo de errores y persistencia.
- **FR-015**: Las pruebas DEBEN usar una base de datos embebida/en memoria para la prueba de integración, sin depender de la instancia PostgreSQL local de desarrollo.

### Key Entities *(include if feature involves data)*

- **Usuario**: persona que se registra e inicia sesión; atributos relevantes: email (identificador de acceso), nombre de usuario, nombre y contraseña. La contraseña debe persistirse protegida, nunca en texto plano.
- **Credencial de acceso**: par email + contraseña usado para iniciar sesión; determina si se emite o no un token.
- **Token de acceso**: credencial emitida tras un login exitoso, que representa la sesión autenticada del usuario.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los escenarios de login y registro listados en esta especificación se ejecutan y pasan en un checkout limpio.
- **SC-002**: Una regresión en el comportamiento de login o registro (por ejemplo, aceptar un email duplicado o devolver un login exitoso con credenciales incorrectas) hace fallar al menos una prueba.
- **SC-003**: La suite completa de login y registro se ejecuta en menos de 2 minutos en una máquina de desarrollo estándar.
- **SC-004**: Las pruebas se ejecutan sin intervención manual: sin Postman, sin crear usuarios/ADMIN a mano y sin pasos previos de preparación de datos.
- **SC-005**: Se incorporan pruebas para las 5 reglas de registro y los 4 comportamientos de login descritos, cubriendo tanto caminos felices como de error.
- **SC-006**: Ningún módulo no directamente relacionado con login y registro ve alterado su comportamiento observable.
- **SC-007**: La verificación oficial del proyecto pasa a incluir `mvn test` como puerta de calidad obligatoria; la constitución (principio VI) y AGENTS.md se actualizan para dejar de afirmar que no existen tests automatizados.

## Assumptions

- El alcance se limita a login y registro de usuarios; quedan fuera autorización por venue, CRUD de reservas y demás dominios.
- El login usa el email como identificador de acceso (el proyecto ya lo hace así).
- El registro crea un usuario sin rol global asignado.
- No se cambian las firmas ni el contrato observable de los endpoints de login y registro; solo se agregan pruebas.
- Se incorpora un framework de pruebas compatible con el stack actual del proyecto; su elección concreta corresponde a la fase de planificación.
- La prueba de integración usa una base de datos embebida/en memoria, de modo que la suite no requiere la PostgreSQL local ni datos sembrados a mano.
- El email se compara por coincidencia exacta (sin normalizar mayúsculas/minúsculas), tal como funciona hoy.
- `mvn test` pasa a ser la puerta de calidad oficial; la actualización de la constitución (principio VI) y de AGENTS.md forma parte del alcance de esta feature.
- Se permite agregar la dependencia de pruebas y configuración mínima en los módulos directamente afectados (autenticación/usuarios y, si hiciera falta para ejecutar la suite, el módulo de arranque), sin tocar el resto.
- Al introducir tests se debe revisar y actualizar la afirmación "no hay tests" de la constitución y de AGENTS.md como parte de esta feature.
