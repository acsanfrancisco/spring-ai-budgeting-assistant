package acsanfrancisco.budgeting.infrastructure.security;

import acsanfrancisco.budgeting.domain.user.Role;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ExtendWith(MockitoExtension.class)
public class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("First Test User", "first@example.com", "encoded-password");
    }

    @Test
    @DisplayName("Should look up provided email and map user ID, email, encoded password and user authority")
    void shouldLoadUserDetailsByEmail() {
        Mockito.when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        UserDetails result = userDetailsService.loadUserByUsername(user.getEmail());

        CustomUserDetails details = Assertions.assertInstanceOf(CustomUserDetails.class, result);
        Assertions.assertEquals(user.getId(), details.getId());
        Assertions.assertEquals(user.getEmail(), details.getUsername());
        Assertions.assertEquals(user.getPassword(), details.getPassword());
        Assertions.assertEquals(Set.of("ROLE_USER"),
                details.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()));
        Mockito.verify(userRepository).findByEmail(user.getEmail());
        Mockito.verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("Should convert all persisted roles to authorities without adding or removing roles")
    void shouldMapAllUserRolesToAuthorities() {
        user.setRoles(Set.of(Role.ROLE_USER, Role.ROLE_ADMIN));
        Mockito.when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername(user.getEmail());

        Assertions.assertEquals(2, details.getAuthorities().size());
        Assertions.assertEquals(Set.of("ROLE_USER", "ROLE_ADMIN"),
                details.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()));
        Mockito.verify(userRepository).findByEmail(user.getEmail());
    }

    @Test
    @DisplayName("Should throw UsernameNotFoundException when repository finds no user for provided email")
    void shouldRejectNonexistentUser() {
        String email = "missing@example.com";
        Mockito.when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        UsernameNotFoundException thrown = Assertions.assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername(email));

        Assertions.assertEquals("Email not found", thrown.getMessage());
        Mockito.verify(userRepository).findByEmail(email);
        Mockito.verifyNoMoreInteractions(userRepository);
    }
}
