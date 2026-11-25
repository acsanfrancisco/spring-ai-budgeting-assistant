package acsanfrancisco.budgeting.application.usecase.transaction;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.output.MonthlyExpensesOutput;
import acsanfrancisco.budgeting.domain.report.MonthlyExpenses;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.transaction.TransactionRepository;
import acsanfrancisco.budgeting.domain.transaction.TransactionType;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.YearMonth;

@Service
public class FindMonthlyExpensesByCategoryUseCase {

    private final TransactionRepository transactionRepository;

    private final UserRepository userRepository;

    public FindMonthlyExpensesByCategoryUseCase(TransactionRepository transactionRepository,  UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public MonthlyExpensesOutput execute(YearMonth yearMonth, Category category, UserId userId) {
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        if(yearMonth == null || category == null) {
            throw new IllegalArgumentException("Must inform period and category.");
        }
        if(category.getType() != TransactionType.EXPENSE){
            throw new IllegalArgumentException("Category must be an expense type.");
        }

        MonthlyExpenses monthlyExpenses = transactionRepository.findMonthlyExpensesByCategory(yearMonth, userId, category);
        return MonthlyExpensesOutput.from(monthlyExpenses);
    }
}
