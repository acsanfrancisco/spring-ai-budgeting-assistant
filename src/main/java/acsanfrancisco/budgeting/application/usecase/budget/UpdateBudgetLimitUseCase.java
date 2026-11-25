package acsanfrancisco.budgeting.application.usecase.budget;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.output.BudgetLimitOutput;
import acsanfrancisco.budgeting.domain.budget.BudgetLimit;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitId;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitRepository;
import acsanfrancisco.budgeting.domain.user.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
public class UpdateBudgetLimitUseCase {

    private final BudgetLimitRepository budgetLimitRepository;

    public  UpdateBudgetLimitUseCase(BudgetLimitRepository budgetLimitRepository) {
        this.budgetLimitRepository = budgetLimitRepository;
    }

    @Transactional
    public BudgetLimitOutput execute(BigDecimal limitAmount, BudgetLimitId budgetLimitId, UserId userId) {
        BudgetLimit budgetLimit = budgetLimitRepository.findBudgetLimitByIdAndUserId(budgetLimitId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget limit not found"));
        budgetLimit.setLimitAmount(limitAmount);
        return BudgetLimitOutput.from(budgetLimitRepository.save(budgetLimit));
    }
}
