package acsanfrancisco.budgeting.infrastructure.http.request;

import jakarta.validation.constraints.NotBlank;

public record AssistantTextRequest(@NotBlank(message = "Must inform message") String message) {}
