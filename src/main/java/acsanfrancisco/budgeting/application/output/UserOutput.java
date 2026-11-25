package acsanfrancisco.budgeting.application.output;

import acsanfrancisco.budgeting.domain.user.User;

import java.util.UUID;

public record UserOutput(UUID id, String fullName, String email) {
    public static UserOutput from(User user) {
        return new UserOutput(
                user.getId().id(),
                user.getFullName(),
                user.getEmail());
    }
}
