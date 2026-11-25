package acsanfrancisco.budgeting.domain.report;

import acsanfrancisco.budgeting.domain.transaction.Category;
import java.math.BigDecimal;

public record MonthlyExpenses(Category category, BigDecimal total) {
}
