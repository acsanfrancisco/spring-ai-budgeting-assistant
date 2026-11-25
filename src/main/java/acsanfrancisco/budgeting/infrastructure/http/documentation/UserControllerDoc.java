package acsanfrancisco.budgeting.infrastructure.http.documentation;

import acsanfrancisco.budgeting.infrastructure.http.exception.ErrorMessage;
import acsanfrancisco.budgeting.infrastructure.http.request.UserRequest;
import acsanfrancisco.budgeting.infrastructure.http.request.AiConfigurationRequest;
import acsanfrancisco.budgeting.infrastructure.http.request.ChangePasswordRequest;
import acsanfrancisco.budgeting.infrastructure.http.response.AiConfigurationResponse;
import acsanfrancisco.budgeting.infrastructure.security.CustomUserDetails;
import acsanfrancisco.budgeting.infrastructure.http.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(
        name = "Users",
        description = "User registration, password change and AI configuration management in the Budgeting application."
)
public interface UserControllerDoc {

    @Operation(
            summary = "Create a user",
            description = """
                    Register a new user in the Budgeting application. Authentication is not required.
                    Full name must contain between 6 and 100 characters, email must be valid and unique,
                    and password must contain between 6 and 30 characters. All fields are required.
                    The response contains the user ID, full name, and email.
                    The password is not returned.
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "User created successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid request body or invalid user data",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Email already registered",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))
                    ),
                    @ApiResponse(
                            responseCode = "422",
                            description = "Request field validation failed",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))
                    )
            }
    )
    ResponseEntity<UserResponse> createUser(@RequestBody @Valid UserRequest request);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Configure chat or transcription",
            description = """
                    Save the chat and transcription configuration for the authenticated user.
                    Authentication is required. No user ID parameter is accepted.
                    Chat providers: OPENAI, DEEPSEEK, GEMINI, and ANTHROPIC.
                    Supply at least one complete modality. Chat requires a provider, model and API key.
                    Omitting an entire modality preserves its saved configuration.
                    Transcription provider: OPENAI, which requires a non-blank transcriptionModel and transcriptionApiKey,
                    for example whisper-1, gpt-4o-transcribe or gpt-4o-mini-transcribe.
                    API keys are encrypted before storage and are never included in the response.
                    Saving configuration does not validate credentials or model availability with the providers.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "AI configuration created or replaced successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AiConfigurationResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid JSON, unknown provider, or invalid provider-specific transcription configuration",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "401", description = "Authentication is missing or invalid", content = @Content),
                    @ApiResponse(responseCode = "404", description = "User not found",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "422", description = "Required request field validation failed",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "500", description = "Configuration encryption or persistence failed",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class)))
            }
    )
    ResponseEntity<AiConfigurationResponse> addAiConfiguration(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Chat settings, transcription settings, or both. Example credentials are illustrative and must be replaced.",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AiConfigurationRequest.class),
                            examples = {
                                    @ExampleObject(name = "OpenAI transcription", value = """
                                            {
                                              "chatProvider": "OPENAI",
                                              "chatModel": "gpt-4o-mini",
                                              "chatApiKey": "your-chat-api-key",
                                              "transcriptionProvider": "OPENAI",
                                              "transcriptionModel": "whisper-1",
                                              "transcriptionApiKey": "your-transcription-api-key"
                                            }
                                            """)
                            }))
            @RequestBody @Valid AiConfigurationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Change the password",
            description = """
                    Change the password of the authenticated user. Authentication is required.
                    No user ID or email is accepted: the user comes from the token.
                    The current password must be correct, and the new password must contain between 6 and 30
                    characters and match its confirmation. The new password is stored as a BCrypt hash.
                    Tokens issued before the change remain valid until they expire.
                    """,
            responses = {
                    @ApiResponse(responseCode = "204", description = "Password changed successfully", content = @Content),
                    @ApiResponse(responseCode = "400", description = "Invalid request body, incorrect current password, or new password and confirmation do not match",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "401", description = "Authentication is missing or invalid", content = @Content),
                    @ApiResponse(responseCode = "404", description = "User not found",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "422", description = "Request field validation failed",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class)))
            }
    )
    ResponseEntity<Void> changeUserPassword(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Current password, new password and its confirmation.",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ChangePasswordRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "currentPassword": "secret123",
                                      "newPassword": "new-secret456",
                                      "confirmNewPassword": "new-secret456"
                                    }
                                    """)))
            @RequestBody @Valid ChangePasswordRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);
}
