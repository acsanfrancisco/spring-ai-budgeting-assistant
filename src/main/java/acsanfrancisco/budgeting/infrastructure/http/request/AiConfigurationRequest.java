package acsanfrancisco.budgeting.infrastructure.http.request;

import acsanfrancisco.budgeting.application.input.AiConfigurationInput;
import acsanfrancisco.budgeting.domain.aiconfiguration.ChatProvider;
import acsanfrancisco.budgeting.domain.aiconfiguration.TranscriptionProvider;

public record AiConfigurationRequest(
        ChatProvider chatProvider,
        String chatModel,
        String chatApiKey,
        TranscriptionProvider transcriptionProvider,
        String transcriptionModel, String transcriptionApiKey) {

    public AiConfigurationInput toInput(){
        return new AiConfigurationInput(chatProvider, chatModel, chatApiKey,
                transcriptionProvider, transcriptionModel, transcriptionApiKey);
    }
}
