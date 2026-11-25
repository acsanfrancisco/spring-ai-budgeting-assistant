package acsanfrancisco.budgeting.infrastructure.http.documentation;

import acsanfrancisco.budgeting.infrastructure.http.exception.ErrorMessage;
import acsanfrancisco.budgeting.infrastructure.http.request.LoginRequest;
import acsanfrancisco.budgeting.infrastructure.http.response.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(
        name = "Authentication",
        description = "Endpoint responsible for authenticating users in the Budgeting application."
)
public interface AuthControllerDoc {

    @Operation(
            summary = "Authenticate a user",
            description = """
                    Authenticate an existing user using email and password and return a JWT.
                    This endpoint is public and does not require a Bearer token.
                    Email must be valid and password must contain between 6 and 30 characters.
                    Both fields are required.
                    Use the returned token in the Authorization header of protected requests:
                    Authorization: Bearer <token>.
                    Invalid credentials return 401.
                    Request field validation failures return 422.
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "User authenticated successfully; JWT returned in the token field",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid or missing JSON request body",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Invalid email or password",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))
                    ),
                    @ApiResponse(
                            responseCode = "422",
                            description = "Request field validation failed",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))
                    )
            }
    )
    ResponseEntity<TokenResponse> login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Credentials of a registered user. The example values are illustrative.",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "email": "user@example.com",
                                      "password": "ExamplePassword123"
                                    }
                                    """)))
            @RequestBody @Valid LoginRequest loginRequest);
}
