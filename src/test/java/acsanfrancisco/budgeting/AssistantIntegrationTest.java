package acsanfrancisco.budgeting;

import acsanfrancisco.budgeting.infrastructure.ai.transcription.TranscriptionModelFactory;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.LinkedMultiValueMap;
import acsanfrancisco.budgeting.domain.aiconfiguration.ChatProvider;
import acsanfrancisco.budgeting.infrastructure.ai.chat.ChatModelFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.mockito.MockMakers;
import org.mockito.Mockito;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fluxo do assistente com tudo real, exceto o provedor de chat: configuração de IA gravada
 * criptografada no banco, conversa e memória persistidas, ferramenta executada pelo
 * ChatClient e lançamento salvo no PostgreSQL. A idempotência depende da restrição
 * (user_id, idempotency_key) do banco e por isso só é provada aqui.
 */
@Import(AssistantIntegrationTest.ScriptedChatConfiguration.class)
class AssistantIntegrationTest extends HttpIntegrationTestSupport {

    private static final String CHAT_MODEL = "test-chat-model";
    private static final String CHAT_API_KEY = "user-chat-api-key";
    private static final String MESSAGE = "Registre R$ 40 no mercado no dia 15/11/2026, sem descrição";

    @Autowired
    ChatModelFactory chatModelFactory;

    @Autowired
    TranscriptionModelFactory transcriptionModelFactory;

    @Autowired
    JdbcTemplate jdbc;

    private final ChatModel provider = mock(ChatModel.class);
    private final TranscriptionModel transcription = mock(TranscriptionModel.class);
    private final ObjectMapper json = new ObjectMapper();
    private Account account;

