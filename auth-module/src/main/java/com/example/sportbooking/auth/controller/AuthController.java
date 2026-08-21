package com.example.sportbooking.auth.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.sportbooking.auth.dto.AuthRequest;
import com.example.sportbooking.auth.dto.AuthResponse;
import com.example.sportbooking.auth.security.util.CustomUserDetailsService;
import com.example.sportbooking.auth.security.util.JwtUtil;
import com.example.sportbooking.user.dto.UserDTO;
import com.example.sportbooking.user.entity.User;
import com.example.sportbooking.user.service.UserService;



@RestController
@RequestMapping("/auth")
public class AuthController {
	
	private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final UserService userService;
   

    public AuthController(AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          CustomUserDetailsService userDetailsService,
                          UserService userService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.userService = userService;
    }
	

	@PostMapping("/register")
	public ResponseEntity<?> register(@RequestBody UserDTO user) {
		
		  if (!this.isValidFields(user)) {
		        // 400, no 401: esto es un error de validación de datos de entrada,
		        // no un fallo de autenticación (401 es "no estás autenticado").
		        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
		                .body(Map.of("error", "email y password son obligatorios"));
		    }

		    try {
		        User newUser = userService.registerUser(user);

		        return ResponseEntity.ok(Map.of(
		                "message", "Usuario registrado con éxito",
		                "username", newUser.getUsername()
		        ));
		    } catch (RuntimeException e) {
		        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		    }
		
	}
	
	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody AuthRequest authRequest) {
		
	
		try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(authRequest.getEmail(), authRequest.getPassword())
            );
        } catch (AuthenticationException e) {
            // Antes solo se capturaba BadCredentialsException. Cualquier otra
            // subclase de AuthenticationException (DisabledException,
            // LockedException, InternalAuthenticationServiceException, etc.)
            // se colaba sin manejar y terminaba en un 500 genérico.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales inválidas"));
        }

		// Ignorar que la funcion se llama findByUsername!!!
		// Se usa el EMAIL como identificador!!!!!
        UserDetails userDetails = userDetailsService.loadUserByUsername(authRequest.getEmail());
        
       
        
        String token = jwtUtil.generateToken(userDetails.getUsername());

        return ResponseEntity.ok(new AuthResponse(token));
	}

	// Endpoint protegido: requiere "Authorization: Bearer <token>" válido.
	// Sirve para probar el flujo de auth completo de punta a punta: si esto
	// responde 200 con tus datos, el JwtFilter validó el token correctamente
	// y cargó el usuario en el SecurityContext.
	@GetMapping("/me")
	public ResponseEntity<?> me() {
		UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext()
				.getAuthentication()
				.getPrincipal();

		return ResponseEntity.ok(Map.of(
				"email", userDetails.getUsername(),
				"authorities", userDetails.getAuthorities()
		));
	}


	/*
	 * Verificar existencia de datos clave del usuario
	 * 
	 * Email y password
	 * 
	 * */
	private boolean isValidFields(UserDTO userDTO) {
		// Antes esto tiraba NullPointerException si "email" o "password"
		// llegaban ausentes en el JSON (userDTO.getEmail() == null), en vez
		// de devolver una respuesta de validación prolija.
		return userDTO.getEmail() != null && !userDTO.getEmail().isBlank()
				&& userDTO.getPassword() != null && !userDTO.getPassword().isBlank();
	}


}
