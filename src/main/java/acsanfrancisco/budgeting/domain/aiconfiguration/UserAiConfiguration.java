package acsanfrancisco.budgeting.domain.aiconfiguration;

import acsanfrancisco.budgeting.domain.user.UserId;
import lombok.Getter;

@Getter
public class UserAiConfiguration {

    private final UserId userId;

    private final ChatProvider chatProvider;

    private final String chatModel;

    private String chatApiKey;

    private final TranscriptionProvider transcriptionProvider;

    private final String transcriptionModel;

    private String transcriptionApiKey;

    public UserAiConfiguration(UserId userId, ChatProvider chatProvider, String chatModel,
                               String chatApiKey, TranscriptionProvider transcriptionProvider,
                               String transcriptionModel, String transcriptionApiKey){
        if(userId == null){
            throw new IllegalArgumentException("User id must not be null");
        }
        if (chatProvider == null && transcriptionProvider == null) {
            throw new IllegalArgumentException("Configure chat or transcription");
        }
        if (chatProvider == null) {
            if (chatModel != null || chatApiKey != null) {
                throw new IllegalArgumentException("Chat provider must be informed");
            }
        } else {
            if (chatModel == null || chatModel.isBlank()) {
                throw new IllegalArgumentException("Chat model must be informed");
            }
            validateChatApiKey(chatApiKey);
        }

        switch (transcriptionProvider) {
            case null -> {
                if (transcriptionModel != null || transcriptionApiKey != null) {
                    throw new IllegalArgumentException("Transcription provider must be informed");
                }
            }
            case OPENAI -> {
                if (transcriptionModel == null || transcriptionModel.isBlank()) {
                    throw new IllegalArgumentException("Transcription model must be informed");
                }
                validateTranscriptionApiKey(transcriptionProvider, transcriptionApiKey);
            }
        }
        this.userId = new UserId(userId.id());
        this.chatProvider = chatProvider;
        this.chatModel = chatModel;
        this.chatApiKey = chatApiKey;
        this.transcriptionProvider = transcriptionProvider;
        this.transcriptionModel = transcriptionModel;
        this.transcriptionApiKey = transcriptionApiKey;
    }

    public void setChatApiKey(String chatApiKey){
        if (chatProvider == null) throw new IllegalArgumentException("Chat provider must be informed");
        validateChatApiKey(chatApiKey);
        this.chatApiKey = chatApiKey;
    }

    public void setTranscriptionApiKey(String transcriptionApiKey){
        validateTranscriptionApiKey(this.transcriptionProvider, transcriptionApiKey);
        this.transcriptionApiKey = transcriptionApiKey;
    }

    private static void validateChatApiKey(String chatApiKey){
        if(chatApiKey == null || chatApiKey.isBlank()){
            throw new IllegalArgumentException("Chat Api Key must be informed");
        }
    }

    private static void validateTranscriptionApiKey(TranscriptionProvider provider, String apiKey){
        if(provider == TranscriptionProvider.OPENAI && (apiKey == null || apiKey.isBlank())){
            throw new IllegalArgumentException("Transcription API key must be informed");
        }
        if(provider != TranscriptionProvider.OPENAI && apiKey != null){
            throw new IllegalArgumentException("Individual transcription credentials require an OpenAI transcription provider");
        }
    }
}
