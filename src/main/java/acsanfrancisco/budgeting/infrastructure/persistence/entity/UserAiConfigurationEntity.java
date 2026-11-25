package acsanfrancisco.budgeting.infrastructure.persistence.entity;

import acsanfrancisco.budgeting.domain.aiconfiguration.UserAiConfiguration;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.aiconfiguration.ChatProvider;
import acsanfrancisco.budgeting.domain.aiconfiguration.TranscriptionProvider;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "tb_user_ai_configuration")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserAiConfigurationEntity {

    @Id
    @Column(name = "user_id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "chat_provider", length = 20)
    private ChatProvider chatProvider;

    @Column(name = "chat_model")
    private String chatModel;

    @Column(name = "chat_api_key", columnDefinition = "TEXT")
    private String chatApiKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "transcription_provider", length = 20)
    private TranscriptionProvider transcriptionProvider;

    @Column(name = "transcription_model")
    private String transcriptionModel;

    @Column(name = "transcription_api_key", columnDefinition = "TEXT")
    private String transcriptionApiKey;

    public static UserAiConfigurationEntity from(UserAiConfiguration userAiConfiguration) {
        return new UserAiConfigurationEntity(
                userAiConfiguration.getUserId().id(),
                userAiConfiguration.getChatProvider(),
                userAiConfiguration.getChatModel(),
                userAiConfiguration.getChatApiKey(),
                userAiConfiguration.getTranscriptionProvider(),
                userAiConfiguration.getTranscriptionModel(),
                userAiConfiguration.getTranscriptionApiKey()
        );
    }

    public UserAiConfiguration toDomain(){
        return new UserAiConfiguration(
                new UserId(this.id),
                this.chatProvider,
                this.chatModel,
                this.chatApiKey,
                this.transcriptionProvider,
                this.transcriptionModel,
                this.transcriptionApiKey
        );
    }
}
