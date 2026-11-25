package acsanfrancisco.budgeting.infrastructure.ai;

import acsanfrancisco.budgeting.application.exception.ConversationInProgressException;
import acsanfrancisco.budgeting.application.exception.AiProviderException;
import acsanfrancisco.budgeting.application.exception.IdempotencyConflictException;
import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.output.TransactionOutput;
import acsanfrancisco.budgeting.application.usecase.aiconversation.GetOrCreateAiConversationUseCase;
import acsanfrancisco.budgeting.application.usecase.budget.GetBudgetLimitStatusUseCase;
import acsanfrancisco.budgeting.application.usecase.budget.PersistBudgetLimitUseCase;
import acsanfrancisco.budgeting.application.usecase.transaction.*;
import acsanfrancisco.budgeting.domain.aiconfiguration.*;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.transaction.TransactionRequestKey;
import acsanfrancisco.budgeting.domain.user.*;
import acsanfrancisco.budgeting.infrastructure.ai.chat.AssistantChatClient;
import acsanfrancisco.budgeting.infrastructure.ai.tools.BudgetLimitTools;
import acsanfrancisco.budgeting.infrastructure.ai.tools.TransactionTools;
import acsanfrancisco.budgeting.application.input.CreateTransactionInput;
import acsanfrancisco.budgeting.infrastructure.ai.chat.ChatModelFactory;
import acsanfrancisco.budgeting.infrastructure.ai.transcription.TranscriptionModelFactory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import acsanfrancisco.budgeting.infrastructure.config.EncryptionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockMakers;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.io.InterruptedIOException;
import com.google.genai.errors.ClientException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssistantServiceTest {
    private final AiConfigurationRepository configurations = mock(AiConfigurationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ChatModelFactory chatFactory = classMock(ChatModelFactory.class);
    private final TranscriptionModelFactory transcriptionFactory = classMock(TranscriptionModelFactory.class);
    private final TranscriptionModel transcription = mock(TranscriptionModel.class);
    private final ChatModel model = mock(ChatModel.class);
    private final GetOrCreateAiConversationUseCase conversations = classMock(GetOrCreateAiConversationUseCase.class);
    private final PersistTransactionUseCase persist = classMock(PersistTransactionUseCase.class);
    private final BytesEncryptor encryptor = new EncryptionConfig().apiKeyEncryptor("test-only-password", "0123456789abcdef");
    private final ChatMemory memory = MessageWindowChatMemory.builder().chatMemoryRepository(new InMemoryChatMemoryRepository()).maxMessages(15).build();
    private final Resource audio = new ByteArrayResource(new byte[]{1, 2, 3});
    private AssistantService service;
    private UserId userId;
    private User user;
    private String conversationId;

    @BeforeEach
    void setUp() {
        user = new User("Test User", "user@example.com", "encoded-password");
        userId = user.getId();
        conversationId = UUID.randomUUID().toString();
        when(configurations.findById(userId)).thenReturn(Optional.of(configuration(userId, TranscriptionProvider.OPENAI)));
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(transcriptionFactory.create(any(), any(), any())).thenReturn(transcription);
        when(chatFactory.create(any(), any(), any())).thenReturn(model);
        var transactionTools = new TransactionTools(persist, classMock(FindTransactionsByCategoryAndPeriodUseCase.class),
                classMock(FindMonthlyExpensesByCategoryUseCase.class), classMock(GetMonthlySummaryUseCase.class));
        var budgetLimitTools = new BudgetLimitTools(classMock(PersistBudgetLimitUseCase.class),
                classMock(GetBudgetLimitStatusUseCase.class));
        var chatClient = new AssistantChatClient(chatFactory, memory, transactionTools, budgetLimitTools);
        ReflectionTestUtils.setField(chatClient, "prompt", new ClassPathResource("prompts/assistant_prompt.st"));
        service = new AssistantService(configurations, encryptor, chatClient, transcriptionFactory, conversations, persist);
    }

    @Test
    void textOnlyConfigurationAcceptsTextAndRejectsAudioBeforeTranscription() {
        when(configurations.findById(userId)).thenReturn(Optional.of(new UserAiConfiguration(userId,
                ChatProvider.GEMINI, "model", encrypted("key"), null, null, null)));
        stubConversation("unused");
        assertThat(service.executeText("Meu saldo", userId)).isEqualTo("Despesa registrada");
        assertThatThrownBy(() -> service.execute(audio, userId)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(transcriptionFactory);
    }

    @Test
    void transcriptionOnlyConfigurationRejectsAssistantRequestsBeforeCallingProviders() {
        when(configurations.findById(userId)).thenReturn(Optional.of(new UserAiConfiguration(userId,
                null, null, null, TranscriptionProvider.OPENAI, "speech", encrypted("key"))));
        assertThatThrownBy(() -> service.executeText("Meu saldo", userId)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.execute(audio, userId)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(chatFactory, conversations, transcriptionFactory, transcription);
    }

    private UserAiConfiguration configuration(UserId owner, TranscriptionProvider provider) {
        return new UserAiConfiguration(owner, ChatProvider.GEMINI, "test-chat-model", encrypted("chat-test-key"),
                provider, provider == TranscriptionProvider.OPENAI ? "test-speech-model" : null,
                provider == TranscriptionProvider.OPENAI ? encrypted("speech-test-key") : null);
    }

    private String encrypted(String value) {
        return Base64.getEncoder().encodeToString(encryptor.encrypt(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static <T> T classMock(Class<T> type) {
        return mock(type, withSettings().mockMaker(MockMakers.SUBCLASS));
    }

    private void stubConversation(String text) {
        when(transcription.transcribe(any(Resource.class))).thenReturn(text);
        when(conversations.execute(userId)).thenReturn(UUID.fromString(conversationId));
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("Despesa registrada")))));
    }

    @Test
    @DisplayName("Should stop before model creation when user has no AI configuration")
    void shouldRejectMissingConfiguration() {
        when(configurations.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(audio, userId))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Ai configuration not found");
        verifyNoInteractions(users, transcriptionFactory, chatFactory, transcription, conversations, model, persist);
    }

    @Test
    @DisplayName("Should decrypt the authenticated user's configured model credentials")
    void shouldUseIndividualConfigurationAndDecryptedCredentials() {
        when(configurations.findById(userId)).thenReturn(Optional.of(new UserAiConfiguration(userId,
                ChatProvider.OPENAI, "individual-chat-model", encrypted("individual-chat-key"),
                TranscriptionProvider.OPENAI, "individual-speech-model", encrypted("individual-speech-key"))));
        stubConversation("Meu saldo");

        service.execute(audio, userId);

        verify(configurations).findById(userId);
        verify(transcriptionFactory).create(TranscriptionProvider.OPENAI, "individual-speech-model", "individual-speech-key");
        verify(chatFactory).create(ChatProvider.OPENAI, "individual-chat-model", "individual-chat-key");
    }

    @Test
    @DisplayName("Should transcribe file and use authenticated user in tool context")
    void shouldTranscribeFileAndUseAuthenticatedUserInToolContext() throws Exception {
        stubConversation("Registre quarenta reais de supermercado");
        assertThat(service.execute(audio, userId)).isEqualTo("Despesa registrada");

        var uploaded = ArgumentCaptor.forClass(Resource.class);
        verify(transcription).transcribe(uploaded.capture());
        assertThat(uploaded.getValue().getContentAsByteArray()).isEqualTo(audio.getContentAsByteArray());
        verify(conversations).execute(userId);
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getUserMessage().getText()).isEqualTo("Registre quarenta reais de supermercado");
        var options = (ToolCallingChatOptions) prompt.getValue().getOptions();
        assertThat(options.getToolContext()).containsEntry("userId", userId);
        assertThat(options.getToolCallbacks()).extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrder("persist-transaction", "persist-budget-limit",
                        "list-transaction-by-category-and-period", "list-monthly-expenses-by-category",
                        "get-monthly-summary", "get-budget-limit-status");
        assertThat(memory.get(conversationId)).extracting(message -> message.getText())
                .containsExactly("Registre quarenta reais de supermercado", "Despesa registrada");
    }

    @Test
    @DisplayName("Should execute registered tool and send JSON result back to model")
    void shouldExecuteRegisteredToolAndSendJsonResultBackToModel() throws Exception {
        stubConversation("Registre quarenta reais de supermercado no dia quinze de novembro de 2026 com a descrição Groceries");
        UUID transactionId = UUID.randomUUID();
        var input = new CreateTransactionInput("Groceries", new BigDecimal("40"), Category.GROCERIES,
                LocalDate.of(2026, 11, 15));
        when(persist.execute(eq(input), eq(userId), isNull()))
                .thenReturn(new TransactionOutput(transactionId, "Groceries", new BigDecimal("40"),
                        "GROCERIES", LocalDate.of(2026, 11, 15), "EXPENSE"));
        var toolRequest = AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("call-1", "function", "persist-transaction", """
                        {"description":"Groceries","amount":40,"category":"GROCERIES","date":"2026-11-15"}
                        """))).build();
        when(model.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(toolRequest))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("Despesa registrada")))));

        assertThat(service.execute(audio, userId)).isEqualTo("Despesa registrada");

        // The tool adapter converted the model's JSON and took the user from the ToolContext.
        verify(persist).execute(eq(input), eq(userId), isNull());
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        var toolResult = prompts.getAllValues().getLast().getInstructions().stream()
                .filter(ToolResponseMessage.class::isInstance).map(ToolResponseMessage.class::cast)
                .findFirst().orElseThrow().getResponses().getFirst();
        assertThat(toolResult.id()).isEqualTo("call-1");
        assertThat(new tools.jackson.databind.ObjectMapper().readTree(toolResult.responseData())
                .get("id").asText()).isEqualTo(transactionId.toString());
    }

    @Test
    void sendsExpenseDescriptionInstructionsInTheSystemPrompt() {
        stubConversation("unused");
        service.executeText("Cria uma despesa de 18,00, gastei com transporte, dia 18/01/2027", userId);

        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getSystemMessage().getText())
                .contains("pergunte se a pessoa quer adicionar uma descrição e aguarde a resposta");
    }

    static Stream<RuntimeException> failuresAfterSaving() {
        return Stream.of(new IllegalStateException(new InterruptedIOException("timeout")),
                new ClientException(429, "RESOURCE_EXHAUSTED", "Slow down"),
                new ClientException(503, "UNAVAILABLE", "Unavailable"));
    }

    @ParameterizedTest
    @MethodSource("failuresAfterSaving")
    void shouldReportSavedTransactionAndReplayWithoutCallingModel(RuntimeException providerFailure) {
        stubConversation("unused");
        UUID key = UUID.randomUUID();
        var saved = new TransactionOutput(UUID.randomUUID(), "Mercado", new BigDecimal("40"),
                "GROCERIES", LocalDate.of(2026, 11, 15), "EXPENSE");
        when(persist.findRegisteredTransaction(eq(userId), any()))
                .thenReturn(Optional.empty()).thenReturn(Optional.of(saved));
        var input = new CreateTransactionInput("Mercado", new BigDecimal("40"), Category.GROCERIES,
                LocalDate.of(2026, 11, 15));
        when(persist.execute(eq(input), eq(userId), any())).thenReturn(saved);
        var toolRequest = AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("call-1", "function", "persist-transaction", """
                        {"description":"Mercado","amount":40,"category":"GROCERIES","date":"2026-11-15"}
                        """))).build();
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(toolRequest))))
                .thenThrow(providerFailure);

        assertThatThrownBy(() -> service.executeText("Registre R$ 40 no mercado, sem descrição", userId, key))
                .isInstanceOfSatisfying(AiProviderException.class,
                        failure -> assertThat(failure.getTransactionId()).isEqualTo(saved.id()));
        assertThat(service.executeText("Registre R$ 40 no mercado, sem descrição", userId, key)).contains(saved.id().toString());

        var savedKey = ArgumentCaptor.forClass(TransactionRequestKey.class);
        verify(persist).execute(eq(input), eq(userId), savedKey.capture());
        var requestKey = savedKey.getValue();
        assertThat(requestKey.value()).isEqualTo(key);
        assertThat(requestKey.messageHash()).matches("[0-9a-f]{64}");
        verify(persist, times(3)).findRegisteredTransaction(userId, requestKey);
        verify(model, times(2)).call(any(Prompt.class));
    }

    @Test
    void shouldPropagateIdempotencyConflictBeforeCallingModel() {
        when(persist.findRegisteredTransaction(eq(userId), any())).thenThrow(new IdempotencyConflictException());

        assertThatThrownBy(() -> service.executeText("Outra mensagem", userId, UUID.randomUUID()))
                .isInstanceOf(IdempotencyConflictException.class);
        verifyNoInteractions(model, conversations);
    }

    @Test
    @DisplayName("Should replay a resent recording with the same key without transcribing again")
    void shouldReplayResentRecordingWithoutTranscribing() throws Exception {
        UUID key = UUID.randomUUID();
        var saved = new TransactionOutput(UUID.randomUUID(), "Mercado", new BigDecimal("40"),
                "GROCERIES", LocalDate.of(2026, 11, 15), "EXPENSE");
        when(persist.findRegisteredTransaction(eq(userId), any())).thenReturn(Optional.of(saved));

        assertThat(service.execute(audio, userId, key)).isEqualTo("Lançamento já registrado. ID: " + saved.id());

        var requestKey = ArgumentCaptor.forClass(TransactionRequestKey.class);
        verify(persist).findRegisteredTransaction(eq(userId), requestKey.capture());
        assertThat(requestKey.getValue().value()).isEqualTo(key);
        assertThat(requestKey.getValue().messageHash()).isEqualTo(sha256(audio.getContentAsByteArray()));
        verifyNoInteractions(transcriptionFactory, transcription, conversations, model);
    }

    @Test
    @DisplayName("Should reject a key reused with another recording before transcribing")
    void shouldRejectKeyReusedWithAnotherRecordingBeforeTranscribing() {
        when(persist.findRegisteredTransaction(eq(userId), any())).thenThrow(new IdempotencyConflictException());

        assertThatThrownBy(() -> service.execute(audio, userId, UUID.randomUUID()))
                .isInstanceOf(IdempotencyConflictException.class);
        verifyNoInteractions(transcriptionFactory, transcription, conversations, model);
    }

    @Test
    @DisplayName("Should save a voice transaction under the key and report it when the provider fails afterwards")
    void shouldReportVoiceTransactionSavedBeforeProviderFailure() throws Exception {
        stubConversation("Registre R$ 40 no mercado no dia 15/11/2026, sem descrição");
        UUID key = UUID.randomUUID();
        var saved = new TransactionOutput(UUID.randomUUID(), "Mercado", new BigDecimal("40"),
                "GROCERIES", LocalDate.of(2026, 11, 15), "EXPENSE");
        var input = new CreateTransactionInput("Mercado", new BigDecimal("40"), Category.GROCERIES,
                LocalDate.of(2026, 11, 15));
        when(persist.findRegisteredTransaction(eq(userId), any()))
                .thenReturn(Optional.empty()).thenReturn(Optional.of(saved));
        when(persist.execute(eq(input), eq(userId), any())).thenReturn(saved);
        var toolRequest = AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("call-1", "function", "persist-transaction", """
                        {"description":"Mercado","amount":40,"category":"GROCERIES","date":"2026-11-15"}
                        """))).build();
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(toolRequest))))
                .thenThrow(new ClientException(503, "UNAVAILABLE", "Unavailable"));

        assertThatThrownBy(() -> service.execute(audio, userId, key))
                .isInstanceOfSatisfying(AiProviderException.class,
                        failure -> assertThat(failure.getTransactionId()).isEqualTo(saved.id()));

        var savedKey = ArgumentCaptor.forClass(TransactionRequestKey.class);
        verify(persist).execute(eq(input), eq(userId), savedKey.capture());
        assertThat(savedKey.getValue().value()).isEqualTo(key);
        assertThat(savedKey.getValue().messageHash()).isEqualTo(sha256(audio.getContentAsByteArray()));
        verify(transcription).transcribe(audio);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    @DisplayName("Should include previous turn for same conversation")
    void shouldIncludePreviousTurnForSameConversation() throws Exception {
        stubConversation("Gastei quarenta reais no supermercado");
        service.execute(audio, userId);
        when(transcription.transcribe(any(Resource.class))).thenReturn("E quanto sobrou?");
        service.execute(audio, userId);

        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().getLast().getInstructions()).extracting(message -> message.getText())
                .contains("Gastei quarenta reais no supermercado", "Despesa registrada", "E quanto sobrou?");
    }

    @Test
    void shouldRejectOverlappingTurnAndKeepMemoryForRetry() throws Exception {
        when(conversations.execute(userId)).thenReturn(UUID.fromString(conversationId));
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        var modelEntered = new CountDownLatch(1);
        var releaseModel = new CountDownLatch(1);
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            modelEntered.countDown();
            assertThat(releaseModel.await(5, TimeUnit.SECONDS)).isTrue();
            return new ChatResponse(List.of(new Generation(new AssistantMessage("Primeira resposta"))));
        }).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Segunda resposta")))));

        var first = CompletableFuture.supplyAsync(() -> service.executeText("Primeira mensagem", userId));
        try {
            assertThat(modelEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service.executeText("Segunda mensagem", userId))
                    .isInstanceOf(ConversationInProgressException.class);
            assertThat(memory.get(conversationId)).extracting(message -> message.getText())
                    .containsExactly("Primeira mensagem");
        } finally {
            releaseModel.countDown();
        }
        assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("Primeira resposta");
        assertThat(service.executeText("Segunda mensagem", userId)).isEqualTo("Segunda resposta");
        assertThat(memory.get(conversationId)).extracting(message -> message.getText())
                .containsExactly("Primeira mensagem", "Primeira resposta", "Segunda mensagem", "Segunda resposta");
    }

    @Test
    @DisplayName("Should not include another user's history")
    void shouldNotIncludeAnotherUsersHistory() throws Exception {
        stubConversation("Minha despesa privada");
        service.execute(audio, userId);
        var otherUser = new UserId();
        when(configurations.findById(otherUser)).thenReturn(Optional.of(configuration(otherUser, TranscriptionProvider.OPENAI)));
        when(users.findById(otherUser)).thenReturn(Optional.of(new User(otherUser, "Other User", "other@example.com", "encoded-password")));
        when(conversations.execute(otherUser)).thenReturn(UUID.randomUUID());
        when(transcription.transcribe(any(Resource.class))).thenReturn("Meu saldo");
        service.execute(audio, otherUser);

        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().getLast().getInstructions()).extracting(message -> message.getText())
                .contains("Meu saldo").doesNotContain("Minha despesa privada", "Despesa registrada");
    }

    @Test
    @DisplayName("Should stop before creating conversation when transcription fails")
    void shouldStopBeforeCreatingConversationWhenTranscriptionFails() throws Exception {
        when(transcription.transcribe(any(Resource.class))).thenThrow(new IllegalArgumentException("Invalid audio"));
        assertThatThrownBy(() -> service.execute(audio, userId))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid audio");
        verifyNoInteractions(conversations, model);
    }

    @Test
    @DisplayName("Should propagate chat failure without saving assistant response or executing tools")
    void shouldPropagateChatFailureWithoutSavingAssistantResponseOrExecutingTools() {
        stubConversation("Meu saldo");
        var failure = new IllegalStateException("Model unavailable");
        when(model.call(any(Prompt.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.execute(audio, userId))
                .isSameAs(failure);
        verify(model).call(any(Prompt.class));
        verifyNoInteractions(persist);
        assertThat(memory.get(conversationId)).extracting(message -> message.getText()).containsExactly("Meu saldo");
        doReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Consegui responder")))))
                .when(model).call(any(Prompt.class));
        assertThat(service.executeText("Pode tentar de novo?", userId)).isEqualTo("Consegui responder");
    }

    @Test
    @DisplayName("Should stop before chat when conversation lookup fails")
    void shouldStopBeforeChatWhenConversationLookupFails() {
        when(transcription.transcribe(any(Resource.class))).thenReturn("Meu saldo");
        var failure = new IllegalStateException("Database unavailable");
        when(conversations.execute(userId)).thenThrow(failure);
        assertThatThrownBy(() -> service.execute(audio, userId))
                .isSameAs(failure);
        verifyNoInteractions(model);
    }

}
