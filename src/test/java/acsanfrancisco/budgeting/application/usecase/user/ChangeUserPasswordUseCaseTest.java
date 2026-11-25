package acsanfrancisco.budgeting.application.usecase.user;

import acsanfrancisco.budgeting.application.exception.PasswordMismatchException;
import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.input.ChangePasswordInput;
import acsanfrancisco.budgeting.domain.user.Role;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChangeUserPasswordUseCaseTest {

    private static final String CURRENT = "secret123";
    private static final String NEW = "new-secret456";

    // A real encoder proves the stored hash matches the new password, not just that save was called.
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UserRepository users = mock(UserRepository.class);
    private final ChangeUserPasswordUseCase useCase = new ChangeUserPasswordUseCase(users, encoder);

    private final UserId userId = new UserId();
    private User user;

    @BeforeEach
    void setUp() {
        user = new User(userId, "Test User", "user@example.com", encoder.encode(CURRENT));
        user.setRoles(Set.of(Role.ROLE_USER, Role.ROLE_ADMIN));
    }

    @Test
    @DisplayName("Should store a hash of the new password and keep the other user data")
    void shouldStoreHashOfNewPassword() {
        when(users.findById(userId)).thenReturn(Optional.of(user));

        useCase.execute(new ChangePasswordInput(CURRENT, NEW, NEW), userId);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        String hash = saved.getValue().getPassword();
        assertThat(hash).isNotEqualTo(NEW);
        assertThat(encoder.matches(NEW, hash)).isTrue();
        assertThat(encoder.matches(CURRENT, hash)).isFalse();
        assertThat(saved.getValue().getId()).isEqualTo(userId);
        assertThat(saved.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(saved.getValue().getRoles()).containsExactlyInAnyOrder(Role.ROLE_USER, Role.ROLE_ADMIN);
    }

    @Test
    @DisplayName("Should reject an incorrect current password without saving")
    void shouldRejectIncorrectCurrentPassword() {
        when(users.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.execute(new ChangePasswordInput("wrong-password", NEW, NEW), userId))
                .isInstanceOf(PasswordMismatchException.class)
                .hasMessage("Current password is incorrect");
        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("Should reject a confirmation that differs from the new password without saving")
    void shouldRejectDifferentConfirmation() {
        when(users.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.execute(new ChangePasswordInput(CURRENT, NEW, "other-secret"), userId))
                .isInstanceOf(PasswordMismatchException.class)
                .hasMessage("The new passwords do not match");
        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("Should report a missing user without saving")
    void shouldReportMissingUser() {
        when(users.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ChangePasswordInput(CURRENT, NEW, NEW), userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
        verify(users, never()).save(any());
    }
}
