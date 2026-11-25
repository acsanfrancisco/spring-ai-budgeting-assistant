package acsanfrancisco.budgeting.infrastructure.config;

import acsanfrancisco.budgeting.application.usecase.aiconversation.PurgeInactiveAiConversationsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.conversation-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class AiConversationCleanupJob {

    private final PurgeInactiveAiConversationsUseCase purge;

    @Scheduled(initialDelayString = "${ai.conversation-cleanup.initial-delay:PT1M}",
            fixedDelayString = "${ai.conversation-cleanup.interval:PT1H}")
    public void run() {
        PurgeInactiveAiConversationsUseCase.Result result = purge.execute(Instant.now());
        if (result.conversations() > 0 || result.messages() > 0) {
            log.info("Deleted inactive AI conversations. conversations={} messages={}",
                    result.conversations(), result.messages());
        }
    }
}
