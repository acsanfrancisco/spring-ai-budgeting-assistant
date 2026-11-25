package acsanfrancisco.budgeting.infrastructure.persistence.repository.transaction;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.domain.pagination.PageQuery;
import acsanfrancisco.budgeting.domain.pagination.PageResult;
import acsanfrancisco.budgeting.domain.report.MonthlyExpenses;
import acsanfrancisco.budgeting.domain.report.MonthlySummary;
import acsanfrancisco.budgeting.domain.transaction.*;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.infrastructure.persistence.entity.TransactionEntity;
import acsanfrancisco.budgeting.infrastructure.persistence.entity.UserEntity;
import acsanfrancisco.budgeting.infrastructure.persistence.projection.MonthlyExpensesProjection;
import acsanfrancisco.budgeting.infrastructure.persistence.projection.MonthlySummaryProjection;
import acsanfrancisco.budgeting.infrastructure.persistence.repository.user.UserEntityRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import acsanfrancisco.budgeting.application.exception.IdempotencyConflictException;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaTransactionRepository implements TransactionRepository {

    private final TransactionEntityRepository transactionEntityRepository;
    private final UserEntityRepository userEntityRepository;

    public JpaTransactionRepository(TransactionEntityRepository transactionEntityRepository, UserEntityRepository userEntityRepository) {
        this.transactionEntityRepository = transactionEntityRepository;
        this.userEntityRepository = userEntityRepository;
    }

    @Override
    @Transactional
    public Transaction save(Transaction transaction, TransactionRequestKey key) {
        UserId userId = transaction.getUser().getId();
        if (key == null) {
            UserEntity user = userEntityRepository.findById(userId.id())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            return transactionEntityRepository.save(TransactionEntity.from(transaction, user)).toDomain();
        }

        transactionEntityRepository.insertIdempotently(transaction.getId().id(),
                transaction.getDescription(), transaction.getAmount(), transaction.getCategory().name(),
                transaction.getDate(), transaction.getType().name(), userId.id(), key.value(), key.messageHash());
        return findByIdempotencyKey(userId, key).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Transaction> findByIdempotencyKey(UserId userId, TransactionRequestKey key) {
        return transactionEntityRepository.findByIdempotencyKey(userId.id(), key.value())
                .map(entity -> {
                    if (!key.messageHash().equals(entity.getIdempotencyRequestHash())) {
                        throw new IdempotencyConflictException();
                    }
                    return entity.toDomain();
                });
    }

    @Override
    public Transaction update(Transaction transaction, TransactionId transactionId, UserId userId) {
        TransactionEntity transactionToUpdate = transactionEntityRepository.findTransactionByIdAndUserId(transactionId.id(), userId.id())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        transactionToUpdate.setType(transaction.getType());
        transactionToUpdate.setAmount(transaction.getAmount());
        transactionToUpdate.setDescription(transaction.getDescription());
        transactionToUpdate.setCategory(transaction.getCategory());
        transactionToUpdate.setDate(transaction.getDate());
        TransactionEntity result = transactionEntityRepository.save(transactionToUpdate);
        return result.toDomain();
    }

    @Override
    public Optional<Transaction> findTransactionByIdAndUserId(TransactionId transactionId, UserId userId) {
        return transactionEntityRepository.findTransactionByIdAndUserId(transactionId.id(), userId.id())
                .map(TransactionEntity::toDomain);
    }

    @Override
    public PageResult<Transaction> findByCategoryAndPeriod(Category category, UserId userId, LocalDate from, LocalDate to, PageQuery pageQuery) {
        Pageable pageable = PageRequest.of(pageQuery.page(), pageQuery.size());
        Page<TransactionEntity> result = transactionEntityRepository.findByCategoryAndPeriod(category, userId.id(), from, to, pageable);
        return new PageResult<>(
                result.getContent().stream().map(TransactionEntity::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements()
        );
    }

    @Override
    public MonthlyExpenses findMonthlyExpensesByCategory(YearMonth yearMonth, UserId userId, Category category) {
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.plusMonths(1).atDay(1);
        MonthlyExpensesProjection projection = transactionEntityRepository.findMonthlyExpensesByCategoryUseCase(startDate, endDate, userId.id(), category);

        return new MonthlyExpenses(
                category,
                projection == null || projection.getTotal() == null ? BigDecimal.ZERO : projection.getTotal());
    }

    @Override
    public MonthlySummary getMonthlySummary(YearMonth yearMonth, UserId userId, TransactionType income, TransactionType expenses) {
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.plusMonths(1).atDay(1);
        MonthlySummaryProjection projection = transactionEntityRepository.getMonthlySummary(startDate, endDate, userId.id(), expenses, income);
        return new MonthlySummary(
                projection == null || projection.getExpenses() == null ? BigDecimal.ZERO : projection.getExpenses(),
                projection == null || projection.getIncome() == null ? BigDecimal.ZERO : projection.getIncome());
    }

    @Override
    public void delete(TransactionId transactionId, UserId userId) {
        TransactionEntity transaction = transactionEntityRepository.findTransactionByIdAndUserId(transactionId.id(), userId.id())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        transactionEntityRepository.deleteById(transaction.getId());
    }
}
