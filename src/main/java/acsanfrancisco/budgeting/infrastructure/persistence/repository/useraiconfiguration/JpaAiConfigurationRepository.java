package acsanfrancisco.budgeting.infrastructure.persistence.repository.useraiconfiguration;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.domain.aiconfiguration.AiConfigurationRepository;
import acsanfrancisco.budgeting.domain.aiconfiguration.UserAiConfiguration;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.infrastructure.persistence.entity.UserAiConfigurationEntity;
import acsanfrancisco.budgeting.infrastructure.persistence.repository.user.UserEntityRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class JpaAiConfigurationRepository implements AiConfigurationRepository {

    private final AiConfigurationEntityRepository aiConfigurationEntityRepository;
    private final UserEntityRepository userEntityRepository;

    public JpaAiConfigurationRepository(AiConfigurationEntityRepository aiConfigurationEntityRepository,  UserEntityRepository userEntityRepository) {
        this.aiConfigurationEntityRepository = aiConfigurationEntityRepository;
        this.userEntityRepository = userEntityRepository;
    }

    @Override
    public UserAiConfiguration save(UserAiConfiguration configuration) {
        userEntityRepository.findById(configuration.getUserId().id())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserAiConfigurationEntity entity = UserAiConfigurationEntity.from(configuration);
        return aiConfigurationEntityRepository.save(entity).toDomain();
    }

    @Override
    public Optional<UserAiConfiguration> findById(UserId userId) {
        return aiConfigurationEntityRepository.findById(userId.id()).map(UserAiConfigurationEntity::toDomain);
    }
}
