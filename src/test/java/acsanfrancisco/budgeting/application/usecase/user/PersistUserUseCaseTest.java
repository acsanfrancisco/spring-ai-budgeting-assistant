package acsanfrancisco.budgeting.application.usecase.user;

import acsanfrancisco.budgeting.application.exception.ResourceAlreadyExistsException;
import acsanfrancisco.budgeting.application.input.CreateUserInput;
import acsanfrancisco.budgeting.application.output.UserOutput;
import acsanfrancisco.budgeting.domain.user.Role;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
public class PersistUserUseCaseTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    PersistUserUseCase useCase;

    CreateUserInput validInput;
    String encodedPassword;

    @BeforeEach
    void setUp() {
        validInput = new CreateUserInput("Test User", "user@example.com", "password");
        encodedPassword = "encoded-password";
    }

    @Test
    @DisplayName("Should reject null input")
    void shouldRejectNullInput() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(null));

        Mockito.verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Should create User with encoded password and default role")
    void shouldCreateUserWithEncodedPassword() {
        CreateUserInput input = validInput;
        Mockito.when(passwordEncoder.encode(input.password())).thenReturn(encodedPassword);
        Mockito.when(userRepository.save(Mockito.any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserOutput output = useCase.execute(input);

        var captor = ArgumentCaptor.forClass(User.class);
        Mockito.verify(userRepository, Mockito.times(1)).save(captor.capture());
        User saved = captor.getValue();
        Assertions.assertNotNull(saved.getId());
        Assertions.assertEquals(input.fullName(), saved.getFullName());
        Assertions.assertEquals(input.email(), saved.getEmail());
        Assertions.assertEquals(encodedPassword, saved.getPassword());
        Assertions.assertNotEquals(input.password(), saved.getPassword());
        Assertions.assertEquals(java.util.Set.of(Role.ROLE_USER), saved.getRoles());
        Assertions.assertEquals(saved.getId().id(), output.id());
        Assertions.assertEquals(input.fullName(), output.fullName());
        Assertions.assertEquals(input.email(), output.email());
        Mockito.verify(userRepository, Mockito.times(1)).existsByEmail(input.email());
        Mockito.verify(passwordEncoder, Mockito.times(1)).encode(input.password());
    }

    @Test
    @DisplayName("Should accept minimum password length")
    void shouldAcceptMinimumPasswordLength() {
        CreateUserInput input = new CreateUserInput(validInput.fullName(), validInput.email(), "a".repeat(6));
        Mockito.when(passwordEncoder.encode(input.password())).thenReturn(encodedPassword);
        Mockito.when(userRepository.save(Mockito.any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserOutput output = useCase.execute(input);

        var captor = ArgumentCaptor.forClass(User.class);
        Mockito.verify(userRepository, Mockito.times(1)).save(captor.capture());
        User saved = captor.getValue();
        Assertions.assertNotNull(saved.getId());
        Assertions.assertEquals(input.fullName(), saved.getFullName());
        Assertions.assertEquals(input.email(), saved.getEmail());
        Assertions.assertEquals(encodedPassword, saved.getPassword());
        Assertions.assertNotEquals(input.password(), saved.getPassword());
        Assertions.assertEquals(java.util.Set.of(Role.ROLE_USER), saved.getRoles());
        Assertions.assertEquals(saved.getId().id(), output.id());
        Assertions.assertEquals(input.fullName(), output.fullName());
        Assertions.assertEquals(input.email(), output.email());
        Mockito.verify(userRepository, Mockito.times(1)).existsByEmail(input.email());
        Mockito.verify(passwordEncoder, Mockito.times(1)).encode(input.password());
    }

    @Test
    @DisplayName("Should accept maximum password length")
    void shouldAcceptMaximumPasswordLength() {
        CreateUserInput input = new CreateUserInput(validInput.fullName(), validInput.email(), "a".repeat(30));
        Mockito.when(passwordEncoder.encode(input.password())).thenReturn(encodedPassword);
        Mockito.when(userRepository.save(Mockito.any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserOutput output = useCase.execute(input);

        var captor = ArgumentCaptor.forClass(User.class);
        Mockito.verify(userRepository, Mockito.times(1)).save(captor.capture());
        User saved = captor.getValue();
        Assertions.assertNotNull(saved.getId());
        Assertions.assertEquals(input.fullName(), saved.getFullName());
        Assertions.assertEquals(input.email(), saved.getEmail());
        Assertions.assertEquals(encodedPassword, saved.getPassword());
        Assertions.assertNotEquals(input.password(), saved.getPassword());
        Assertions.assertEquals(java.util.Set.of(Role.ROLE_USER), saved.getRoles());
        Assertions.assertEquals(saved.getId().id(), output.id());
        Assertions.assertEquals(input.fullName(), output.fullName());
        Assertions.assertEquals(input.email(), output.email());
        Mockito.verify(userRepository, Mockito.times(1)).existsByEmail(input.email());
        Mockito.verify(passwordEncoder, Mockito.times(1)).encode(input.password());
    }

    @Test
    @DisplayName("Should accept minimum full name length")
    void shouldAcceptMinimumFullNameLength() {
        CreateUserInput input = new CreateUserInput("a".repeat(6), validInput.email(), validInput.password());
        Mockito.when(passwordEncoder.encode(input.password())).thenReturn(encodedPassword);
        Mockito.when(userRepository.save(Mockito.any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserOutput output = useCase.execute(input);

        var captor = ArgumentCaptor.forClass(User.class);
        Mockito.verify(userRepository, Mockito.times(1)).save(captor.capture());
        User saved = captor.getValue();
        Assertions.assertNotNull(saved.getId());
        Assertions.assertEquals(input.fullName(), saved.getFullName());
        Assertions.assertEquals(input.email(), saved.getEmail());
        Assertions.assertEquals(encodedPassword, saved.getPassword());
        Assertions.assertNotEquals(input.password(), saved.getPassword());
        Assertions.assertEquals(java.util.Set.of(Role.ROLE_USER), saved.getRoles());
        Assertions.assertEquals(saved.getId().id(), output.id());
        Assertions.assertEquals(input.fullName(), output.fullName());
        Assertions.assertEquals(input.email(), output.email());
        Mockito.verify(userRepository, Mockito.times(1)).existsByEmail(input.email());
        Mockito.verify(passwordEncoder, Mockito.times(1)).encode(input.password());
    }

    @Test
    @DisplayName("Should accept maximum full name length")
    void shouldAcceptMaximumFullNameLength() {
        CreateUserInput input = new CreateUserInput("a".repeat(100), validInput.email(), validInput.password());
        Mockito.when(passwordEncoder.encode(input.password())).thenReturn(encodedPassword);
        Mockito.when(userRepository.save(Mockito.any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserOutput output = useCase.execute(input);

        var captor = ArgumentCaptor.forClass(User.class);
        Mockito.verify(userRepository, Mockito.times(1)).save(captor.capture());
        User saved = captor.getValue();
        Assertions.assertNotNull(saved.getId());
        Assertions.assertEquals(input.fullName(), saved.getFullName());
        Assertions.assertEquals(input.email(), saved.getEmail());
        Assertions.assertEquals(encodedPassword, saved.getPassword());
        Assertions.assertNotEquals(input.password(), saved.getPassword());
        Assertions.assertEquals(java.util.Set.of(Role.ROLE_USER), saved.getRoles());
        Assertions.assertEquals(saved.getId().id(), output.id());
        Assertions.assertEquals(input.fullName(), output.fullName());
        Assertions.assertEquals(input.email(), output.email());
        Mockito.verify(userRepository, Mockito.times(1)).existsByEmail(input.email());
        Mockito.verify(passwordEncoder, Mockito.times(1)).encode(input.password());
    }

    @Test
    @DisplayName("Should reject null password")
    void shouldRejectNullPassword() {
        CreateUserInput input = new CreateUserInput(validInput.fullName(), validInput.email(), null);

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input));

        Mockito.verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Should reject too short password")
    void shouldRejectTooShortPassword() {
        CreateUserInput input = new CreateUserInput(validInput.fullName(), validInput.email(), "a".repeat(5));

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input));

        Mockito.verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Should reject too long password")
    void shouldRejectTooLongPassword() {
        CreateUserInput input = new CreateUserInput(validInput.fullName(), validInput.email(), "a".repeat(31));

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input));

        Mockito.verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Should reject duplicate email without encoding password or saving User")
    void shouldRejectDuplicateEmail() {
        Mockito.when(userRepository.existsByEmail(validInput.email())).thenReturn(true);

        Assertions.assertThrows(ResourceAlreadyExistsException.class, () -> useCase.execute(validInput));

        Mockito.verify(userRepository, Mockito.times(1)).existsByEmail(validInput.email());
        Mockito.verify(userRepository, Mockito.never()).save(Mockito.any(User.class));
        Mockito.verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("Should reject null full name")
    void shouldRejectNullFullName() {
        CreateUserInput input = new CreateUserInput(null, validInput.email(), validInput.password());
        Mockito.when(passwordEncoder.encode(input.password())).thenReturn(encodedPassword);

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input));

        Mockito.verify(userRepository, Mockito.never()).save(Mockito.any(User.class));
    }

    @Test
    @DisplayName("Should reject blank full name")
    void shouldRejectBlankFullName() {
        CreateUserInput input = new CreateUserInput("   ", validInput.email(), validInput.password());
        Mockito.when(passwordEncoder.encode(input.password())).thenReturn(encodedPassword);

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input));

        Mockito.verify(userRepository, Mockito.never()).save(Mockito.any(User.class));
    }

    @Test
    @DisplayName("Should reject too short full name")
    void shouldRejectTooShortFullName() {
        CreateUserInput input = new CreateUserInput("a".repeat(5), validInput.email(), validInput.password());
        Mockito.when(passwordEncoder.encode(input.password())).thenReturn(encodedPassword);

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input));

        Mockito.verify(userRepository, Mockito.never()).save(Mockito.any(User.class));
    }

    @Test
    @DisplayName("Should reject too long full name")
    void shouldRejectTooLongFullName() {
        CreateUserInput input = new CreateUserInput("a".repeat(101), validInput.email(), validInput.password());
        Mockito.when(passwordEncoder.encode(input.password())).thenReturn(encodedPassword);

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input));

        Mockito.verify(userRepository, Mockito.never()).save(Mockito.any(User.class));
    }

    @Test
    @DisplayName("Should reject null email")
    void shouldRejectNullEmail() {
        CreateUserInput input = new CreateUserInput(validInput.fullName(), null, validInput.password());
        Mockito.when(passwordEncoder.encode(input.password())).thenReturn(encodedPassword);

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input));

        Mockito.verify(userRepository, Mockito.never()).save(Mockito.any(User.class));
    }

    @Test
    @DisplayName("Should reject blank email")
    void shouldRejectBlankEmail() {
        CreateUserInput input = new CreateUserInput(validInput.fullName(), "   ", validInput.password());
        Mockito.when(passwordEncoder.encode(input.password())).thenReturn(encodedPassword);

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input));

        Mockito.verify(userRepository, Mockito.never()).save(Mockito.any(User.class));
    }

}

