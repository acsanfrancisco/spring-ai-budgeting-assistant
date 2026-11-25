package acsanfrancisco.budgeting.infrastructure.http.controller;

import acsanfrancisco.budgeting.application.output.AiConfigurationOutput;
import acsanfrancisco.budgeting.application.output.UserOutput;
import acsanfrancisco.budgeting.application.usecase.user.ChangeUserPasswordUseCase;
import acsanfrancisco.budgeting.application.usecase.user.PersistUserUseCase;
import acsanfrancisco.budgeting.application.usecase.useraiconfiguration.AddAiConfigurationUseCase;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.infrastructure.http.documentation.UserControllerDoc;
import acsanfrancisco.budgeting.infrastructure.http.request.AiConfigurationRequest;
import acsanfrancisco.budgeting.infrastructure.http.request.ChangePasswordRequest;
import acsanfrancisco.budgeting.infrastructure.http.request.UserRequest;
import acsanfrancisco.budgeting.infrastructure.http.response.AiConfigurationResponse;
import acsanfrancisco.budgeting.infrastructure.http.response.UserResponse;
import acsanfrancisco.budgeting.infrastructure.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController implements UserControllerDoc {

    private final PersistUserUseCase persistUserUseCase;

    private final AddAiConfigurationUseCase addAiConfigurationUseCase;

    private final ChangeUserPasswordUseCase changeUserPasswordUseCase;

    public UserController(PersistUserUseCase persistUserUseCase, AddAiConfigurationUseCase addAiConfigurationUseCase,
                          ChangeUserPasswordUseCase changeUserPasswordUseCase) {
        this.persistUserUseCase = persistUserUseCase;
        this.addAiConfigurationUseCase = addAiConfigurationUseCase;
        this.changeUserPasswordUseCase = changeUserPasswordUseCase;
    }

    @Override
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody @Valid UserRequest request){
        UserOutput user = persistUserUseCase.execute(request.toInput());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @Override
    @PostMapping("/ai-config")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AiConfigurationResponse> addAiConfiguration(
            @RequestBody @Valid AiConfigurationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails){
        UserId userId = userDetails.getId();
        AiConfigurationOutput response = addAiConfigurationUseCase.execute(request.toInput(), userId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(AiConfigurationResponse.from(response));

    }

    @Override
    @PostMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changeUserPassword(
            @RequestBody @Valid ChangePasswordRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails){
        UserId userId = userDetails.getId();
        changeUserPasswordUseCase.execute(request.toInput(), userId);
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }

}
