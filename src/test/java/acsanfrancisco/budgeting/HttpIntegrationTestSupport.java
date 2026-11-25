package acsanfrancisco.budgeting;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Base dos testes que exercitam a aplicação como um cliente HTTP: servidor real em porta
 * aleatória e PostgreSQL vazio com as migrations do Flyway. As credenciais de teste vêm de
 * {@code src/test/resources/config/application.properties}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(HttpIntegrationTestSupport.OfflineAiConfiguration.class)
abstract class HttpIntegrationTestSupport {

    static final String PASSWORD = "secret123";

    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @LocalServerPort
    int port;

    RestClient http;

    @BeforeEach
    void createHttpClient() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    record Account(String id, String token) {}

    Account registerAndLogin(String email) {
        ResponseEntity<JsonNode> user = http.post().uri("/api/v1/users")
                .body(Map.of("fullName", "Integration User", "email", email, "password", PASSWORD))
                .retrieve().toEntity(JsonNode.class);
        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(user.getBody().get("email").asString()).isEqualTo(email);
        assertThat(user.getBody().has("password")).isFalse();

        ResponseEntity<JsonNode> login = http.post().uri("/api/v1/auth/login")
                .body(Map.of("email", email, "password", PASSWORD))
                .retrieve().toEntity(JsonNode.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = login.getBody().get("token").asString();
        assertThat(token).isNotBlank();
        return new Account(user.getBody().get("id").asString(), token);
    }

    ResponseEntity<JsonNode> post(String token, String uri, Map<String, ?> body) {
        return http.post().uri("/api/v1" + uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .body(body)
                .retrieve().toEntity(JsonNode.class);
    }

    JsonNode get(String token, String uri) {
        ResponseEntity<JsonNode> response = http.get().uri("/api/v1" + uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve().toEntity(JsonNode.class);
        assertThat(response.getStatusCode()).as("GET %s", uri).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    static String bearer(String token) {
        return token.startsWith("Bearer ") ? token : "Bearer " + token;
    }

    static void assertMoney(JsonNode node, String field, String expected) {
        assertThat(node.get(field).decimalValue()).as(field).isEqualByComparingTo(new BigDecimal(expected));
    }

    /** O chat é criado por usuário em ChatModelFactory; este bean só satisfaz a montagem do contexto. */
    @TestConfiguration(proxyBeanMethods = false)
    static class OfflineAiConfiguration {
        @Bean ChatModel chatModel() { return mock(ChatModel.class); }
        @Bean ChatClient.Builder chatClientBuilder(ChatModel model) { return ChatClient.builder(model); }
    }
}
