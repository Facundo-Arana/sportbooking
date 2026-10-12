package com.example.sportbooking.user.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.sportbooking.user.entity.Role;
import com.example.sportbooking.user.repository.RoleRepository;

// Primer RoleService: RoleRepository ya existia (lo usa user-venue-role para
// resolver "existe el rol con este id?"), pero para exponer el catalogo via
// controller faltaba una capa de servicio que respete la convencion
// (controllers -> service -> repository, nunca el repository directo).
@Service
public class RoleService {

	private final RoleRepository roleRepository;

	public RoleService(RoleRepository roleRepository) {
		this.roleRepository = roleRepository;
	}

	public List<Role> findAll() {
		return roleRepository.findAll();
	}

}
