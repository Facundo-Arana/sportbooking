package com.example.sportbooking.exception;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.sportbooking.court.exception.CourtNotFoundException;
import com.example.sportbooking.courtschedule.exception.CourtScheduleNotFoundException;
import com.example.sportbooking.reservation.exception.ReservationNotFoundException;
import com.example.sportbooking.timeblock.exception.TimeBlockNotFoundException;
import com.example.sportbooking.user.exception.RoleNotFoundException;
import com.example.sportbooking.user.exception.UserNotFoundException;
import com.example.sportbooking.uservenuerole.exception.UserVenueRoleNotFoundException;
import com.example.sportbooking.venue.exception.VenueNotFoundException;

// Vive en app-module (y no en un módulo "común") porque es el único módulo
// que ya depende de los siete módulos de dominio en su pom.xml -- necesita
// ver las seis *NotFoundException para poder capturarlas. Crear un módulo
// compartido solo para esto sería sobre-ingeniería en un monolito modular
// de este tamaño (ver bitácora punto 20).
//
// @RestControllerAdvice = @ControllerAdvice + @ResponseBody: cada método de
// acá abajo se aplica a las excepciones no capturadas de TODOS los
// @RestController del proyecto, y el valor de retorno se serializa directo
// a JSON (antes cada controller hacía esto mismo, repetido, con
// ResponseEntity.status(...).body(Map.of("error", ...))).
@RestControllerAdvice
public class GlobalExceptionHandler {

	// 404: recurso inexistente. Antes cada controller (Venue/Court/TimeBlock/
	// CourtSchedule/Reservation) tenía su propio catch para esto, repetido
	// módulo por módulo. Estas ocho excepciones no comparten una clase base
	// (cada módulo define la suya, RuntimeException a secas) a propósito: no
	// se creó una ResourceNotFoundException común porque eso exigiría un
	// módulo compartido nuevo solo para una clase -- ver nota de arriba.
	@ExceptionHandler({ VenueNotFoundException.class, CourtNotFoundException.class, TimeBlockNotFoundException.class,
			CourtScheduleNotFoundException.class, ReservationNotFoundException.class, UserNotFoundException.class,
			RoleNotFoundException.class, UserVenueRoleNotFoundException.class })
	public ResponseEntity<Map<String, String>> handleNotFound(RuntimeException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
	}

	// 400: dato de entrada inválido. Cubre las validaciones de negocio de los
	// services (end_time <= start_time, day_of_week fuera de rango, email/
	// username duplicado en el registro -- ver bitácora punto 20).
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
	}

	// 409: el request es válido, pero choca contra el estado actual del
	// sistema (turno ya reservado). Antes solo lo capturaba
	// ReservationController -- ver bitácora punto 3.1 sobre la distinción
	// conceptual 400 vs 401, extendida acá a 400 vs 409.
	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
	}

	// 409: violación de un constraint real de la base (UNIQUE/NOT NULL/FK).
	// Con el punto 19 (UNIQUE real en email/username) esto deja de ser
	// hipotético: dos registros simultáneos pueden pasar ambos el
	// findByEmail/findByUsername de UserService (check-then-act) y uno solo
	// gana la carrera en el INSERT. Sin este handler, ese caso puntual
	// terminaba en 500 genérico en vez de un 409 prolijo.
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("error", "El dato ya existe o viola una restricción de la base"));
	}

	// 403: autorizacion denegada por @PreAuthorize (metodo). Sin este handler,
	// la AccessDeniedException que lanza Spring Security al evaluar el
	// @PreAuthorize DENTRO del metodo del controller cae en el
	// handleUnexpected(Exception) de abajo y devuelve 500, cuando la spec
	// (user-directory, etc.) espera 403. El 401 de "sin token" si lo resuelve
	// el AuthenticationEntryPoint a nivel de filtro, antes de llegar al MVC.
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No tenés permisos para esta operación"));
	}

	// 401: red de seguridad genérica. AuthController.login() ya captura
	// AuthenticationException puntualmente (necesita cortar el flujo ahí
	// mismo antes de generar el token), así que en la práctica este handler
	// no debería dispararse desde /auth/login -- queda como fallback por si
	// algún otro punto de entrada delega en Spring Security más adelante.
	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<Map<String, String>> handleAuthentication(AuthenticationException e) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Credenciales inválidas"));
	}

	// 500: última red de contención. Sin esto, cualquier excepción no
	// prevista (ej. un NullPointerException real) devuelve el stacktrace
	// crudo de Spring Boot al cliente -- acá se corta y se responde algo
	// genérico sin filtrar detalles internos.
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Ocurrió un error inesperado"));
	}

}
