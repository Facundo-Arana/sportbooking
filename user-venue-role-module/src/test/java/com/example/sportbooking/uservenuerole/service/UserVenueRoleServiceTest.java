package com.example.sportbooking.uservenuerole.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.sportbooking.user.entity.Role;
import com.example.sportbooking.user.entity.User;
import com.example.sportbooking.user.exception.RoleNotFoundException;
import com.example.sportbooking.user.exception.UserNotFoundException;
import com.example.sportbooking.user.repository.RoleRepository;
import com.example.sportbooking.user.repository.UserRepository;
import com.example.sportbooking.uservenuerole.dto.UserVenueRoleDTO;
import com.example.sportbooking.uservenuerole.entity.UserVenueRole;
import com.example.sportbooking.uservenuerole.exception.UserVenueRoleNotFoundException;
import com.example.sportbooking.uservenuerole.repository.UserVenueRoleRepository;
import com.example.sportbooking.venue.entity.Venue;
import com.example.sportbooking.venue.exception.VenueNotFoundException;
import com.example.sportbooking.venue.repository.VenueRepository;

@ExtendWith(MockitoExtension.class)
class UserVenueRoleServiceTest {

    @Mock
    private UserVenueRoleRepository userVenueRoleRepository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private UserVenueRoleService userVenueRoleService;

    private UserVenueRoleDTO dto(Long venueId, Long userId, Long roleId) {
        return new UserVenueRoleDTO(venueId, userId, roleId);
    }

    @Test
    void createRejectsMissingIds() {
        assertThatThrownBy(() -> userVenueRoleService.create(dto(null, 1L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> userVenueRoleService.create(dto(1L, null, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> userVenueRoleService.create(dto(1L, 1L, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createThrowsWhenVenueDoesNotExist() {
        when(venueRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userVenueRoleService.create(dto(1L, 2L, 3L)))
                .isInstanceOf(VenueNotFoundException.class);
    }

    @Test
    void createThrowsWhenUserDoesNotExist() {
        when(venueRepository.findById(1L)).thenReturn(Optional.of(Venue.builder().id(1L).build()));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userVenueRoleService.create(dto(1L, 2L, 3L)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void createThrowsWhenRoleDoesNotExist() {
        when(venueRepository.findById(1L)).thenReturn(Optional.of(Venue.builder().id(1L).build()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).build()));
        when(roleRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userVenueRoleService.create(dto(1L, 2L, 3L)))
                .isInstanceOf(RoleNotFoundException.class);
    }

    @Test
    void createRejectsDuplicateAssignment() {
        when(venueRepository.findById(1L)).thenReturn(Optional.of(Venue.builder().id(1L).build()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).build()));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(Role.builder().id(3L).build()));
        when(userVenueRoleRepository.existsByVenue_IdAndUser_IdAndRole_Id(1L, 2L, 3L)).thenReturn(true);

        assertThatThrownBy(() -> userVenueRoleService.create(dto(1L, 2L, 3L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya tiene ese rol");
        verify(userVenueRoleRepository, never()).save(any());
    }

    @Test
    void createPersistsAssignment() {
        Venue venue = Venue.builder().id(1L).build();
        User user = User.builder().id(2L).build();
        Role role = Role.builder().id(3L).build();
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));
        when(userVenueRoleRepository.existsByVenue_IdAndUser_IdAndRole_Id(1L, 2L, 3L)).thenReturn(false);
        when(userVenueRoleRepository.save(any(UserVenueRole.class))).thenAnswer(inv -> inv.getArgument(0));

        UserVenueRole created = userVenueRoleService.create(dto(1L, 2L, 3L));

        assertThat(created.getVenue()).isEqualTo(venue);
        assertThat(created.getUser()).isEqualTo(user);
        assertThat(created.getRole()).isEqualTo(role);
    }

    @Test
    void findAllDelegatesToMostSpecificQuery() {
        UserVenueRole row = UserVenueRole.builder().id(1L).build();
        when(userVenueRoleRepository.findByVenue_IdAndUser_Id(1L, 2L)).thenReturn(List.of(row));

        assertThat(userVenueRoleService.findAll(1L, 2L)).containsExactly(row);
        verify(userVenueRoleRepository).findByVenue_IdAndUser_Id(1L, 2L);
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(userVenueRoleRepository.existsById(8L)).thenReturn(false);

        assertThatThrownBy(() -> userVenueRoleService.delete(8L))
                .isInstanceOf(UserVenueRoleNotFoundException.class);
    }

}
