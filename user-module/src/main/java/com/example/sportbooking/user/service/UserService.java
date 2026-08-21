package com.example.sportbooking.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.sportbooking.user.dto.UserDTO;
import com.example.sportbooking.user.entity.User;
import com.example.sportbooking.user.repository.UserRepository;

@Service
public class UserService {
	
	private UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
	

	public User registerUser(UserDTO user) {

		if (userRepository.findByEmail(user.getEmail()).isPresent()) {
			throw new RuntimeException("Ya existe un usuario registrado con ese email");
		}

		if (userRepository.findByUsername(user.getUsername()).isPresent()) {
			throw new RuntimeException("Ese nombre de usuario ya está en uso");
		}

		// encriptar contraseña
		String encryptedPassword = passwordEncoder.encode(user.getPassword());

		User u = new User(user.getUsername(), user.getEmail(), encryptedPassword, user.getName());

		// Antes este save() estaba en un try/catch que solo hacía printStackTrace():
		// si guardar fallaba, el método igual devolvía "u" como si se hubiera
		// persistido, y el controller respondía 200 OK con un usuario que nunca
		// llegó a la base. Ahora la excepción se propaga y la maneja el controller.
		userRepository.save(u);

		return u;
	}

}
