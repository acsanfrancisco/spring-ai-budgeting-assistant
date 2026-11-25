package acsanfrancisco.budgeting.infrastructure.persistence.repository.aiconversation;

import acsanfrancisco.budgeting.domain.aiconversation.AiConversationStatus;
import acsanfrancisco.budgeting.infrastructure.persistence.entity.AiConversationEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AiConversationEntityRepository extends JpaRepository<AiConversationEntity, UUID> {

    @Query("""
    SELECT a
    FROM AiConversationEntity a
    WHERE a.userId = :userId
    AND a.status = :status""")
    Optional<AiConversationEntity> findActiveByUserId(@Param("userId") UUID userId, @Param("status") AiConversationStatus status);

    // Bulk deletes run immediately. A derived delete would wait for the flush, and Hibernate
    // flushes inserts before deletes, so a replacement ACTIVE row would hit the unique index.
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM AiConversationEntity a WHERE a.id = :id")
    void deleteNow(@Param("id") UUID id);

    @Modifying(flushAutomatically = true)
    @Query("""
    DELETE FROM AiConversationEntity a
    WHERE a.status = :closed
    OR a.expiresAt <= :now""")
    int deleteInactive(@Param("closed") AiConversationStatus closed, @Param("now") Instant now);
}
