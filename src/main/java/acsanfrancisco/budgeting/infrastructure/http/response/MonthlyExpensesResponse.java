package acsanfrancisco.budgeting.infrastructure.http.response;

import acsanfrancisco.budgeting.application.output.MonthlyExpensesOutput;
import acsanfrancisco.budgeting.domain.transaction.Category;
import java.math.BigDecimal;

public record MonthlyExpensesResponse(Category category, BigDecimal total) {
    public static MonthlyExpensesResponse from(MonthlyExpensesOutput expensesOutput) {
        return new MonthlyExpensesResponse(
                expensesOutput.category(),
                expensesOutput.total());
    }
}
