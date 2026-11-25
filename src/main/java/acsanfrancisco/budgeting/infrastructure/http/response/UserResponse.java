package acsanfrancisco.budgeting.infrastructure.http.response;

import acsanfrancisco.budgeting.application.output.UserOutput;
import java.util.UUID;

public record UserResponse(UUID id, String fullName, String email) {

    public static UserResponse from(UserOutput user) {
        return new UserResponse(
                user.id(),
                user.fullName(),
                user.email());
    }
}
