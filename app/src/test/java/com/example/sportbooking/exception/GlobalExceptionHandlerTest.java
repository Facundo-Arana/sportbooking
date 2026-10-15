package com.example.sportbooking.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import com.example.sportbooking.venue.exception.VenueNotFoundException;

/**
 * Unit test del {@link GlobalExceptionHandler}: verifica el mapeo
 * excepcion -> HTTP que el resto de la app asume (y que los slices no pueden
 * cubrir del todo sin pasar por un controller real).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void illegalArgumentMapsTo400() {
        ResponseEntity<java.util.Map<String, String>> response = handler.handleBadRequest(new IllegalArgumentException("dato invalido"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "dato invalido");
    }

    @Test
    void notFoundMapsTo404() {
        ResponseEntity<java.util.Map<String, String>> response = handler.handleNotFound(new VenueNotFoundException(7L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsKey("error");
    }

    @Test
    void illegalStateMapsTo409() {
        ResponseEntity<java.util.Map<String, String>> response = handler.handleConflict(new IllegalStateException("turno ocupado"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "turno ocupado");
    }

    @Test
    void dataIntegrityViolationMapsTo409() {
        ResponseEntity<java.util.Map<String, String>> response =
                handler.handleDataIntegrityViolation(new DataIntegrityViolationException("unique"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void accessDeniedMapsTo403() {
        ResponseEntity<java.util.Map<String, String>> response = handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void authenticationExceptionMapsTo401() {
        ResponseEntity<java.util.Map<String, String>> response = handler.handleAuthentication(new BadCredentialsException("bad"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unexpectedMapsTo500() {
        ResponseEntity<java.util.Map<String, String>> response = handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
