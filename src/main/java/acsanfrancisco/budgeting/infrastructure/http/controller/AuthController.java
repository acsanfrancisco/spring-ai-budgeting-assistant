package acsanfrancisco.budgeting.infrastructure.http.controller;

import acsanfrancisco.budgeting.application.output.TokenOutput;
import acsanfrancisco.budgeting.application.usecase.user.UserLoginUseCase;
import acsanfrancisco.budgeting.infrastructure.http.documentation.AuthControllerDoc;
import acsanfrancisco.budgeting.infrastructure.http.request.LoginRequest;
import acsanfrancisco.budgeting.infrastructure.http.response.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController implements AuthControllerDoc {

    private final UserLoginUseCase userLoginUseCase;

    public AuthController(UserLoginUseCase userLoginUseCase) {
        this.userLoginUseCase = userLoginUseCase;
    }

    @Override
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest loginRequest) {
        TokenOutput token = userLoginUseCase.execute(loginRequest.toLoginInput());
        return ResponseEntity.status(HttpStatus.OK).body(TokenResponse.from(token));
    }
}
