package acsanfrancisco.budgeting.infrastructure.http.response;

import acsanfrancisco.budgeting.application.output.MonthlyBudgetLimitStatusOutput;
import acsanfrancisco.budgeting.domain.budget.BudgetStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.YearMonth;

public record BudgetLimitStatusResponse(@JsonFormat(pattern = "MM/yyyy") YearMonth period, BudgetStatus status, BigDecimal limit, BigDecimal spent, BigDecimal remaining, BigDecimal percentage) {
    public static BudgetLimitStatusResponse from(YearMonth period, MonthlyBudgetLimitStatusOutput monthlyBudgetLimitStatusOutput) {
        return new BudgetLimitStatusResponse(
                period,
                monthlyBudgetLimitStatusOutput.status(),
                monthlyBudgetLimitStatusOutput.limit(),
                monthlyBudgetLimitStatusOutput.spent(),
                monthlyBudgetLimitStatusOutput.remaining(),
                monthlyBudgetLimitStatusOutput.percentage());
    }
}
