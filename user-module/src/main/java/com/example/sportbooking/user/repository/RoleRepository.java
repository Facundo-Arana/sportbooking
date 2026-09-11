package com.example.sportbooking.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.sportbooking.user.entity.Role;

// Primer repository para Role: la entity ya existía (usada desde
// UserGlobalRole), pero nada la resolvía todavía por id. user-venue-role-module
// es el primer caso que necesita "¿existe el rol con este id?" -- mismo
// motivo por el que UserNotFoundException nació recién con reservation-module.
public interface RoleRepository extends JpaRepository<Role, Long> {

}
