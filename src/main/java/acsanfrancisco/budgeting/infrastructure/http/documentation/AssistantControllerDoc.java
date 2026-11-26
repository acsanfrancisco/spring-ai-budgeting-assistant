package acsanfrancisco.budgeting.infrastructure.http.documentation;

import acsanfrancisco.budgeting.infrastructure.http.request.AssistantTextRequest;
import acsanfrancisco.budgeting.infrastructure.security.CustomUserDetails;
import acsanfrancisco.budgeting.infrastructure.http.exception.ErrorMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@Tag(
        name = "Assistant",
        description = """
                Financial assistant with audio transcription, AI-assisted tool calling, and conversation history.
                All endpoints require an Authorization: Bearer <token> header."""
)
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Authentication is missing or invalid", content = @Content)
public interface AssistantControllerDoc {

    @Operation(summary = "Process a written message",
            description = "Uses the authenticated user's chat settings and the same active conversation as voice requests.")
    @ApiResponse(responseCode = "200", description = "Assistant text returned")
    @ApiResponse(responseCode = "400", description = "Invalid request body or idempotency key")
    @ApiResponse(responseCode = "404", description = "AI configuration not found")
    @ApiResponse(responseCode = "409", description = "Conversation in progress or key reused with another message")
    @ApiResponse(responseCode = "422", description = "Invalid message, provider key, model, permissions or exhausted quota")
    @ApiResponse(responseCode = "429", description = "Provider usage limit reached; Retry-After when available")
    @ApiResponse(responseCode = "503", description = "Provider unavailable")
    @ApiResponse(responseCode = "504", description = "Chat provider timed out")
    ResponseEntity<String> processFromText(AssistantTextRequest request,
            @Parameter(description = "UUID reused when retrying the same single-transaction request") java.util.UUID key,
            @Parameter(hidden = true) CustomUserDetails userDetails);

    @Operation(summary = "Close the authenticated user's active conversation",
            description = "Deletes the conversation and its messages. The next interaction starts a new conversation.")
    @ApiResponse(responseCode = "204", description = "Conversation closed and deleted")
    @ApiResponse(responseCode = "404", description = "No active conversation found")
    ResponseEntity<Void> closeConversation(@Parameter(hidden = true) CustomUserDetails userDetails);

    @Operation(
            summary = "Process a voice request",
            description = """
                    Transcribe an uploaded audio file and process the request for the authenticated user.
                    Send multipart/form-data with a required, non-empty file part named file, up to 25 MB.
                    Configure chat and transcription through POST /api/v1/users/ai-config, together or separately.
                    The assistant interprets the transcript and can call financial tools in the active conversation.
                    The application uses the authenticated user's saved providers, models, and API keys.
                    Transcription uses the user's OpenAI credentials.
                    The assistant can register income and expenses, list transactions, consult monthly expenses
                    and financial summaries, create monthly budget limits, and consult budget status.
                    The response is the assistant's text in Brazilian Portuguese. It may ask for clarification
                    when information needed to process the request is missing or ambiguous.
                    The application selects the conversation and user identity from the authenticated user;
                    no user ID or conversation ID parameter is required.
                    Each text or voice interaction that reaches the chat flow renews the active conversation
                    for 24 hours. An expired conversation is deleted with its messages and replaced on the next interaction.
                    The 15 most recent messages are sent as context, isolated by conversation ID.
                    Send an optional Idempotency-Key (UUID) and reuse it, with the same audio file, when retrying
                    a request that creates a single transaction. A retry with the same key and audio returns the
                    transaction already saved without transcribing again; the same key with another audio file
                    returns 409. If the provider fails after the transaction was saved, the error response
                    carries its ID in the X-Transaction-Id header.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Voice request processed; assistant text returned",
                            content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"),
                                    examples = @ExampleObject(value = "Você gastou R$ 40,00 com supermercado em novembro de 2026."))),
                    @ApiResponse(responseCode = "400", description = "Required audio file is missing or empty, or request data is invalid; domain validation errors return ErrorMessage JSON",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "404", description = "AI configuration not found, or chat or transcription not configured",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "409", description = "Conversation in progress, or Idempotency-Key reused with another audio file",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "413", description = "Audio file exceeds the 25 MB limit",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "415", description = "Request content type is not multipart/form-data",
                            content = @Content),
                    @ApiResponse(responseCode = "422", description = "Invalid provider key, model, permissions or exhausted quota",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class))),
                    @ApiResponse(responseCode = "429", description = "Provider usage limit reached; Retry-After when available"),
                    @ApiResponse(responseCode = "503", description = "Provider unavailable"),
                    @ApiResponse(responseCode = "504", description = "Transcription or chat provider timed out"),
                    @ApiResponse(responseCode = "500", description = "An unhandled error occurred during audio processing, conversation persistence, or AI processing",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorMessage.class)))
            }
    )
    ResponseEntity<String> processFromVoice(
            @Parameter(description = "Audio file to transcribe and process", required = true,
                    schema = @Schema(type = "string", format = "binary"))
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "UUID reused, with the same audio file, when retrying the same single-transaction request") java.util.UUID key,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails);
}
