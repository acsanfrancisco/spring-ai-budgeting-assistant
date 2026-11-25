package acsanfrancisco.budgeting.infrastructure.http.request;

import acsanfrancisco.budgeting.application.input.UserLoginInput;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest (
        @Email(message = "Must inform a valid email")
        @NotBlank(message = "Must inform email")
        String email,
        @NotBlank(message = "Must inform password")
        @Size(min = 6, max = 30, message = "Inform a valid password")
        String password) {

    public UserLoginInput toLoginInput() {
        return new UserLoginInput(email, password);
    }
}
