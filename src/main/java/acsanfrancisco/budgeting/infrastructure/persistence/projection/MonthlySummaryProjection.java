package acsanfrancisco.budgeting.infrastructure.persistence.projection;

import java.math.BigDecimal;

public interface MonthlySummaryProjection {
    BigDecimal getExpenses();
    BigDecimal getIncome();
}
