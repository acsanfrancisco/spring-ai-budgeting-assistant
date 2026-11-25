package acsanfrancisco.budgeting.infrastructure.persistence.repository.useraiconfiguration;

import acsanfrancisco.budgeting.infrastructure.persistence.entity.UserAiConfigurationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AiConfigurationEntityRepository extends JpaRepository<UserAiConfigurationEntity, UUID> {
}
