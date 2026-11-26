package acsanfrancisco.budgeting.infrastructure.http.controller;

import acsanfrancisco.budgeting.application.usecase.aiconversation.CloseByIdAndUserIDUseCase;
import acsanfrancisco.budgeting.infrastructure.ai.AssistantService;
import acsanfrancisco.budgeting.infrastructure.http.documentation.AssistantControllerDoc;
import acsanfrancisco.budgeting.infrastructure.http.request.AssistantTextRequest;
import acsanfrancisco.budgeting.infrastructure.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController implements AssistantControllerDoc {

    private final AssistantService assistantService;

    private final CloseByIdAndUserIDUseCase closeConversationUseCase;

    public AssistantController(AssistantService assistantService, CloseByIdAndUserIDUseCase closeConversationUseCase) {
        this.assistantService = assistantService;
        this.closeConversationUseCase = closeConversationUseCase;
    }

    @Override
    @PostMapping("/conversation/close")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> closeConversation(@AuthenticationPrincipal CustomUserDetails userDetails) {
        closeConversationUseCase.execute(userDetails.getId());
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }

    @Override
    @PostMapping(value = "/text", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> processFromText(
            @Valid @RequestBody AssistantTextRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) UUID key,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(key == null
                        ? assistantService.executeText(request.message(), userDetails.getId())
                        : assistantService.executeText(request.message(), userDetails.getId(), key));
    }

    @Override
    @PostMapping(value = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> processFromVoice(
            @RequestPart("file") MultipartFile file,
            @RequestHeader(name = "Idempotency-Key", required = false) UUID key,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Audio file is empty");
        }
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(key == null
                        ? assistantService.execute(file.getResource(), userDetails.getId())
                        : assistantService.execute(file.getResource(), userDetails.getId(), key));
    }
}



