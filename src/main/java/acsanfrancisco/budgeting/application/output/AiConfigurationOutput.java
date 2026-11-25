package acsanfrancisco.budgeting.application.output;

import acsanfrancisco.budgeting.domain.aiconfiguration.UserAiConfiguration;
import acsanfrancisco.budgeting.domain.aiconfiguration.ChatProvider;
import acsanfrancisco.budgeting.domain.aiconfiguration.TranscriptionProvider;

public record AiConfigurationOutput(ChatProvider chatProvider, String chatModel, TranscriptionProvider transcriptionProvider,
                                    String transcriptionModel) {

    public static AiConfigurationOutput from(UserAiConfiguration configuration) {
        return new AiConfigurationOutput(
                configuration.getChatProvider(),
                configuration.getChatModel(),
                configuration.getTranscriptionProvider(),
                configuration.getTranscriptionModel()
        );
    }
}
