package com.example.sportbooking.user.repository;


import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.sportbooking.user.entity.User;


public interface UserRepository extends JpaRepository<User, Long>{
	
	Optional<User> findByUsername(String username);
	Optional<User> findByEmail(String email);

	// Trae al User junto con sus globalRoles (y el Role de cada uno) en UNA
	// sola consulta con JOIN FETCH. Necesario para usar en autenticación:
	// getAuthorities() se llama desde el JwtFilter, antes de que Open
	// Session In View abra la sesión de Hibernate (eso pasa recién al
	// llegar al DispatcherServlet). Sin este fetch explícito, acceder a
	// globalRoles ahí tira LazyInitializationException: "no Session".
	@Query("SELECT u FROM User u " +
	       "LEFT JOIN FETCH u.globalRoles gr " +
	       "LEFT JOIN FETCH gr.role " +
	       "WHERE u.email = :email")
	Optional<User> findByEmailWithRoles(@Param("email") String email);
}
