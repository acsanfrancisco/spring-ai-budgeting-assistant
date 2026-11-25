package acsanfrancisco.budgeting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Troca de senha contra o servidor HTTP real e o PostgreSQL: depois da troca, o login
 * aceita só a senha nova.
 */
class ChangePasswordIntegrationTest extends HttpIntegrationTestSupport {

    private static final String NEW_PASSWORD = "new-secret456";

    @Test
    @DisplayName("Should log in only with the new password after changing it")
    void shouldLogInOnlyWithNewPassword() {
        String email = "password-change@example.com";
        String token = registerAndLogin(email).token();

        ResponseEntity<JsonNode> change = post(token, "/users/password", Map.of(
                "currentPassword", PASSWORD, "newPassword", NEW_PASSWORD, "confirmNewPassword", NEW_PASSWORD));

        assertThat(change.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(email, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Should keep the current password when it is informed incorrectly")
    void shouldKeepPasswordWhenCurrentIsIncorrect() {
        String email = "password-wrong@example.com";
        String token = registerAndLogin(email).token();

        ResponseEntity<JsonNode> change = post(token, "/users/password", Map.of(
                "currentPassword", "wrong-password", "newPassword", NEW_PASSWORD, "confirmNewPassword", NEW_PASSWORD));

        assertThat(change.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(change.getBody().get("message").asString()).isEqualTo("Current password is incorrect");
        assertThat(login(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login(email, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<JsonNode> login(String email, String password) {
        return http.post().uri("/api/v1/auth/login")
                .body(Map.of("email", email, "password", password))
                .retrieve().toEntity(JsonNode.class);
    }
}
