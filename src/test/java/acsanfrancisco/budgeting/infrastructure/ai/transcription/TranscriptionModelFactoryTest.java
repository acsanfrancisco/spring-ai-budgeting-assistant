package acsanfrancisco.budgeting.infrastructure.ai.transcription;

import acsanfrancisco.budgeting.domain.aiconfiguration.TranscriptionProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranscriptionModelFactoryTest {

    private final TranscriptionModelFactory factory = new TranscriptionModelFactory();

    @Test
    @DisplayName("Should create an OpenAI transcription model with the user's model and key")
    void shouldCreateOpenAiTranscription() {
        assertThat(factory.create(TranscriptionProvider.OPENAI, "whisper-1", "test-key"))
                .isInstanceOf(OpenAiAudioTranscriptionModel.class);
    }

    @Test
    @DisplayName("Should require provider, model and key")
    void shouldRequireProviderModelAndKey() {
        assertThatThrownBy(() -> factory.create(null, "whisper-1", "test-key")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.create(TranscriptionProvider.OPENAI, " ", "test-key")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.create(TranscriptionProvider.OPENAI, "whisper-1", null)).isInstanceOf(IllegalArgumentException.class);
    }
}
