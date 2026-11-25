package acsanfrancisco.budgeting.domain.aiconfiguration;

import acsanfrancisco.budgeting.domain.user.UserId;

import java.util.Optional;

public interface AiConfigurationRepository {

    UserAiConfiguration save(UserAiConfiguration aiConfiguration);

    Optional<UserAiConfiguration> findById(UserId userId);
}
