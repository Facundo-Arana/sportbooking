package com.example.sportbooking;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.sportbooking.venue.repository.VenueRepository;

/**
 * Smoke test del bootstrap de integracion (tarea 2.4): levanta el contexto
 * completo contra el Postgres efimero del {@link AbstractIntegrationTest}.
 *
 * <p>La clave de la verificacion es {@code venueRepository.count() == 0}: el
 * Postgres de dev tiene datos (el seed crea una sede), asi que un container
 * recien creado y vacio confirma que se esta usando el Postgres efimero y no
 * el de desarrollo.
 */
class ContextLoadsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private VenueRepository venueRepository;

    @Test
    void contextLoadsAgainstEphemeralDatabase() throws Exception {
        assertThat(dataSource.getConnection().isValid(2)).isTrue();
        assertThat(venueRepository.count()).isZero();
    }

}