    @BeforeEach
    void setUp() {
        Mockito.reset(chatModelFactory, transcriptionModelFactory);
        account = registerAndLogin("assistant-" + UUID.randomUUID() + "@example.com");
        ResponseEntity<JsonNode> configuration = post(account.token(), "/users/ai-config",
                Map.of("chatProvider", "GEMINI", "chatModel", CHAT_MODEL, "chatApiKey", CHAT_API_KEY,
                        "transcriptionProvider", "OPENAI", "transcriptionModel", "test-speech-model",
                        "transcriptionApiKey", "user-speech-api-key"));
        assertThat(configuration.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(configuration.getBody().has("chatApiKey")).isFalse();

        when(chatModelFactory.create(any(), any(), any())).thenReturn(provider);
        when(provider.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(transcriptionModelFactory.create(any(), any(), any())).thenReturn(transcription);
        // Reading the upload here proves it is still readable after the service hashed it.
        when(transcription.transcribe(any(Resource.class))).thenAnswer(invocation -> {
            assertThat(invocation.getArgument(0, Resource.class).getContentAsByteArray()).isNotEmpty();
            return MESSAGE;
        });
    }

    @Test
    @DisplayName("Should save the transaction requested through the tool and replay it for the same Idempotency-Key")
    void shouldPersistToolTransactionAndReplayIdempotentRequest() throws Exception {
        when(provider.call(any(Prompt.class))).thenReturn(toolCall(), reply("Despesa registrada"));
        UUID key = UUID.randomUUID();

        ResponseEntity<String> first = sendText(key, MESSAGE);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isEqualTo("Despesa registrada");
        JsonNode saved = singleGroceriesTransaction(account.token());
        assertThat(saved.get("description").asString()).isEqualTo("Mercado");
        assertThat(saved.get("date").asString()).isEqualTo("15/11/2026");
        assertThat(saved.get("type").asString()).isEqualTo("EXPENSE");
        assertMoney(saved, "amount", "40.00");
        String transactionId = saved.get("id").asString();

        // A chave salva no banco é a do usuário, criptografada, e volta descriptografada para o provedor.
        String storedKey = jdbc.queryForObject("SELECT chat_api_key FROM tb_user_ai_configuration WHERE user_id = ?",
                String.class, UUID.fromString(account.id()));
        assertThat(storedKey).isNotBlank().doesNotContain(CHAT_API_KEY);
        verify(chatModelFactory).create(ChatProvider.GEMINI, CHAT_MODEL, CHAT_API_KEY);

        // Conversa ativa e memória gravadas no PostgreSQL.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tb_ai_conversation WHERE user_id = ? AND status = 'ACTIVE'",
                Integer.class, UUID.fromString(account.id()))).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM SPRING_AI_CHAT_MEMORY m
                JOIN tb_ai_conversation c ON m.conversation_id = c.id::text
                WHERE c.user_id = ?""", Integer.class, UUID.fromString(account.id()))).isPositive();

        ResponseEntity<String> replay = sendText(key, MESSAGE);

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody()).isEqualTo("Lançamento já registrado. ID: " + transactionId);
        assertThat(singleGroceriesTransaction(account.token()).get("id").asString()).isEqualTo(transactionId);
        verify(provider, times(2)).call(any(Prompt.class));

        ResponseEntity<String> reusedKey = sendText(key, "Registre R$ 99 de transporte");

        assertThat(reusedKey.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(singleGroceriesTransaction(account.token()).get("id").asString()).isEqualTo(transactionId);
        verify(provider, times(2)).call(any(Prompt.class));

        String otherUser = registerAndLogin("assistant-other-" + UUID.randomUUID() + "@example.com").token();
        assertThat(get(otherUser, "/transactions?category=GROCERIES").get("totalElements").asLong()).isZero();
    }

    @Test
    @DisplayName("Should report the saved transaction when the provider fails after the tool ran, then replay it")
    void shouldReportSavedTransactionAfterProviderFailure() throws Exception {
        when(provider.call(any(Prompt.class)))
                .thenReturn(toolCall())
                .thenThrow(new IllegalStateException(new InterruptedIOException("timeout")));
        UUID key = UUID.randomUUID();

        ResponseEntity<String> failure = sendText(key, MESSAGE);

        assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(json.readTree(failure.getBody()).get("code").asString()).isEqualTo("AI_PROVIDER_TIMEOUT");
        String transactionId = singleGroceriesTransaction(account.token()).get("id").asString();
        assertThat(failure.getHeaders().getFirst("X-Transaction-Id")).isEqualTo(transactionId);

        ResponseEntity<String> retry = sendText(key, MESSAGE);

        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getBody()).isEqualTo("Lançamento já registrado. ID: " + transactionId);
        assertThat(singleGroceriesTransaction(account.token()).get("id").asString()).isEqualTo(transactionId);
        verify(provider, times(2)).call(any(Prompt.class));
    }

    @Test
    @DisplayName("Should replay a resent recording with the same Idempotency-Key and reject the key with another recording")
    void shouldReplayResentRecordingAndRejectKeyWithAnotherRecording() {
        when(provider.call(any(Prompt.class))).thenReturn(toolCall(), reply("Despesa registrada"));
        UUID key = UUID.randomUUID();
        byte[] recording = {1, 2, 3, 4};

        ResponseEntity<String> first = sendVoice(key, recording);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isEqualTo("Despesa registrada");
        String transactionId = singleGroceriesTransaction(account.token()).get("id").asString();

        ResponseEntity<String> resent = sendVoice(key, recording);

        assertThat(resent.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resent.getBody()).isEqualTo("Lançamento já registrado. ID: " + transactionId);

        ResponseEntity<String> otherRecording = sendVoice(key, new byte[]{9, 8, 7, 6});

        assertThat(otherRecording.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(singleGroceriesTransaction(account.token()).get("id").asString()).isEqualTo(transactionId);
        verify(transcription, times(1)).transcribe(any(Resource.class));
        verify(provider, times(2)).call(any(Prompt.class));
    }

    @Test
    @DisplayName("Should report the voice transaction saved before a provider failure, then replay it")
    void shouldReportVoiceTransactionSavedBeforeProviderFailure() throws Exception {
        when(provider.call(any(Prompt.class)))
                .thenReturn(toolCall())
                .thenThrow(new IllegalStateException(new InterruptedIOException("timeout")));
        UUID key = UUID.randomUUID();
        byte[] recording = {1, 2, 3, 4};

        ResponseEntity<String> failure = sendVoice(key, recording);

        assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        String transactionId = singleGroceriesTransaction(account.token()).get("id").asString();
        assertThat(failure.getHeaders().getFirst("X-Transaction-Id")).isEqualTo(transactionId);

        ResponseEntity<String> retry = sendVoice(key, recording);

        assertThat(retry.getBody()).isEqualTo("Lançamento já registrado. ID: " + transactionId);
        verify(transcription, times(1)).transcribe(any(Resource.class));
    }

    @Test
    @DisplayName("Should return the real JSON error when the client accepts only text/plain, as Swagger does")
    void shouldReturnJsonErrorsToTextPlainClients() throws Exception {
        String unconfigured = registerAndLogin("assistant-plain-" + UUID.randomUUID() + "@example.com").token();

        ResponseEntity<String> missingConfiguration = sendTextAcceptingPlain(unconfigured, "Quanto gastei?");

        assertThat(missingConfiguration.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missingConfiguration.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(json.readTree(missingConfiguration.getBody()).get("message").asString())
                .isEqualTo("Ai configuration not found");

        ResponseEntity<String> blankMessage = sendTextAcceptingPlain(unconfigured, "");

        assertThat(blankMessage.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(json.readTree(blankMessage.getBody()).get("message").asString()).isEqualTo("Validation Failed.");
    }

    private ResponseEntity<String> sendTextAcceptingPlain(String token, String message) {
        return http.post().uri("/api/v1/assistant/text")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .accept(MediaType.TEXT_PLAIN)
                .body(Map.of("message", message))
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> sendText(UUID key, String message) {
        return http.post().uri("/api/v1/assistant/text")
                .header(HttpHeaders.AUTHORIZATION, bearer(account.token()))
                .header("Idempotency-Key", key.toString())
                .body(Map.of("message", message))
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> sendVoice(UUID key, byte[] recording) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ByteArrayResource(recording) {
            @Override
            public String getFilename() {
                return "recording.webm";
            }
        });
        return http.post().uri("/api/v1/assistant/voice")
                .header(HttpHeaders.AUTHORIZATION, bearer(account.token()))
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve().toEntity(String.class);
    }

    private JsonNode singleGroceriesTransaction(String token) {
        JsonNode page = get(token, "/transactions?category=GROCERIES");
        assertThat(page.get("totalElements").asLong()).isEqualTo(1);
        return page.get("content").get(0);
    }

    private static ChatResponse toolCall() {
        var request = AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("call-1", "function", "persist-transaction", """
                        {"description":"Mercado","amount":40,"category":"GROCERIES","date":"2026-11-15"}
                        """))).build();
        return new ChatResponse(List.of(new Generation(request)));
    }

    private static ChatResponse reply(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    /** Substitui só a criação do modelo do provedor; o restante do fluxo do assistente é o real. */
    @TestConfiguration(proxyBeanMethods = false)
    static class ScriptedChatConfiguration {
        @Bean @Primary
        ChatModelFactory scriptedChatModelFactory() {
            return mock(ChatModelFactory.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }

        @Bean @Primary
        TranscriptionModelFactory scriptedTranscriptionModelFactory() {
            return mock(TranscriptionModelFactory.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
    }
}
