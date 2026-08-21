package com.example.sportbooking.auth.dto;

public class AuthRequest {
	
	private String email;
    private String password;

	// Constructor vacío requerido por Jackson para deserializar el body del
	// POST /auth/login. Sin esto, la deserialización depende de que Spring
	// Boot compile con el flag "-parameters" (lo hace por defecto, pero es
	// frágil dejarlo implícito).
	public AuthRequest() {
	}

	public AuthRequest(String email, String password) {
		super();
		this.email = email;
		this.password = password;
	}
	
	public String getEmail() {
		return email;
	}
	
	public void setEmail(String email) {
		this.email = email;
	}
	
	public String getPassword() {
		return password;
	}
	
	public void setPassword(String password) {
		this.password = password;
	}

    
}
