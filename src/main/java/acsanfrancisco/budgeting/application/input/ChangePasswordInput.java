package acsanfrancisco.budgeting.application.input;

public record ChangePasswordInput(String currentPassword, String newPassword, String confirmNewPassword) {
}
