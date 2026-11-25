package acsanfrancisco.budgeting.infrastructure.persistence.repository.transaction;

import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.transaction.TransactionType;
import acsanfrancisco.budgeting.infrastructure.persistence.entity.TransactionEntity;
import acsanfrancisco.budgeting.infrastructure.persistence.projection.MonthlyExpensesProjection;
import acsanfrancisco.budgeting.infrastructure.persistence.projection.MonthlySummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface TransactionEntityRepository extends JpaRepository<TransactionEntity, UUID> {

    @Query("""
    SELECT t FROM TransactionEntity t
    WHERE t.user.id = :userId
      AND t.category = :category
      AND t.date BETWEEN coalesce(:from, t.date) AND coalesce(:to, t.date)
    ORDER BY t.date DESC, t.id DESC
    """)
    Page<TransactionEntity> findByCategoryAndPeriod(
            @Param("category") Category category, @Param("userId") UUID userId,
            @Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);

    @Query("""
    SELECT sum(t.amount) AS total
    FROM TransactionEntity t
    WHERE t.user.id = :userId
    AND t.category = :category
    AND t.date >= :startDate
    AND t.date < :endDate
    """)
    MonthlyExpensesProjection findMonthlyExpensesByCategoryUseCase(
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("userId")UUID userId, @Param("category") Category category);

    @Query("""
    SELECT sum(CASE
    WHEN t.type = :expenses
    THEN t.amount ELSE 0 END) AS expenses,
    sum(CASE
    WHEN t.type = :income
    THEN t.amount ELSE 0 END) AS income
    FROM TransactionEntity t
    WHERE t.user.id = :userId
    AND date >= :startDate
    AND date < :endDate
    """)
    MonthlySummaryProjection getMonthlySummary(
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("userId") UUID userId,
            @Param("expenses") TransactionType expenses, @Param("income") TransactionType income);

    @Modifying
    @Query(value = """
            INSERT INTO tb_transaction
                (id, description, amount, category, date, type, user_id, idempotency_key, idempotency_request_hash)
            VALUES (:id, :description, :amount, :category, :date, :type, :userId, :key, :messageHash)
            ON CONFLICT (user_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    void insertIdempotently(@Param("id") UUID id, @Param("description") String description,
            @Param("amount") BigDecimal amount, @Param("category") String category,
            @Param("date") LocalDate date, @Param("type") String type,
            @Param("userId") UUID userId, @Param("key") UUID key, @Param("messageHash") String messageHash);

    @Query(value = """
            SELECT * FROM tb_transaction WHERE user_id = :userId AND idempotency_key = :key
            """, nativeQuery = true)
    Optional<TransactionEntity> findByIdempotencyKey(@Param("userId") UUID userId, @Param("key") UUID key);

    Optional<TransactionEntity> findTransactionByIdAndUserId(UUID transactionId, UUID userId);

}
