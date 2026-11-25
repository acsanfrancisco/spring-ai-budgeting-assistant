package acsanfrancisco.budgeting.application.input;

import acsanfrancisco.budgeting.domain.transaction.Category;
import java.math.BigDecimal;
import java.time.YearMonth;

public record CreateBudgetLimitInput(Category category, YearMonth period, BigDecimal limitAmount) {
}
