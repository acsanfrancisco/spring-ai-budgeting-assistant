package acsanfrancisco.budgeting.domain.transaction;

import acsanfrancisco.budgeting.domain.pagination.PageQuery;
import acsanfrancisco.budgeting.domain.pagination.PageResult;
import acsanfrancisco.budgeting.domain.report.MonthlyExpenses;
import acsanfrancisco.budgeting.domain.report.MonthlySummary;
import acsanfrancisco.budgeting.domain.user.UserId;
import java.time.YearMonth;
import java.time.LocalDate;
import java.util.Optional;

public interface TransactionRepository {

    Transaction save(Transaction transaction, TransactionRequestKey key);

    Optional<Transaction> findByIdempotencyKey(UserId userId, TransactionRequestKey key);

    Transaction update(Transaction transaction,TransactionId transactionId, UserId userId);

    Optional<Transaction> findTransactionByIdAndUserId(TransactionId transactionId, UserId userId);

    PageResult<Transaction> findByCategoryAndPeriod(Category category, UserId userId, LocalDate from, LocalDate to, PageQuery pageQuery);

    MonthlyExpenses findMonthlyExpensesByCategory(YearMonth yearMonth, UserId userId, Category category);

    MonthlySummary getMonthlySummary(YearMonth yearMonth, UserId userId, TransactionType income, TransactionType expenses);

    void delete(TransactionId transactionId, UserId userId);
}
