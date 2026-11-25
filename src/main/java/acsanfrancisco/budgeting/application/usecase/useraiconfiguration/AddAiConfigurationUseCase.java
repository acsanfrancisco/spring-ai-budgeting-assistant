package acsanfrancisco.budgeting.application.usecase.useraiconfiguration;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.input.AiConfigurationInput;
import acsanfrancisco.budgeting.application.output.AiConfigurationOutput;
import acsanfrancisco.budgeting.domain.aiconfiguration.AiConfigurationRepository;
import acsanfrancisco.budgeting.domain.aiconfiguration.TranscriptionProvider;
import acsanfrancisco.budgeting.domain.aiconfiguration.UserAiConfiguration;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AddAiConfigurationUseCase {

    private final BytesEncryptor encryptor;
    private final UserRepository userRepository;
    private final AiConfigurationRepository aiConfigurationRepository;

    @Transactional
    public AiConfigurationOutput execute(AiConfigurationInput input, UserId userId) {
        userRepository.lockById(userId);
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserAiConfiguration configuration = new UserAiConfiguration(
                userId,
                input.chatProvider(),
                input.chatModel(),
                input.chatApiKey(),
                input.transcriptionProvider(),
                input.transcriptionModel(),
                input.transcriptionApiKey());

        if (input.chatProvider() != null) {
            configuration.setChatApiKey(encrypt(configuration.getChatApiKey()));
        }

        if (configuration.getTranscriptionProvider() == TranscriptionProvider.OPENAI) {
            configuration.setTranscriptionApiKey(
                    encrypt(configuration.getTranscriptionApiKey())
            );
        }

        if (input.chatProvider() == null || input.transcriptionProvider() == null) {
            var existing = aiConfigurationRepository.findById(userId);
            if (existing.isPresent()) {
                UserAiConfiguration chat = input.chatProvider() == null ? existing.get() : configuration;
                UserAiConfiguration voice = input.transcriptionProvider() == null ? existing.get() : configuration;
                configuration = new UserAiConfiguration(userId, chat.getChatProvider(), chat.getChatModel(),
                        chat.getChatApiKey(), voice.getTranscriptionProvider(), voice.getTranscriptionModel(),
                        voice.getTranscriptionApiKey());
            }
        }
        UserAiConfiguration saved = aiConfigurationRepository.save(configuration);
        return AiConfigurationOutput.from(saved);
    }

    private String encrypt(String apiKey) {
        return Base64.getEncoder().encodeToString(
                encryptor.encrypt(apiKey.getBytes(StandardCharsets.UTF_8))
        );
    }
}
