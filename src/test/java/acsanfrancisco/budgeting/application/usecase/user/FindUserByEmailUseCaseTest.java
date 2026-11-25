package acsanfrancisco.budgeting.application.usecase.user;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.output.UserOutput;
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
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class FindUserByEmailUseCaseTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    FindUserByEmailUseCase useCase;

    User user;
    String email;

    @BeforeEach
    void setUp() {
        email = "user@example.com";
        user = new User("Test User", email, "encoded-password");
    }

    @Test
    @DisplayName("Should return User when email exists")
    void shouldReturnUserWhenEmailExists() {
        Mockito.when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        UserOutput output = useCase.execute(email);

        Assertions.assertEquals(user.getId().id(), output.id());
        Assertions.assertEquals("Test User", output.fullName());
        Assertions.assertEquals(email, output.email());
        Mockito.verify(userRepository, Mockito.times(1)).findByEmail(email);
    }

    @Test
    @DisplayName("Should reject nonexistent User")
    void shouldRejectNonexistentUser() {
        Mockito.when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        ResourceNotFoundException thrown = Assertions.assertThrows(ResourceNotFoundException.class,
                () -> useCase.execute(email));

        Assertions.assertEquals("User not found", thrown.getMessage());
        Mockito.verify(userRepository, Mockito.times(1)).findByEmail(email);
    }
}
