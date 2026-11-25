package acsanfrancisco.budgeting.application.output;

import acsanfrancisco.budgeting.domain.budget.BudgetLimit;
import java.math.BigDecimal;
import java.util.UUID;

public record BudgetLimitOutput(UUID id, String category, String period, BigDecimal limitAmount) {
    public static BudgetLimitOutput from(BudgetLimit budgetLimit) {
        return new BudgetLimitOutput(
                budgetLimit.getId().id(),
                budgetLimit.getCategory().toString(),
                budgetLimit.getPeriod().toString(),
                budgetLimit.getLimitAmount());
    }
}
