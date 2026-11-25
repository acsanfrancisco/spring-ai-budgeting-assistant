package acsanfrancisco.budgeting.application.input;

import acsanfrancisco.budgeting.domain.aiconfiguration.ChatProvider;
import acsanfrancisco.budgeting.domain.aiconfiguration.TranscriptionProvider;

public record AiConfigurationInput(ChatProvider chatProvider, String chatModel, String chatApiKey,
                                   TranscriptionProvider transcriptionProvider, String transcriptionModel, String transcriptionApiKey) {

}
