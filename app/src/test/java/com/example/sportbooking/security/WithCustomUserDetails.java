package com.example.sportbooking.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * Inyecta un {@code CustomUserDetails} construido a partir de un {@code User}
 * de prueba en el {@code SecurityContext} (D5 del design).
 *
 * <p>Existe porque {@code @WithMockUser} produce un principal que NO es
 * {@code CustomUserDetails}: {@code VenueAuthorization.currentUser()} lo
 * descarta y todos los {@code canManage*} dan {@code false}, generando 403
 * falsos justo en lo que mas importa testear (autorizacion por recurso).
 *
 * @see CustomUserDetailsSecurityContextFactory
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = CustomUserDetailsSecurityContextFactory.class)
public @interface WithCustomUserDetails {

    long id() default 1L;

    String username() default "admin";

    String email() default "admin@sportbooking.dev";

    String name() default "Administrador";

    /** Nombres de rol global; se exponen como {@code ROLE_<MAYUSCULAS>}. */
    String[] roles() default { "ADMIN" };

}
