package acsanfrancisco.budgeting.application.usecase.budget;

import acsanfrancisco.budgeting.domain.budget.BudgetLimitId;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitRepository;
import acsanfrancisco.budgeting.domain.user.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteBudgetLimitUseCase {

    private final BudgetLimitRepository budgetLimitRepository;

    public DeleteBudgetLimitUseCase(BudgetLimitRepository budgetLimitRepository) {
        this.budgetLimitRepository = budgetLimitRepository;
    }

    @Transactional
    public void execute(BudgetLimitId budgetLimitId, UserId userId) {
        budgetLimitRepository.delete(budgetLimitId, userId);
    }
}
