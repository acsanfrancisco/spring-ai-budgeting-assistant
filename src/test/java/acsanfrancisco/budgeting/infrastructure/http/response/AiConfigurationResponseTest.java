package acsanfrancisco.budgeting.infrastructure.http.response;

import acsanfrancisco.budgeting.application.output.AiConfigurationOutput;
import acsanfrancisco.budgeting.domain.aiconfiguration.ChatProvider;
import acsanfrancisco.budgeting.domain.aiconfiguration.TranscriptionProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiConfigurationResponseTest {
    @Test
    void representsEitherModalityIndependently() {
        assertEquals(new AiConfigurationResponse("GEMINI", "chat", null, null),
                AiConfigurationResponse.from(new AiConfigurationOutput(ChatProvider.GEMINI, "chat", null, null)));
        assertEquals(new AiConfigurationResponse(null, null, "OPENAI", "whisper-1"),
                AiConfigurationResponse.from(new AiConfigurationOutput(null, null, TranscriptionProvider.OPENAI, "whisper-1")));
    }
}
