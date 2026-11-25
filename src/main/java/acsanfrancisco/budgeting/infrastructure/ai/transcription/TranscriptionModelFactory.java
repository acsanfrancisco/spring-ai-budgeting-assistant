package acsanfrancisco.budgeting.infrastructure.ai.transcription;

import acsanfrancisco.budgeting.domain.aiconfiguration.TranscriptionProvider;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class TranscriptionModelFactory {

    public TranscriptionModel create(
            TranscriptionProvider provider,
            String model,
            String apiKey) {
        Assert.notNull(provider, "Provider must not be null");
        return switch (provider){
            case OPENAI -> createOpenAi(model, apiKey);
        };
    }

    private TranscriptionModel createOpenAi(String model, String apiKey) {
        Assert.hasText(model, "Model must not be empty");
        Assert.hasText(apiKey, "ApiKey must not be empty");

        OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
                .model(model)
                .apiKey(apiKey)
                .build();
        return OpenAiAudioTranscriptionModel.builder()
                .options(options)
                .build();
    }
}
