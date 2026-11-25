package acsanfrancisco.budgeting.infrastructure.http.request;

import acsanfrancisco.budgeting.application.input.ChangePasswordInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Must inform password")
        @Size(min = 6, max = 30, message = "Inform a valid password")
        String currentPassword,
        @NotBlank(message = "Must inform password")
        @Size(min = 6, max = 30, message = "Inform a valid password")
        String newPassword,
        @NotBlank(message = "Must inform password")
        @Size(min = 6, max = 30, message = "Inform a valid password")
        String confirmNewPassword) {

    public ChangePasswordInput toInput() {
        return new ChangePasswordInput(currentPassword, newPassword, confirmNewPassword);
    }
}
