package acsanfrancisco.budgeting.infrastructure.persistence.projection;

import java.math.BigDecimal;

public interface MonthlyExpensesProjection {
    BigDecimal getTotal();
}
