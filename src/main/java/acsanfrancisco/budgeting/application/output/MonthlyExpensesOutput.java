package acsanfrancisco.budgeting.application.output;

import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.report.MonthlyExpenses;
import java.math.BigDecimal;

public record MonthlyExpensesOutput(Category category, BigDecimal total) {

    public static MonthlyExpensesOutput from(MonthlyExpenses monthlyExpenses) {
        return new MonthlyExpensesOutput(
                monthlyExpenses.category(),
                monthlyExpenses.total());
    }
}
