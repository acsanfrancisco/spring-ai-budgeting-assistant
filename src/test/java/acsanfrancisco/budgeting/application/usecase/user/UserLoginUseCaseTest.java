package acsanfrancisco.budgeting.application.usecase.user;

import acsanfrancisco.budgeting.application.input.UserLoginInput;
import acsanfrancisco.budgeting.application.output.TokenOutput;
import acsanfrancisco.budgeting.infrastructure.security.TokenProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockMakers;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class UserLoginUseCaseTest {

    @Mock(mockMaker = MockMakers.SUBCLASS)
    TokenProvider tokenProvider;

    @Mock
    AuthenticationManager authenticationManager;

    @InjectMocks
    UserLoginUseCase useCase;

    UserLoginInput validInput;

    @BeforeEach
    void setUp() {
        validInput = new UserLoginInput("user@example.com", "password");
    }

    @Test
    @DisplayName("Should authenticate User and return generated token")
    void shouldAuthenticateUserAndReturnToken() {
        Authentication authenticatedUser = new UsernamePasswordAuthenticationToken("authenticated-user", null, List.of());
        Mockito.when(authenticationManager.authenticate(Mockito.any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authenticatedUser);
        Mockito.when(tokenProvider.generateToken(authenticatedUser)).thenReturn("generated-token");

        TokenOutput output = useCase.execute(validInput);

        var captor = ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        Mockito.verify(authenticationManager, Mockito.times(1)).authenticate(captor.capture());
        Assertions.assertEquals(validInput.email(), captor.getValue().getPrincipal());
        Assertions.assertEquals(validInput.password(), captor.getValue().getCredentials());
        Assertions.assertFalse(captor.getValue().isAuthenticated());
        Assertions.assertEquals(new TokenOutput("generated-token"), output);
        Mockito.verify(tokenProvider, Mockito.times(1)).generateToken(authenticatedUser);
    }

    @Test
    @DisplayName("Should reject invalid credentials without generating token")
    void shouldRejectInvalidCredentials() {
        BadCredentialsException cause = new BadCredentialsException("Authentication failed");
        Mockito.when(authenticationManager.authenticate(Mockito.any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(cause);

        BadCredentialsException thrown = Assertions.assertThrows(BadCredentialsException.class,
                () -> useCase.execute(validInput));

        Assertions.assertEquals("Invalid email or password", thrown.getMessage());
        Assertions.assertSame(cause, thrown.getCause());
        Mockito.verify(authenticationManager, Mockito.times(1)).authenticate(Mockito.any(UsernamePasswordAuthenticationToken.class));
        Mockito.verifyNoInteractions(tokenProvider);
    }
}
