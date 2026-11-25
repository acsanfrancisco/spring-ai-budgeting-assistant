package acsanfrancisco.budgeting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercita os limites de multipart do Tomcat configurados em application.properties,
 * que o MockMvc não aplica: o áudio acima de 25 MB recebe 413 com a mensagem da API,
 * sem a conexão ser cortada, e um arquivo dentro do limite chega ao assistente.
 */
class AudioUploadLimitIntegrationTest extends HttpIntegrationTestSupport {

    private static final int MB = 1024 * 1024;

    private String token;

    @BeforeEach
    void authenticate() {
        token = registerAndLogin("upload-" + System.nanoTime() + "@example.com").token();
    }

    @Test
    @DisplayName("Should reject audio larger than 25 MB with 413 and the API error message")
    void shouldRejectOversizedAudio() {
        ResponseEntity<JsonNode> response = upload(26 * MB);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody().get("message").asString()).isEqualTo("Uploaded file exceeds the 25 MB limit");
    }

    @Test
    @DisplayName("Should accept audio within the limit and hand it to the assistant")
    void shouldAcceptAudioWithinLimit() {
        ResponseEntity<JsonNode> response = upload(24 * MB);

        // Sem configuração de IA, o assistente responde 404: o arquivo passou pelo limite de upload.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("message").asString()).isEqualTo("Ai configuration not found");
    }

    @Test
    @DisplayName("Should accept audio of exactly 25 MB, as documented")
    void shouldAcceptAudioOfExactly25Mb() {
        ResponseEntity<JsonNode> response = upload(25 * MB);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("message").asString()).isEqualTo("Ai configuration not found");
    }

    @Test
    @DisplayName("Should reject audio one byte above 25 MB")
    void shouldRejectAudioOneByteAboveLimit() {
        assertThat(upload(25 * MB + 1).getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    }

    private ResponseEntity<JsonNode> upload(int size) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ByteArrayResource(new byte[size]) {
            @Override
            public String getFilename() {
                return "audio.wav";
            }
        });
        return http.post().uri("/api/v1/assistant/voice")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve().toEntity(JsonNode.class);
    }
}
