package acsanfrancisco.budgeting.application.usecase.user;

import acsanfrancisco.budgeting.application.input.UserLoginInput;
import acsanfrancisco.budgeting.application.output.TokenOutput;
import acsanfrancisco.budgeting.infrastructure.security.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserLoginUseCase {

    private final TokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public TokenOutput execute(UserLoginInput loginInput) {
        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginInput.email(), loginInput.password()));
            String token = tokenProvider.generateToken(authentication);
            return new TokenOutput(token);
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Invalid email or password", e);
        }
    }
}
