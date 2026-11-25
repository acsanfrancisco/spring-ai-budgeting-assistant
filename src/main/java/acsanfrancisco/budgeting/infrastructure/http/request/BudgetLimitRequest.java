package acsanfrancisco.budgeting.infrastructure.http.request;

import acsanfrancisco.budgeting.application.input.CreateBudgetLimitInput;
import acsanfrancisco.budgeting.domain.transaction.Category;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.YearMonth;

public record BudgetLimitRequest(
        @NotNull(message = "Category must be informed")
        Category category,
        @NotNull(message = "Period must be informed")
        @JsonFormat(pattern = "MM/yyyy")
        YearMonth period,
        @NotNull(message = "Amount must be informed")
        @Positive(message = "Amount must be positive")
        BigDecimal limitAmount) {

    public CreateBudgetLimitInput toInput(){
        return new CreateBudgetLimitInput(category, period, limitAmount);
    }
}
