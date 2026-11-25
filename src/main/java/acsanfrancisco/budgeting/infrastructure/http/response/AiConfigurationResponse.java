package acsanfrancisco.budgeting.infrastructure.http.response;

import acsanfrancisco.budgeting.application.output.AiConfigurationOutput;

public record AiConfigurationResponse(String chatProvider, String chatModel, String transcriptionProvider, String transcriptionModel) {

    public static AiConfigurationResponse from(AiConfigurationOutput output) {
        return new AiConfigurationResponse(
                output.chatProvider() == null ? null : output.chatProvider().name(),
                output.chatModel(),
                output.transcriptionProvider() == null ? null : output.transcriptionProvider().name(),
                output.transcriptionModel());
    }
}
