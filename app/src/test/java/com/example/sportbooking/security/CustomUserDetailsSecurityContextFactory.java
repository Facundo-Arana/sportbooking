package com.example.sportbooking.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import com.example.sportbooking.auth.security.util.CustomUserDetails;
import com.example.sportbooking.user.entity.Role;
import com.example.sportbooking.user.entity.User;
import com.example.sportbooking.user.entity.UserGlobalRole;

/**
 * Arma el {@link CustomUserDetails} del test a partir de los atributos de
 * {@link WithCustomUserDetails}: crea un {@code User} con sus
 * {@code UserGlobalRole} (sin tocar la base) y lo deja como principal del
 * {@code SecurityContext}. Asi {@code @PreAuthorize} evalua authorities reales
 * y {@code VenueAuthorization.currentUser()} reconoce al principal.
 */
public class CustomUserDetailsSecurityContextFactory implements WithSecurityContextFactory<WithCustomUserDetails> {

    @Override
    public SecurityContext createSecurityContext(WithCustomUserDetails annotation) {
        User user = new User(annotation.username(), annotation.email(), "test-password-hash", annotation.name());
        user.setId(annotation.id());

        for (String roleName : annotation.roles()) {
            Role role = Role.builder().name(roleName).build();
            UserGlobalRole link = UserGlobalRole.builder().role(role).build();
            link.setUser(user);
            user.getGlobalRoles().add(link);
        }

        CustomUserDetails principal = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }

}
