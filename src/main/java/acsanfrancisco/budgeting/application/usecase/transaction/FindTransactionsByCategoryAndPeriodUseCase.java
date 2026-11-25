package acsanfrancisco.budgeting.application.usecase.transaction;

import acsanfrancisco.budgeting.application.output.TransactionOutput;
import acsanfrancisco.budgeting.domain.pagination.PageQuery;
import acsanfrancisco.budgeting.domain.pagination.PageResult;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.transaction.Transaction;
import acsanfrancisco.budgeting.domain.transaction.TransactionRepository;
import acsanfrancisco.budgeting.domain.user.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
public class FindTransactionsByCategoryAndPeriodUseCase {

    private final TransactionRepository transactionRepository;

    public FindTransactionsByCategoryAndPeriodUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public PageResult<TransactionOutput> execute(Category category, UserId userId, LocalDate from, LocalDate to, PageQuery pageQuery) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must be on or before to");
        }
        PageResult<Transaction> result = transactionRepository.findByCategoryAndPeriod(category, userId, from, to, pageQuery);
        return new PageResult<>(
                result.content().stream().map(TransactionOutput::from).toList(),
                result.page(),
                result.size(),
                result.totalElements());
    }

}
