package acsanfrancisco.budgeting.infrastructure.persistence.projection;

import java.math.BigDecimal;

public interface MonthlyBudgetLimitStatusProjection {

    BigDecimal getLimit();
    BigDecimal getSpent();
}
