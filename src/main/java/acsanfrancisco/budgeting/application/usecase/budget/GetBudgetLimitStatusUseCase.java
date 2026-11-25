package acsanfrancisco.budgeting.application.usecase.budget;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.output.MonthlyBudgetLimitStatusOutput;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitRepository;
import acsanfrancisco.budgeting.domain.report.MonthlyBudgetLimitStatus;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.transaction.TransactionType;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.YearMonth;

@Service
public class GetBudgetLimitStatusUseCase {

    private final BudgetLimitRepository budgetLimitRepository;

    private final UserRepository userRepository;

    public GetBudgetLimitStatusUseCase(BudgetLimitRepository budgetLimitRepository, UserRepository userRepository) {
        this.budgetLimitRepository = budgetLimitRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public MonthlyBudgetLimitStatusOutput execute(YearMonth period, UserId userId, Category category){
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if(period == null){
            throw new IllegalArgumentException("Must inform period");
        }
        if(category == null || category.getType() != TransactionType.EXPENSE ){
            throw new IllegalArgumentException("Must inform a Expense Category");
        }
        MonthlyBudgetLimitStatus budgetLimitStatus = budgetLimitRepository.getBudgetLimitStatus(period, userId, category);
        return MonthlyBudgetLimitStatusOutput.from(budgetLimitStatus);
    }

}
