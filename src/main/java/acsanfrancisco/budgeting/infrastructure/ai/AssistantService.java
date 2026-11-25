package acsanfrancisco.budgeting.infrastructure.ai;

import acsanfrancisco.budgeting.application.exception.AiProviderException;
import acsanfrancisco.budgeting.application.exception.ConversationInProgressException;
import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.usecase.aiconversation.GetOrCreateAiConversationUseCase;
import acsanfrancisco.budgeting.application.usecase.transaction.PersistTransactionUseCase;
import acsanfrancisco.budgeting.domain.aiconfiguration.AiConfigurationRepository;
import acsanfrancisco.budgeting.domain.aiconfiguration.TranscriptionProvider;
import acsanfrancisco.budgeting.domain.aiconfiguration.UserAiConfiguration;
import acsanfrancisco.budgeting.domain.transaction.TransactionRequestKey;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.infrastructure.ai.chat.AssistantChatClient;
import acsanfrancisco.budgeting.infrastructure.ai.transcription.TranscriptionModelFactory;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantService {

    private final Set<UUID> conversationsInProgress = ConcurrentHashMap.newKeySet();

    private final AiConfigurationRepository aiConfigurationRepository;

    private final BytesEncryptor encryptor;

    private final AssistantChatClient chatClient;

    private final TranscriptionModelFactory transcriptionModelFactory;

    private final GetOrCreateAiConversationUseCase getOrCreateAiConversationUseCase;

    private final PersistTransactionUseCase persistTransactionUseCase;

    public String execute(Resource audio, UserId userId) {
        return execute(audio, userId, null);
    }

    /**
     * With an Idempotency-Key, a resent recording returns the transaction it already created
     * instead of being transcribed and processed again. The key is bound to the audio bytes,
     * because a new transcription of the same recording may come out with different words.
     */
    public String execute(Resource audio, UserId userId, UUID key) {
        UserAiConfiguration configuration = findConfiguration(userId);

        if (configuration.getTranscriptionProvider() == null) {
            throw new ResourceNotFoundException("Transcription is not configured");
        }
        if (configuration.getChatProvider() == null) {
            throw new ResourceNotFoundException("Chat is required to process the transcribed request");
        }
        TransactionRequestKey requestKey = key == null ? null : new TransactionRequestKey(key, hashAudio(audio));
        Optional<String> replay = replayRegisteredTransaction(userId, requestKey);
        if (replay.isPresent()) {
            return replay.get();
        }
        TranscriptionModel transcriptionModel = transcriptionModelFactory.create(
                configuration.getTranscriptionProvider(),
                configuration.getTranscriptionModel(),
                decrypt(configuration.getTranscriptionApiKey()));

        TranscriptionProvider provider = configuration.getTranscriptionProvider();
        String text;
        long start = System.nanoTime();
        try {
            text = transcriptionModel.transcribe(audio);
        } catch (RuntimeException failure) {
            RuntimeException translated = AiProviderErrorTranslator.translate(failure, "transcription");
            log.warn("Transcription failed. provider={} reason={}", provider, AiProviderErrorTranslator.describe(translated));
            throw translated;
        }
        log.info("Transcription completed. provider={} durationMs={}", provider, (System.nanoTime() - start) / 1_000_000);
        return processMessage(text, userId, configuration, requestKey);
    }

    public String executeText(String message, UserId userId) {
        return executeText(message, userId, null);
    }

    public String executeText(String message, UserId userId, UUID key) {
        UserAiConfiguration configuration = findConfiguration(userId);
        if (configuration.getChatProvider() == null) {
            throw new ResourceNotFoundException("Text chat is not configured");
        }
        TransactionRequestKey requestKey = key == null ? null : new TransactionRequestKey(key, hashMessage(message));
        Optional<String> replay = replayRegisteredTransaction(userId, requestKey);
        if (replay.isPresent()) {
            return replay.get();
        }
        return processMessage(message.strip(), userId, configuration, requestKey);
    }

    private Optional<String> replayRegisteredTransaction(UserId userId, TransactionRequestKey key) {
        if (key == null) {
            return Optional.empty();
        }
        return persistTransactionUseCase.findRegisteredTransaction(userId, key)
                .map(saved -> "Lançamento já registrado. ID: " + saved.id());
    }

    private static String hashMessage(String message) {
        return HexFormat.of().formatHex(sha256().digest(message.getBytes(StandardCharsets.UTF_8)));
    }

    // Streams the upload (up to 25 MB) instead of loading it whole; the transcription reads it again.
    private static String hashAudio(Resource audio) {
        MessageDigest digest = sha256();
        try (InputStream in = new DigestInputStream(audio.getInputStream(), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the audio file", exception);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private UserAiConfiguration findConfiguration(UserId userId) {
        return aiConfigurationRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Ai configuration not found"));
    }

    private String processMessage(String text, UserId userId, UserAiConfiguration configuration,
            TransactionRequestKey key) {
        UUID conversationId = getOrCreateAiConversationUseCase.execute(userId);
        if (!conversationsInProgress.add(conversationId)) {
            throw new ConversationInProgressException();
        }
        try {
            return chatClient.reply(text, userId, configuration, conversationId, key, decrypt(configuration.getChatApiKey()));
        } catch (AiProviderException failure) {
            if (key != null) {
                persistTransactionUseCase.findRegisteredTransaction(userId, key)
                        .ifPresent(saved -> failure.setTransactionId(saved.id()));
            }
            throw failure;
        } finally {
            conversationsInProgress.remove(conversationId);
        }
    }

    private String decrypt(String encryptedApiKey) {
        byte[] encrypted = Base64.getDecoder().decode(encryptedApiKey);
        return new String(encryptor.decrypt(encrypted), StandardCharsets.UTF_8);

    }
}
