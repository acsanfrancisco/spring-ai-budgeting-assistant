package acsanfrancisco.budgeting.application.output;

import acsanfrancisco.budgeting.domain.budget.BudgetStatus;
import acsanfrancisco.budgeting.domain.report.MonthlyBudgetLimitStatus;
import java.math.BigDecimal;

public record MonthlyBudgetLimitStatusOutput(BudgetStatus status, BigDecimal limit, BigDecimal spent, BigDecimal remaining, BigDecimal percentage) {
    public static MonthlyBudgetLimitStatusOutput from(MonthlyBudgetLimitStatus monthlyBudgetLimitStatus) {
        return new MonthlyBudgetLimitStatusOutput(
                monthlyBudgetLimitStatus.status(),
                monthlyBudgetLimitStatus.limitAmount(),
                monthlyBudgetLimitStatus.spent(),
                monthlyBudgetLimitStatus.remaining(),
                monthlyBudgetLimitStatus.percentage());
    }
}
