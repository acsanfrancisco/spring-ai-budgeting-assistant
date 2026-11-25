package acsanfrancisco.budgeting.infrastructure.http.response;

import acsanfrancisco.budgeting.application.output.BudgetLimitOutput;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

public record BudgetLimitResponse(UUID id, String category, @JsonFormat(pattern = "MM/yyyy") YearMonth period, BigDecimal limitAmount) {

    public static BudgetLimitResponse from(BudgetLimitOutput budgetLimitOutput) {
        return new BudgetLimitResponse(
                budgetLimitOutput.id(),
                budgetLimitOutput.category(),
                YearMonth.parse(budgetLimitOutput.period()),
                budgetLimitOutput.limitAmount());
    }
}
