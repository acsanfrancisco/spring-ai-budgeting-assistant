package acsanfrancisco.budgeting.infrastructure.http.request;

import acsanfrancisco.budgeting.application.input.CreateUserInput;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRequest(
        @NotBlank(message = "Must inform full name")
        @Size(min = 6, max = 100, message = "Invalid size")
        String fullName,
        @Email(message = "Must inform a valid email")
        @NotBlank(message = "Must inform email")
        String email,
        @NotBlank(message = "Must inform password")
        @Size(min = 6, max = 30, message = "Inform a valid password")
        String password) {

    public CreateUserInput toInput() {
        return new CreateUserInput(fullName, email, password);
    }
}
