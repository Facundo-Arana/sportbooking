package com.example.sportbooking.auth.security.util;

import java.util.Collection;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.sportbooking.user.entity.User;
import com.example.sportbooking.user.entity.UserGlobalRole;

@SuppressWarnings("serial")
public class CustomUserDetails implements UserDetails {

	private final User user;

	public CustomUserDetails(User user) {
		this.user = user;
	}

	// Se usa el Email como subject
	@Override
	public String getUsername() {
		return user.getEmail();
	}

	@Override
	public String getPassword() {
		return user.getPassword_hash();
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {

		return user.getGlobalRoles()
				.stream()
				.map(UserGlobalRole::getRole)
				.map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().toUpperCase()))
				.collect(Collectors.toList());

	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	// Podés exponer el objeto User si necesitás accederlo luego
	public User getUser() {
		return user;
	}
}
