package acsanfrancisco.budgeting.infrastructure.persistence.repository.budget;

import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.infrastructure.persistence.entity.BudgetLimitEntity;
import acsanfrancisco.budgeting.infrastructure.persistence.projection.MonthlyBudgetLimitStatusProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

public interface BudgetLimitEntityRepository extends JpaRepository<BudgetLimitEntity, UUID> {

    boolean existsByUserIdAndCategoryAndPeriod(UUID userId, Category category, YearMonth period);

    @Query("""
    SELECT b.limitAmount AS limit,(
        SELECT sum(t.amount)
        FROM TransactionEntity t
        WHERE t.user.id = b.user.id
        AND t.category = b.category
        AND t.date >= :startDate
        AND t.date < :endDate) AS spent
    FROM BudgetLimitEntity b
    WHERE b.user.id = :userId
    AND b.category = :category
    AND b.period = :period
    """)
    MonthlyBudgetLimitStatusProjection getBudgetLimitStatus(
            @Param("period") YearMonth period, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("userId") UUID userId, @Param("category") Category category);

    Optional<BudgetLimitEntity> findBudgetLimitByIdAndUserId(UUID id, UUID userId);
}
